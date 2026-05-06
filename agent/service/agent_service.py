import json
import logging
from langchain_core.prompts import ChatPromptTemplate, SystemMessagePromptTemplate, HumanMessagePromptTemplate
from langchain_core.output_parsers import StrOutputParser

from config.llm import get_llm
from config.redis import get_chat_history, MAX_HISTORY_ROUNDS
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

    async def process(self, message: str, session_id: str = "", mentions: list[str] | None = None,
                      pending_task: str = "", collected_info: dict[str, str] | None = None,
                      in_info_collection: bool = False) -> AgentResponse:
        try:
            mentions_text = self._format_mentions(mentions)
            history_text = self._load_history(session_id)
            collected_text = self._format_collected_info(collected_info)

            raw_output = await self.chain.ainvoke({
                "message": message,
                "history": history_text,
                "mentions": mentions_text,
                "pending_task": pending_task or "(none)",
                "collected_info": collected_text,
                "in_info_collection": str(in_info_collection),
            })
            logger.info(f"LLM raw output: {raw_output}")

            parsed = self._parse_json(raw_output)
            result = AgentResponse.from_dict(parsed)

            self._save_history(session_id, message, result, mentions)

            return result
        except Exception as e:
            logger.error(f"Agent processing failed: {e}")
            return self._fallback()

    def _format_mentions(self, mentions: list[str] | None) -> str:
        if not mentions:
            return "(none)"
        return ", ".join(mentions)

    def _format_collected_info(self, collected_info: dict[str, str] | None) -> str:
        if not collected_info:
            return "(none)"
        return ", ".join(f"{k}={v}" for k, v in collected_info.items())

    def _load_history(self, session_id: str) -> str:
        if not session_id:
            return "(none)"

        try:
            history = get_chat_history(session_id)
            if history is None:
                return "(none)"

            messages = history.messages
            if not messages:
                return "(none)"

            recent = messages[-(MAX_HISTORY_ROUNDS * 2):]
            pairs = []
            for msg in recent:
                role = "Human" if msg.type == "human" else "AI"
                content = msg.content
                if role == "AI" and content.startswith("{"):
                    try:
                        parsed = json.loads(content)
                        t = parsed.get("type", "")
                        c = parsed.get("content", "")
                        if t == "task":
                            content = f"[classified as task: {c}]"
                        elif t == "suggestion":
                            content = f"[suggestion: {c}]"
                        elif t == "ignore":
                            content = "[ignored as casual chat]"
                    except json.JSONDecodeError:
                        pass
                pairs.append(f"{role}: {content}")

            result = "\n".join(pairs)
            logger.info(f"Loaded {len(recent)} messages from history for session {session_id}")
            return result
        except Exception as e:
            logger.warning(f"Failed to load history for session {session_id}: {e}")
            return "(none)"

    def _save_history(self, session_id: str, message: str, response: AgentResponse, mentions: list[str] | None = None) -> None:
        if not session_id:
            return

        try:
            history = get_chat_history(session_id)
            if history is None:
                return

            tag = ""
            if mentions:
                tag = f"[@{', '.join(mentions)}] "
            history.add_user_message(f"{tag}{message}")
            history.add_ai_message(json.dumps(response.to_dict(), ensure_ascii=False))
        except Exception as e:
            logger.warning(f"Failed to save history for session {session_id}: {e}")

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

        valid_types = {"ignore", "mention", "suggestion", "task"}
        if data["type"] not in valid_types:
            data["type"] = "ignore"

        if "content" not in data:
            data["content"] = ""

        return data

    def _fallback(self) -> AgentResponse:
        return AgentResponse(type="ignore", content="", requires_confirmation=False, suggested_task="", confidence=0.0, info_sufficiency="unknown", missing_fields=[])
