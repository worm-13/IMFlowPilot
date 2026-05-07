import json
import logging
from typing import Any
from config.llm import get_llm
from model.plan import PlanStep
from service.tools.base import BaseTool
from tools.doc_generator import DocGenerator

logger = logging.getLogger(__name__)

STRUCTURE_PROMPT = """将以下文档大纲和内容转换为结构化JSON对象。

大纲:
{outline}

内容:
{content}

输出格式（严格JSON对象）:
{{"章节标题": "该章节的完整内容", ...}}

规则:
- 每个大纲项对应一个key
- value要完整保留原文内容，不要截断
- 直接输出JSON对象，不要markdown代码块，不要额外解释"""


class DocBuildTool(BaseTool):

    @property
    def tool_name(self) -> str:
        return "build_doc"

    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        args = context.get("args", {})
        pipeline = args.get("_pipeline", {})
        message = context.get("message", "")
        task = context.get("task", "")

        is_modify = task in ("modify_doc", "modify_ppt")

        if is_modify:
            content_md = pipeline.get("merge_content", "")
            outline_md = ""
        else:
            outline_md = pipeline.get("generate_outline", "")
            content_md = pipeline.get("generate_content", "")

        if not outline_md and not content_md:
            sections = {"文档内容": message}
        else:
            sections = await self._structure_sections(outline_md, content_md)

        topic = message[:80] if message else "文档"
        try:
            file_path = DocGenerator.generate_doc(topic, sections)
            logger.info("DocBuildTool: generated %s with %d sections", file_path, len(sections))
            return {"status": "completed", "output": file_path}
        except Exception as e:
            logger.error("DocBuildTool failed: %s", e)
            return {"status": "failed", "output": str(e)}

    async def _structure_sections(self, outline_md: str, content_md: str) -> dict[str, str]:
        llm = get_llm()
        prompt = STRUCTURE_PROMPT.format(outline=outline_md, content=content_md)
        try:
            response = await llm.ainvoke(prompt)
            raw = response.content if hasattr(response, "content") else str(response)
            return self._parse_sections_json(raw)
        except Exception as e:
            logger.warning("DocBuildTool structure LLM failed: %s, using fallback parse", e)
            return self._fallback_parse(outline_md, content_md)

    def _parse_sections_json(self, raw: str) -> dict[str, str]:
        cleaned = raw.strip()
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            cleaned = "\n".join(lines[1:])
            if cleaned.endswith("```"):
                cleaned = cleaned[:-3]
            cleaned = cleaned.strip()
        if cleaned.startswith("json"):
            cleaned = cleaned[4:].strip()
        try:
            data = json.loads(cleaned)
            if isinstance(data, dict):
                return data
        except json.JSONDecodeError:
            pass
        return {"文档内容": raw[:2000]}

    def _fallback_parse(self, outline_md: str, content_md: str) -> dict[str, str]:
        titles = [line.lstrip("#").strip() for line in outline_md.split("\n") if line.startswith("#") and line.lstrip("#").strip()]
        if not titles:
            return {"文档内容": content_md[:2000]}

        result = {}
        sections = content_md.split("\n# ")
        for i, title in enumerate(titles):
            content = ""
            for section in sections:
                if section.startswith(title) or section.lstrip("#").startswith(title):
                    content = section
                    break
            if not content and i < len(sections):
                content = sections[i]
            result[title] = content[:2000] if content else "暂无内容"
        return result if result else {"文档内容": content_md[:2000]}
