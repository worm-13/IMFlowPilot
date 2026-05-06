from dataclasses import dataclass, field
from typing import Any


@dataclass
class AgentResponse:
    type: str
    content: str
    requires_confirmation: bool = False
    suggested_task: str = ""
    confidence: float = 0.0
    info_sufficiency: str = "unknown"
    missing_fields: list[str] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AgentResponse":
        meta = data.get("meta", {}) or {}
        return cls(
            type=data.get("type", "ignore"),
            content=data.get("content", ""),
            requires_confirmation=meta.get("requires_confirmation", False),
            suggested_task=meta.get("suggested_task", ""),
            confidence=float(meta.get("confidence", 0.0)),
            info_sufficiency=meta.get("info_sufficiency", "unknown"),
            missing_fields=meta.get("missing_fields", []),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "type": self.type,
            "content": self.content,
            "meta": {
                "requires_confirmation": self.requires_confirmation,
                "suggested_task": self.suggested_task,
                "confidence": self.confidence,
                "info_sufficiency": self.info_sufficiency,
                "missing_fields": self.missing_fields,
            },
        }
