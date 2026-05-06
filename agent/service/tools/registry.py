import logging
from service.tools.base import BaseTool

logger = logging.getLogger(__name__)


class ToolRegistry:
    _tools: dict[str, BaseTool] = {}

    @classmethod
    def register(cls, tool: BaseTool):
        cls._tools[tool.tool_name] = tool
        logger.info("Tool registered: name=%s, class=%s", tool.tool_name, tool.__class__.__name__)

    @classmethod
    def get(cls, name: str) -> BaseTool | None:
        return cls._tools.get(name)

    @classmethod
    def list_names(cls) -> list[str]:
        return list(cls._tools.keys())
