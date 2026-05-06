import asyncio
import logging
from typing import Any
import httpx
from model.plan import Plan
from service.tools.registry import ToolRegistry

logger = logging.getLogger(__name__)


class OrchestratorService:

    async def execute(self, plan: Plan, callback_url: str = "") -> Plan:
        if not callback_url:
            for step in plan.steps:
                step.status = "completed"
            return plan

        pipeline_context: dict[str, Any] = {}

        for step in plan.steps:
            step.status = "running"
            await self._notify(callback_url, plan, step)

            try:
                enriched_args = self._enrich_args(step.args, pipeline_context)
                tool = ToolRegistry.get(step.tool) if step.tool else None
                if tool:
                    result = await tool.execute(step, {
                        "task": plan.task,
                        "message": plan.message,
                        "plan": plan,
                        "args": enriched_args,
                    })
                    step.status = result.get("status", "completed")
                    output = result.get("output", "")
                    if output:
                        pipeline_context[step.step] = output
                    logger.info(
                        "Step %s executed by %s → %s (output: %d chars)",
                        step.step, tool.__class__.__name__, step.status, len(str(output)),
                    )
                else:
                    await asyncio.sleep(1.5)
                    step.status = "completed"
                    logger.info("Step %s executed (fallback sleep, no tool=%s)", step.step, step.tool or "(none)")

                await self._notify(callback_url, plan, step)
            except Exception as e:
                logger.error("Step %s failed: %s", step.step, e)
                step.status = "failed"
                await self._notify(callback_url, plan, step)
                break

        return plan

    def _enrich_args(self, step_args: dict[str, Any], pipeline_context: dict[str, Any]) -> dict[str, Any]:
        enriched = dict(step_args)
        enriched["_pipeline"] = dict(pipeline_context)
        return enriched

    async def _notify(self, callback_url: str, plan: Plan, current_step):
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(10)) as client:
                await client.post(callback_url, json={
                    "task": plan.task,
                    "message": plan.message,
                    "steps": [s.to_dict() for s in plan.steps],
                })
        except Exception as e:
            logger.warning("Failed to notify progress to %s: %s", callback_url, e)
