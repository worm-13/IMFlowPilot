from dataclasses import dataclass, field
from typing import Any


@dataclass
class PlanStep:
    step: str
    name: str
    status: str = "pending"

    def to_dict(self) -> dict[str, str]:
        return {"step": self.step, "name": self.name, "status": self.status}

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PlanStep":
        return cls(
            step=data.get("step", ""),
            name=data.get("name", ""),
            status=data.get("status", "pending"),
        )


@dataclass
class Plan:
    task: str
    message: str
    steps: list[PlanStep] = field(default_factory=list)
    result: dict[str, Any] | None = field(default=None)

    def to_dict(self) -> dict[str, Any]:
        data = {
            "task": self.task,
            "message": self.message,
            "steps": [s.to_dict() for s in self.steps],
        }
        if self.result:
            data["result"] = self.result
        return data