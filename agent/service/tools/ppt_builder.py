import json
import logging
from typing import Any
from config.llm import get_llm
from model.plan import PlanStep
from service.tools.base import BaseTool
from tools.ppt_generator import PPTGenerator

logger = logging.getLogger(__name__)

STRUCTURE_PROMPT = """将以下PPT大纲和每页内容转换为结构化JSON数组。

大纲:
{outline}

每页内容:
{slides}

输出格式（严格JSON数组）:
[{{"title": "页面标题", "content": "该页的完整内容"}}]

规则:
- 每个大纲项对应一个数组元素
- content 要完整保留原文内容，不要截断
- 直接输出JSON数组，不要markdown代码块，不要额外解释"""


class PPTBuildTool(BaseTool):

    @property
    def tool_name(self) -> str:
        return "build_ppt"

    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        args = context.get("args", {})
        pipeline = args.get("_pipeline", {})
        message = context.get("message", "")
        task = context.get("task", "")

        is_modify = task in ("modify_ppt",)

        if is_modify:
            merged = pipeline.get("merge_content", "")
            if merged:
                outline_md, slides_md = self._split_merged_ppt(merged)
            else:
                outline_md, slides_md = "", ""
        else:
            outline_md = pipeline.get("generate_outline", "")
            slides_md = pipeline.get("generate_slides", "")

        if not outline_md and not slides_md:
            slides_content = [{"title": "主题", "content": message}]
        else:
            slides_content = await self._structure_slides(outline_md, slides_md)

        topic = message[:80] if message else "PPT文档"
        try:
            file_path = PPTGenerator.generate_ppt(topic, slides_content)
            logger.info("PPTBuildTool: generated %s with %d slides", file_path, len(slides_content))
            return {"status": "completed", "output": file_path, "slides_data": slides_content}
        except Exception as e:
            logger.error("PPTBuildTool failed: %s", e)
            return {"status": "failed", "output": str(e)}

    def _split_merged_ppt(self, merged: str) -> tuple[str, str]:
        outline_md = ""
        slides_md = ""

        if "# PPT大纲" in merged:
            parts = merged.split("# 每页内容", 1)
            outline_md = parts[0].replace("# PPT大纲", "").strip()
            slides_md = parts[1].strip() if len(parts) > 1 else ""
        elif "# 每页内容" in merged:
            parts = merged.split("# 每页内容", 1)
            outline_md = ""
            slides_md = parts[1].strip() if len(parts) > 1 else merged
        else:
            slides_md = merged

        return outline_md, slides_md

    async def _structure_slides(self, outline_md: str, slides_md: str) -> list[dict]:
        llm = get_llm()
        prompt = STRUCTURE_PROMPT.format(outline=outline_md, slides=slides_md)
        try:
            response = await llm.ainvoke(prompt)
            raw = response.content if hasattr(response, "content") else str(response)
            return self._parse_slides_json(raw)
        except Exception as e:
            logger.warning("PPTBuildTool structure LLM failed: %s, using fallback parse", e)
            return self._fallback_parse(outline_md, slides_md)

    def _parse_slides_json(self, raw: str) -> list[dict]:
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
            if isinstance(data, list):
                return data
        except json.JSONDecodeError:
            pass
        return [{"title": "内容", "content": raw[:2000]}]

    def _fallback_parse(self, outline_md: str, slides_md: str) -> list[dict]:
        titles = [line.lstrip("#").strip() for line in outline_md.split("\n") if line.startswith("#") and line.lstrip("#").strip()]
        if not titles:
            titles = ["内容页"]

        sections = slides_md.split("\n# ")
        result = []
        for i, title in enumerate(titles):
            content = ""
            for section in sections:
                if section.startswith(title) or section.lstrip("#").startswith(title):
                    content = section
                    break
            if not content and i < len(sections):
                content = sections[i] if i < len(sections) else slides_md[:500]
            result.append({"title": title, "content": content[:2000]})
        return result if result else [{"title": "内容", "content": slides_md[:2000]}]
