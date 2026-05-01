import json
import logging
from langchain_core.prompts import ChatPromptTemplate, SystemMessagePromptTemplate, HumanMessagePromptTemplate
from langchain_core.output_parsers import StrOutputParser

from config.llm import get_llm
from model.response import AgentResponse
from prompt.agent_prompt import SYSTEM_PROMPT, HUMAN_TEMPLATE

logger = logging.getLogger(__name__)


class AgentService:
    def __init__(self) -> None:
        self.llm = get_llm()
        self.chain = self._build_chain()

    def _build_chain(self):
        prompt = ChatPromptTemplate.from_messages([
            SystemMessagePromptTemplate.from_template(SYSTEM_PROMPT),
            HumanMessagePromptTemplate.from_template(HUMAN_TEMPLATE),
        ])

        chain = prompt | self.llm | StrOutputParser()
        return chain

    async def process(self, message: str) -> AgentResponse:
        try:
            raw_output = await self.chain.ainvoke({"message": message})
            logger.info(f"LLM raw output: {raw_output}")

            parsed = self._parse_json(raw_output)
            return AgentResponse.from_dict(parsed)
        except Exception as e:
            logger.error(f"Agent processing failed: {e}")
            return self._fallback()

    def _parse_json(self, raw: str) -> dict:
        raw = raw.strip()

        if raw.startswith("```"):
            lines = raw.split("\n")
            raw = "\n".join(lines[1:]) if len(lines) > 1 else raw
            if raw.endswith("```"):
                raw = raw[:-3]
            raw = raw.strip()
            if raw.startswith("json"):
                raw = raw[4:].strip()

        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            import re
            match = re.search(r'\{.*\}', raw, re.DOTALL)
            if match:
                try:
                    data = json.loads(match.group())
                except json.JSONDecodeError:
                    raise
            else:
                raise

        if not isinstance(data, dict):
            raise ValueError(f"Expected JSON object, got {type(data)}")

        if "type" not in data:
            raise ValueError("Missing 'type' field in response")

        valid_types = {"ignore", "suggestion", "task"}
        if data["type"] not in valid_types:
            data["type"] = "ignore"

        if "content" not in data:
            data["content"] = ""

        return data

    def _fallback(self) -> AgentResponse:
        return AgentResponse(type="ignore", content="")
