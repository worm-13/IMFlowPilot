from dataclasses import dataclass, field
from typing import Any


@dataclass
class AgentResponse:
    type: str
    content: str

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AgentResponse":
        return cls(
            type=data.get("type", "ignore"),
            content=data.get("content", ""),
        )

    def to_dict(self) -> dict[str, str]:
        return {
            "type": self.type,
            "content": self.content,
        }
