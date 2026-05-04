import json
import logging
from langchain_core.prompts import ChatPromptTemplate, SystemMessagePromptTemplate, HumanMessagePromptTemplate
from langchain_core.output_parsers import StrOutputParser

from config.llm import get_llm
from model.plan import Plan, PlanStep
from prompt.planner_prompt import PLANNER_SYSTEM_PROMPT, PLANNER_HUMAN_TEMPLATE

logger = logging.getLogger(__name__)

TASK_PLANS: dict[str, list[dict[str, str]]] = {
    "generate_ppt": [
        {"step": "analyze_requirements", "name": "分析需求与主题"},
        {"step": "generate_outline", "name": "生成PPT大纲"},
        {"step": "generate_slides", "name": "生成每页内容"},
        {"step": "build_ppt", "name": "构建PPT文件"},
        {"step": "notify_user", "name": "通知用户完成"},
    ],
    "generate_doc": [
        {"step": "analyze_requirements", "name": "分析需求"},
        {"step": "generate_outline", "name": "生成文档大纲"},
        {"step": "generate_content", "name": "生成文档内容"},
        {"step": "format_doc", "name": "格式化文档"},
        {"step": "notify_user", "name": "通知用户完成"},
    ],
    "analyze_data": [
        {"step": "parse_requirements", "name": "解析数据需求"},
        {"step": "query_data", "name": "查询数据"},
        {"step": "generate_analysis", "name": "生成分析报告"},
        {"step": "notify_user", "name": "通知用户完成"},
    ],
    "modify_ppt": [
        {"step": "parse_modifications", "name": "解析修改需求"},
        {"step": "apply_changes", "name": "应用修改"},
        {"step": "notify_user", "name": "通知用户完成"},
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
            steps = [PlanStep(**s) for s in TASK_PLANS[task]]
            logger.info("Using predefined plan for task=%s with %d steps", task, len(steps))
            return Plan(task=task, message=message, steps=steps)

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

        fallback = [PlanStep(step="process_request", name="处理请求")]
        return Plan(task=task, message=message, steps=fallback)
