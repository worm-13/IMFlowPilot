import logging
from typing import Any
from langchain_core.prompts import ChatPromptTemplate, SystemMessagePromptTemplate, HumanMessagePromptTemplate
from langchain_core.output_parsers import StrOutputParser
from config.llm import get_llm
from model.plan import PlanStep
from service.tools.base import BaseTool

logger = logging.getLogger(__name__)

DOC_GENERATOR_SYSTEM_PROMPT = """你是一个专业的文档撰写助手。你的唯一任务是严格根据用户提供的需求信息来撰写文档内容。

核心规则（必须遵守）：
- 只能使用用户需求中明确提到的产品名、功能、目标用户等信息
- 绝对不要从你的训练数据中编造或补充用户未提及的内容
- 绝对不要描述你所在的系统或任何其他无关项目
- 用户说什么产品，你就写什么产品；用户要求什么结构，你就用什么结构
- 如果用户需求中提到了具体的章节要求，必须全部覆盖"""

DOC_GENERATOR_HUMAN_TEMPLATE = """请生成{content_type_desc}。

{chat_history}
用户需求:
{message}

{upstream_context}要求:
- 使用恰当的标题层级
- 内容充实，不少于 500 字
- 语言与用户需求保持一致
- 直接输出 Markdown 内容，不要额外解释"""

CONTENT_TYPE_DESC: dict[str, str] = {
    "doc_outline": "文档大纲（列出所有章节标题和简要说明）",
    "doc_content": "完整的文档正文内容",
    "ppt_outline": "PPT大纲（列出所有页面标题）",
    "ppt_slides": "PPT每页的详细内容",
    "analysis_report": "数据分析报告",
    "meeting_summary": "会议纪要（总结会议讨论内容、关键决策和行动项）",
}

MODIFY_ANALYSIS_SYSTEM_PROMPT = """你是一个专业的文档编辑助手。请严格根据用户修改需求和原始文档内容进行分析。"""

MODIFY_ANALYSIS_HUMAN_TEMPLATE = """原始文档内容:
{original_content}

用户修改需求: {message}

请分析:
1. 用户想要修改什么（新增/删除/改写/结构调整）
2. 修改涉及文档的哪些章节
3. 修改的具体内容是什么

输出格式（严格JSON）:
{{
  "modification_type": "add_section | remove_section | rewrite_section | restructure | append_content",
  "target_sections": ["章节名1", "章节名2"],
  "change_summary": "修改摘要描述",
  "new_content_plan": "计划新增/修改的内容概要"
}}

直接输出JSON对象，不要markdown代码块，不要额外解释"""

MODIFY_MERGE_SYSTEM_PROMPT = """你是一个专业的文档编辑助手。请根据修改分析结果，将修改合并到原始文档中。"""

MODIFY_MERGE_HUMAN_TEMPLATE = """原始文档:
{original_content}

修改分析:
{analysis}

用户原始需求: {message}

请生成合并后的完整 Markdown 文档。要求:
- 保留原始文档中未被修改的部分
- 将修改内容自然地融入文档结构
- 保持文档风格和格式一致
- 使用恰当的标题层级
- 直接输出完整的 Markdown 文档，不要额外解释"""

MODIFY_PPT_ANALYSIS_SYSTEM_PROMPT = """你是一个专业的PPT编辑助手。请严格根据用户修改需求和原始PPT内容进行分析。"""

MODIFY_PPT_ANALYSIS_HUMAN_TEMPLATE = """原始PPT内容（大纲+每页内容）:
{original_content}

用户修改需求: {message}

请分析:
1. 用户想要修改什么（新增页面/删除页面/改写内容/调整结构）
2. 修改涉及哪些页面
3. 修改的具体内容是什么

输出格式（严格JSON）:
{{
  "modification_type": "add_slide | remove_slide | rewrite_slide | restructure",
  "target_slides": ["页面标题1", "页面标题2"],
  "change_summary": "修改摘要描述",
  "new_content_plan": "计划新增/修改的内容概要"
}}

直接输出JSON对象，不要markdown代码块，不要额外解释"""

MODIFY_PPT_MERGE_SYSTEM_PROMPT = """你是一个专业的PPT编辑助手。请根据修改分析结果，将修改合并到原始PPT内容中。"""

MODIFY_PPT_MERGE_HUMAN_TEMPLATE = """原始PPT内容:
{original_content}

修改分析:
{analysis}

用户原始需求: {message}

请生成合并后的完整PPT内容，包含:
1. 大纲（所有页面的标题列表）
2. 每页的详细内容

输出格式:
# PPT大纲
- 页面1标题
- 页面2标题
...

# 每页内容

## 页面1标题
页面1的详细内容...

## 页面2标题
页面2的详细内容...

直接输出完整内容，不要额外解释"""


class DocGeneratorTool(BaseTool):

    @property
    def tool_name(self) -> str:
        return "generate_content"

    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        message = context.get("message", "")
        args = context.get("args", {})
        content_type = args.get("type", "content")
        pipeline = args.get("_pipeline", {})

        if content_type.startswith("modify_"):
            return await self._execute_modify(message, content_type, pipeline)

        upstream_context = self._build_upstream_context(content_type, pipeline)
        content_type_desc = CONTENT_TYPE_DESC.get(content_type, "文档内容")
        chat_history = self._build_chat_history(pipeline)

        llm = get_llm()
        try:
            prompt = ChatPromptTemplate.from_messages([
                SystemMessagePromptTemplate.from_template(DOC_GENERATOR_SYSTEM_PROMPT),
                HumanMessagePromptTemplate.from_template(DOC_GENERATOR_HUMAN_TEMPLATE),
            ])
            chain = prompt | llm | StrOutputParser()
            content = await chain.ainvoke({
                "content_type_desc": content_type_desc,
                "chat_history": chat_history,
                "message": message,
                "upstream_context": upstream_context,
            })
            logger.info(
                "DocGeneratorTool: generated %d chars for type=%s (upstream keys: %s, has_history=%s)",
                len(content), content_type, list(pipeline.keys()),
                bool(pipeline.get("_chat_history")),
            )
            return {"status": "completed", "output": content}
        except Exception as e:
            logger.error("DocGeneratorTool failed: %s", e)
            return {"status": "failed", "output": str(e)}

    async def _execute_modify(self, message: str, content_type: str,
                               pipeline: dict[str, Any]) -> dict[str, Any]:
        original_content = pipeline.get("_original_doc_content", "")
        if not original_content:
            original_content = pipeline.get("_original_ppt_content", "")

        llm = get_llm()

        if content_type in ("modify_analysis", "modify_ppt_analysis"):
            system_prompt = MODIFY_ANALYSIS_SYSTEM_PROMPT if content_type == "modify_analysis" else MODIFY_PPT_ANALYSIS_SYSTEM_PROMPT
            human_template = MODIFY_ANALYSIS_HUMAN_TEMPLATE if content_type == "modify_analysis" else MODIFY_PPT_ANALYSIS_HUMAN_TEMPLATE
            try:
                prompt = ChatPromptTemplate.from_messages([
                    SystemMessagePromptTemplate.from_template(system_prompt),
                    HumanMessagePromptTemplate.from_template(human_template),
                ])
                chain = prompt | llm | StrOutputParser()
                content = await chain.ainvoke({
                    "original_content": original_content,
                    "message": message,
                })
                logger.info("DocGeneratorTool: modify analysis generated %d chars", len(content))
                return {"status": "completed", "output": content}
            except Exception as e:
                logger.error("DocGeneratorTool modify analysis failed: %s", e)
                return {"status": "failed", "output": str(e)}

        elif content_type in ("modify_merge", "modify_ppt_merge"):
            analysis = pipeline.get("analyze_changes", "")
            system_prompt = MODIFY_MERGE_SYSTEM_PROMPT if content_type == "modify_merge" else MODIFY_PPT_MERGE_SYSTEM_PROMPT
            human_template = MODIFY_MERGE_HUMAN_TEMPLATE if content_type == "modify_merge" else MODIFY_PPT_MERGE_HUMAN_TEMPLATE
            try:
                prompt = ChatPromptTemplate.from_messages([
                    SystemMessagePromptTemplate.from_template(system_prompt),
                    HumanMessagePromptTemplate.from_template(human_template),
                ])
                chain = prompt | llm | StrOutputParser()
                content = await chain.ainvoke({
                    "original_content": original_content,
                    "analysis": analysis,
                    "message": message,
                })
                logger.info("DocGeneratorTool: modify merge generated %d chars", len(content))
                return {"status": "completed", "output": content}
            except Exception as e:
                logger.error("DocGeneratorTool modify merge failed: %s", e)
                return {"status": "failed", "output": str(e)}

        return {"status": "failed", "output": f"Unknown modify content type: {content_type}"}

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
        elif content_type == "meeting_summary":
            chat_hist = pipeline.get("_chat_history", "")
            if chat_hist:
                parts.append(f"会议讨论记录:\n{chat_hist}")

        if parts:
            return "上游输出:\n" + "\n\n".join(parts)
        return ""

    def _build_chat_history(self, pipeline: dict[str, Any]) -> str:
        raw = pipeline.get("_chat_history", "")
        if not raw:
            return ""

        return f"""最近对话记录（请从中提取与文档生成相关的所有信息，忽略无关闲聊）:
{raw}

"""
