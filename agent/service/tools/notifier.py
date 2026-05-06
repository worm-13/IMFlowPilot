import logging
from typing import Any
from model.plan import PlanStep
from service.tools.base import BaseTool

logger = logging.getLogger(__name__)


class NotifierTool(BaseTool):

    @property
    def tool_name(self) -> str:
        return "notify_user"

    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        task = context.get("task", "")
        logger.info("NotifierTool: task=%s completed, notifying user", task)
        return {
            "status": "completed",
            "output": f"任务 {task} 已完成，通知用户",
        }
