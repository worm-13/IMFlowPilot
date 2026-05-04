import asyncio
import logging
import httpx
from model.plan import Plan

logger = logging.getLogger(__name__)


class OrchestratorService:

    async def execute(self, plan: Plan, callback_url: str = "") -> Plan:
        if not callback_url:
            for step in plan.steps:
                step.status = "completed"
            return plan

        for step in plan.steps:
            step.status = "running"
            await self._notify(callback_url, plan, step)

            try:
                await asyncio.sleep(1.5)
                step.status = "completed"
                await self._notify(callback_url, plan, step)
            except Exception as e:
                logger.error("Step %s failed: %s", step.step, e)
                step.status = "failed"
                await self._notify(callback_url, plan, step)
                break

        return plan

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
