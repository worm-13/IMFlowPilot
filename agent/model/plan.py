from dataclasses import dataclass, field
from typing import Any


@dataclass
class PlanStep:
    step: str
    name: str
    status: str = "pending"
    tool: str = ""
    args: dict[str, Any] = field(default_factory=dict)
    depends_on: list[str] = field(default_factory=list)
    condition: str = ""
    on_failure: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "step": self.step,
            "name": self.name,
            "status": self.status,
            "tool": self.tool,
            "args": self.args,
            "depends_on": self.depends_on,
            "condition": self.condition,
            "on_failure": self.on_failure,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PlanStep":
        return cls(
            step=data.get("step", ""),
            name=data.get("name", ""),
            status=data.get("status", "pending"),
            tool=data.get("tool", ""),
            args=data.get("args", {}),
            depends_on=data.get("depends_on", []),
            condition=data.get("condition", ""),
            on_failure=data.get("on_failure", ""),
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