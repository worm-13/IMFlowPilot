from dataclasses import dataclass, field
from typing import Any


@dataclass
class PlanStep:
    step: str
    name: str
    status: str = "pending"
    tool: str = ""
    args: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "step": self.step,
            "name": self.name,
            "status": self.status,
            "tool": self.tool,
            "args": self.args,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PlanStep":
        return cls(
            step=data.get("step", ""),
            name=data.get("name", ""),
            status=data.get("status", "pending"),
            tool=data.get("tool", ""),
            args=data.get("args", {}),
        )


@dataclass
class Plan:
    task: str
    message: str
    steps: list[PlanStep] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "task": self.task,
            "message": self.message,
            "steps": [s.to_dict() for s in self.steps],
        }
