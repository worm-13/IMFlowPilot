import json
import logging
from langchain_core.prompts import ChatPromptTemplate, SystemMessagePromptTemplate, HumanMessagePromptTemplate
from langchain_core.output_parsers import StrOutputParser

from config.llm import get_llm
from model.plan import Plan, PlanStep
from prompt.planner_prompt import PLANNER_SYSTEM_PROMPT, PLANNER_HUMAN_TEMPLATE

logger = logging.getLogger(__name__)

TASK_PLANS: dict[str, list[dict]] = {
    "generate_ppt": [
        {"step": "generate_outline", "name": "生成PPT大纲",
         "tool": "generate_content", "args": {"type": "ppt_outline"}},
        {"step": "generate_slides", "name": "生成每页内容",
         "tool": "generate_content", "args": {"type": "ppt_slides"}},
        {"step": "build_ppt", "name": "构建PPT文件",
         "tool": "", "args": {}},
        {"step": "notify_user", "name": "通知用户完成",
         "tool": "notify_user", "args": {}},
    ],
    "generate_doc": [
        {"step": "generate_outline", "name": "生成文档大纲",
         "tool": "generate_content", "args": {"type": "doc_outline"}},
        {"step": "generate_content", "name": "生成文档内容",
         "tool": "generate_content", "args": {"type": "doc_content"}},
        {"step": "format_doc", "name": "格式化文档",
         "tool": "", "args": {}},
        {"step": "notify_user", "name": "通知用户完成",
         "tool": "notify_user", "args": {}},
    ],
    "analyze_data": [
        {"step": "query_data", "name": "查询处理数据",
         "tool": "", "args": {}},
        {"step": "generate_analysis", "name": "生成分析报告",
         "tool": "generate_content", "args": {"type": "analysis_report"}},
        {"step": "notify_user", "name": "通知用户完成",
         "tool": "notify_user", "args": {}},
    ],
    "modify_ppt": [
        {"step": "parse_modifications", "name": "解析修改需求",
         "tool": "", "args": {}},
        {"step": "apply_changes", "name": "应用修改",
         "tool": "", "args": {}},
        {"step": "notify_user", "name": "通知用户完成",
         "tool": "notify_user", "args": {}},
    ],
}


class PlannerService:
    def __init__(self) -> None:
        self.llm = get_llm()
        self.chain = self._build_chain()

    def _build_chain(self):
        prompt = ChatPromptTemplate.from_messages([
            SystemMessagePromptTemplate.from_template(PLANNER_SYSTEM_PROMPT),
            HumanMessagePromptTemplate.from_template(PLANNER_HUMAN_TEMPLATE),
        ])
        return prompt | self.llm | StrOutputParser()

    async def plan(self, task: str, message: str) -> Plan:
        if task in TASK_PLANS:
            return self._build_conditional_plan(task, message)

        return await self._llm_plan(task, message)

    def _build_conditional_plan(self, task: str, message: str) -> Plan:
        steps = [PlanStep(**s) for s in TASK_PLANS[task]]

        logger.info(
            "Execution plan for task=%s: total=%d steps",
            task, len(steps),
        )

        return Plan(task=task, message=message, steps=steps)

    async def _llm_plan(self, task: str, message: str) -> Plan:
        try:
            raw = await self.chain.ainvoke({"message": message, "task": task})
            logger.info("LLM plan output: %s", raw)

            cleaned = raw.strip()
            if cleaned.startswith("```"):
                lines = cleaned.split("\n")
                cleaned = "\n".join(lines[1:])
                if cleaned.endswith("```"):
                    cleaned = cleaned[:-3]
                cleaned = cleaned.strip()

            data = json.loads(cleaned)
            if isinstance(data, list):
                steps = [PlanStep.from_dict(s) for s in data if isinstance(s, dict)]
                return Plan(task=task, message=message, steps=steps)
        except Exception as e:
            logger.warning("LLM plan generation failed, using fallback: %s", e)

        fallback = [
            PlanStep(step="process_request", name="处理请求", tool="", args={}),
            PlanStep(step="notify_user", name="通知用户完成", tool="notify_user", args={}),
        ]
        return Plan(task=task, message=message, steps=fallback)
