from abc import ABC, abstractmethod
from typing import Any
from model.plan import PlanStep


class BaseTool(ABC):

    @property
    @abstractmethod
    def tool_name(self) -> str:
        pass

    @abstractmethod
    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        pass
