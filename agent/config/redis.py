import os
import logging
from typing import Optional
from langchain_community.chat_message_histories import RedisChatMessageHistory

logger = logging.getLogger(__name__)

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
SESSION_TTL = int(os.getenv("AGENT_SESSION_TTL", "3600"))
MAX_HISTORY_ROUNDS = int(os.getenv("AGENT_MAX_HISTORY", "25"))


def get_chat_history(session_id: str) -> Optional[RedisChatMessageHistory]:
    try:
        return RedisChatMessageHistory(
            session_id=f"agent_session:{session_id}",
            url=REDIS_URL,
            ttl=SESSION_TTL,
        )
    except Exception as e:
        logger.warning(f"Failed to connect to Redis at {REDIS_URL}: {e}")
        return None
