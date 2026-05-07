import asyncio
import logging
import os
from collections import deque
from typing import Any
import httpx
from model.plan import Plan, PlanStep
from service.tools.registry import ToolRegistry
from config.redis import get_chat_history, MAX_HISTORY_ROUNDS

logger = logging.getLogger(__name__)


class OrchestratorService:

    async def execute(self, plan: Plan, callback_url: str = "",
                       previous_doc_content: str = "", previous_ppt_content: str = "",
                       session_id: str = "") -> Plan:
        if not callback_url:
            for step in plan.steps:
                step.status = "completed"
            return plan

        pipeline_context: dict[str, Any] = {}

        if plan.task in ("modify_doc",) and previous_doc_content:
            pipeline_context["_original_doc_content"] = previous_doc_content
            logger.info("Seeded pipeline with original doc content (%d chars)", len(previous_doc_content))
        elif plan.task in ("modify_ppt",) and previous_ppt_content:
            pipeline_context["_original_ppt_content"] = previous_ppt_content
            logger.info("Seeded pipeline with original PPT content (%d chars)", len(previous_ppt_content))

        chat_history_text = self._load_chat_history(session_id)
        if chat_history_text:
            pipeline_context["_chat_history"] = chat_history_text
            logger.info("Loaded chat history for session %s (%d chars)", session_id, len(chat_history_text))

        step_map: dict[str, PlanStep] = {s.step: s for s in plan.steps}
        dependencies: dict[str, list[str]] = {}
        dependents: dict[str, list[str]] = {}

        for s in plan.steps:
            deps = s.depends_on if s.depends_on else self._infer_deps(s, plan.steps)
            dependencies[s.step] = [d for d in deps if d in step_map]
            for d in dependencies[s.step]:
                dependents.setdefault(d, []).append(s.step)

        ready = deque()
        pending_count: dict[str, int] = {}
        for s in plan.steps:
            pending_count[s.step] = len(dependencies[s.step])
            if pending_count[s.step] == 0 and s.status == "pending":
                ready.append(s)

        if not ready:
            logger.warning("No ready steps found; possible circular dependency")
            for s in plan.steps:
                if s.status == "pending":
                    s.status = "failed"
            return plan

        skipped: set[str] = set()

        while ready:
            wave = list(ready)
            ready.clear()

            wave_tasks = []
            for step in wave:
                wave_tasks.append(self._execute_step(step, plan, pipeline_context, callback_url))

            results = await asyncio.gather(*wave_tasks, return_exceptions=True)

            for i, result in enumerate(results):
                step = wave[i]
                if isinstance(result, Exception):
                    logger.error("Step %s raised exception: %s", step.step, result)
                    step.status = "failed"
                    await self._notify(callback_url, plan, step)
                    self._handle_failure(step, step_map, ready, pending_count, skipped)
                    continue

                if result == "skipped":
                    skipped.add(step.step)
                    continue

                for dep in dependents.get(step.step, []):
                    pending_count[dep] -= 1
                    if pending_count[dep] == 0 and step_map[dep].status == "pending":
                        ready.append(step_map[dep])

        self._attach_result(plan, pipeline_context)
        await self._notify(callback_url, plan, plan.steps[-1] if plan.steps else None)
        return plan

    def _infer_deps(self, step: PlanStep, all_steps: list[PlanStep]) -> list[str]:
        deps = []
        idx = None
        for i, s in enumerate(all_steps):
            if s.step == step.step:
                idx = i
                break
        if idx is not None and idx > 0:
            deps.append(all_steps[idx - 1].step)
        return deps

    async def _execute_step(
        self, step: PlanStep, plan: Plan, pipeline: dict[str, Any], callback_url: str,
    ) -> str:
        if step.condition:
            ok = self._eval_condition(step.condition, pipeline)
            if not ok:
                step.status = "skipped"
                await self._notify(callback_url, plan, step)
                logger.info("Step %s skipped: condition '%s' not met", step.step, step.condition)
                return "skipped"

        step.status = "running"
        await self._notify(callback_url, plan, step)

        try:
            enriched_args = self._enrich_args(step.args, pipeline)
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
                    pipeline[step.step] = output
                slides_data = result.get("slides_data")
                if slides_data:
                    pipeline[step.step + "_slides_data"] = slides_data
                deliverables = result.get("deliverables")
                if deliverables:
                    pipeline["_deliverables"] = deliverables
                delivery_content = result.get("delivery_content")
                if delivery_content:
                    pipeline["_delivery_content"] = delivery_content
                logger.info(
                    "Step %s executed by %s → %s (output: %d chars)",
                    step.step, tool.__class__.__name__, step.status, len(str(output)),
                )
            else:
                await asyncio.sleep(1.5)
                step.status = "completed"
                logger.info("Step %s executed (fallback sleep, no tool=%s)", step.step, step.tool or "(none)")

            await self._notify(callback_url, plan, step)
            return "completed"
        except Exception as e:
            logger.error("Step %s failed: %s", step.step, e)
            step.status = "failed"
            await self._notify(callback_url, plan, step)
            raise

    def _handle_failure(
        self, step: PlanStep, step_map: dict[str, PlanStep],
        ready: deque, pending_count: dict[str, int], skipped: set[str],
    ) -> None:
        if not step.on_failure:
            return

        recovery_step = step_map.get(step.on_failure)
        if not recovery_step:
            logger.warning("on_failure target '%s' not found for step '%s'", step.on_failure, step.step)
            return

        if recovery_step.status != "pending":
            return

        deps_met = True
        for dep in recovery_step.depends_on:
            dep_step = step_map.get(dep)
            if not dep_step or dep_step.status not in ("completed", "skipped"):
                deps_met = False
                break

        if deps_met:
            logger.info("Failure branch: step '%s' failed → enqueuing '%s'", step.step, step.on_failure)
            ready.append(recovery_step)
            pending_count[recovery_step.step] = 0

    def _eval_condition(self, condition: str, pipeline: dict[str, Any]) -> bool:
        if not condition or not condition.strip():
            return True

        expr = condition.strip()
        negate = False
        if expr.startswith("!"):
            negate = True
            expr = expr[1:]

        parts = expr.split(".", 1)
        step_name = parts[0]
        check = parts[1] if len(parts) > 1 else "completed"

        if check == "completed":
            result = step_name in pipeline and bool(pipeline[step_name])
        elif check == "failed":
            result = step_name not in pipeline or not pipeline[step_name]
        elif check == "output":
            result = step_name in pipeline and bool(pipeline.get(step_name))
        else:
            result = step_name in pipeline

        return not result if negate else result

    def _load_chat_history(self, session_id: str) -> str:
        if not session_id:
            return ""

        try:
            history = get_chat_history(session_id)
            if history is None:
                return ""

            messages = history.messages
            if not messages:
                return ""

            recent = messages[-(MAX_HISTORY_ROUNDS * 2):]
            lines: list[str] = []
            for i, msg in enumerate(recent):
                role = "用户" if msg.type == "human" else "AI"
                lines.append(f"[{i + 1}] {role}: {msg.content}")

            return "\n".join(lines)
        except Exception as e:
            logger.warning("Failed to load chat history for session %s: %s", session_id, e)
            return ""

    def _enrich_args(self, step_args: dict[str, Any], pipeline_context: dict[str, Any]) -> dict[str, Any]:
        enriched = dict(step_args)
        enriched["_pipeline"] = dict(pipeline_context)
        return enriched

    def _attach_result(self, plan: Plan, pipeline_context: dict[str, Any]) -> None:
        deliverables = pipeline_context.get("_deliverables")
        delivery_content = pipeline_context.get("_delivery_content")

        if deliverables:
            plan.result = {"deliverables": deliverables}
            if delivery_content:
                plan.result["generated_content"] = delivery_content
        else:
            for key in ("build_ppt", "build_doc", "format_doc"):
                file_path = pipeline_context.get(key, "")
                if file_path and os.path.isfile(file_path):
                    plan.result = {
                        "file_name": os.path.basename(file_path),
                        "file_size": os.path.getsize(file_path),
                        "download_url": f"/download/{os.path.basename(file_path)}",
                    }
                    break

        if plan.task == "summarize_meeting":
            content = delivery_content or pipeline_context.get("generate_summary") or ""
            if content:
                if not plan.result:
                    plan.result = {}
                plan.result["generated_content"] = content
        elif plan.task in ("generate_doc", "modify_doc"):
            content = delivery_content or pipeline_context.get("generate_content") or pipeline_context.get("merge_content") or ""
            if content and plan.result:
                plan.result["generated_content"] = content
            elif content:
                plan.result = {"generated_content": content}
        elif plan.task in ("generate_ppt", "modify_ppt"):
            outline = pipeline_context.get("generate_outline", "")
            slides = pipeline_context.get("generate_slides", "")
            merged = pipeline_context.get("merge_content", "")
            ppt_content = delivery_content or merged or (outline + "\n\n" + slides if outline or slides else "")
            if ppt_content and plan.result:
                plan.result["generated_content"] = ppt_content
            elif ppt_content:
                plan.result = {"generated_content": ppt_content}

            slides_data = pipeline_context.get("build_ppt_slides_data")
            if slides_data:
                if plan.result:
                    plan.result["slides_data"] = slides_data
                else:
                    plan.result = {"slides_data": slides_data}

        if plan.result:
            logger.info("Plan result attached: %s", plan.result)

    async def _notify(self, callback_url: str, plan: Plan, current_step):
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(10)) as client:
                payload = {
                    "task": plan.task,
                    "message": plan.message,
                    "steps": [s.to_dict() for s in plan.steps],
                }
                if hasattr(plan, "result") and plan.result:
                    payload["result"] = plan.result
                await client.post(callback_url, json=payload)
        except Exception as e:
            logger.warning("Failed to notify progress to %s: %s", callback_url, e)
