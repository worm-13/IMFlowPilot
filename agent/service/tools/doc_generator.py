import logging
from typing import Any
from config.llm import get_llm
from model.plan import PlanStep
from service.tools.base import BaseTool

logger = logging.getLogger(__name__)

DOC_GENERATOR_PROMPT = """你是一个专业的文档撰写助手。请根据以下信息生成文档内容：

任务: {task}
用户需求: {message}
内容类型: {content_type}
{upstream_context}

生成一份结构清晰、内容详实的 Markdown 文档。要求:
- 使用恰当的标题层级
- 内容充实，不少于 500 字
- 语言与用户需求保持一致
- 直接输出 Markdown 内容，不要额外解释"""


class DocGeneratorTool(BaseTool):

    @property
    def tool_name(self) -> str:
        return "generate_content"

    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        task = context.get("task", "")
        message = context.get("message", "")
        args = context.get("args", {})
        content_type = args.get("type", "content")
        pipeline = args.get("_pipeline", {})

        upstream_context = self._build_upstream_context(content_type, pipeline)

        llm = get_llm()
        try:
            prompt = DOC_GENERATOR_PROMPT.format(
                task=task,
                message=message,
                content_type=content_type,
                upstream_context=upstream_context,
            )
            response = await llm.ainvoke(prompt)
            content = response.content if hasattr(response, "content") else str(response)
            logger.info(
                "DocGeneratorTool: generated %d chars for type=%s (upstream keys: %s)",
                len(content), content_type, list(pipeline.keys()),
            )
            return {"status": "completed", "output": content}
        except Exception as e:
            logger.error("DocGeneratorTool failed: %s", e)
            return {"status": "failed", "output": str(e)}

    def _build_upstream_context(self, content_type: str, pipeline: dict[str, Any]) -> str:
        if not pipeline:
            return ""

        parts: list[str] = []
        if content_type == "ppt_slides" and "generate_outline" in pipeline:
            parts.append(f"上游已生成的大纲:\n{pipeline['generate_outline']}")
        elif content_type == "doc_content" and "generate_outline" in pipeline:
            parts.append(f"上游已生成的大纲:\n{pipeline['generate_outline']}")
        elif content_type == "analysis_report" and "query_data" in pipeline:
            parts.append(f"上游查询结果:\n{pipeline['query_data']}")

        if parts:
            return "上游输出:\n" + "\n\n".join(parts)
        return ""
