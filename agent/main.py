import logging
import os
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from service.agent_service import AgentService
from service.planner import PlannerService
from service.orchestrator import OrchestratorService

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

app = FastAPI(title="IMFlowPilot Agent", version="1.0.0")
agent_service = AgentService()
planner_service = PlannerService()
orchestrator_service = OrchestratorService()


class ProcessRequest(BaseModel):
    message: str = Field(..., min_length=1, description="User chat message")
    session_id: str = Field(default="", description="Session ID for context memory")
    mentions: list[str] = Field(default_factory=list, description="List of mentioned users (e.g. ['agent', '???'])")


class ProcessResponse(BaseModel):
    type: str = Field(..., description="Classification: ignore | suggestion | task")
    content: str = Field(default="", description="Response content")
    meta: dict | None = Field(default=None, description="Meta info: requires_confirmation, suggested_task, confidence")


@app.post("/agent/process", response_model=ProcessResponse)
async def process_message(request: ProcessRequest) -> ProcessResponse:
    result = await agent_service.process(request.message, request.session_id, request.mentions)
    return ProcessResponse(
        type=result.type,
        content=result.content,
        meta={
            "requires_confirmation": result.requires_confirmation,
            "suggested_task": result.suggested_task,
            "confidence": result.confidence,
        } if result.type in ("suggestion", "task") else None
    )


class PlanRequest(BaseModel):
    task: str = Field(..., description="Task type from classification")
    message: str = Field(..., min_length=1, description="Original user message")
    session_id: str = Field(default="", description="Session ID")


class PlanResponse(BaseModel):
    task: str
    message: str
    steps: list[dict] = Field(default_factory=list)


@app.post("/agent/plan", response_model=PlanResponse)
async def create_plan(request: PlanRequest) -> PlanResponse:
    plan = await planner_service.plan(request.task, request.message)
    return PlanResponse(task=plan.task, message=plan.message, steps=[s.to_dict() for s in plan.steps])


class ExecuteRequest(BaseModel):
    task: str = Field(..., description="Task type")
    message: str = Field(..., min_length=1, description="User message")
    session_id: str = Field(default="", description="Session ID")
    callback_url: str = Field(default="", description="URL for progress callbacks")
    steps: list[dict] = Field(default_factory=list, description="Plan steps")


@app.post("/agent/execute", response_model=PlanResponse)
async def execute_plan(request: ExecuteRequest):
    from model.plan import Plan, PlanStep
    plan = Plan(
        task=request.task,
        message=request.message,
        steps=[PlanStep.from_dict(s) for s in request.steps],
    )
    result = await orchestrator_service.execute(plan, request.callback_url)
    return PlanResponse(task=result.task, message=result.message, steps=[s.to_dict() for s in result.steps])


@app.get("/health")
async def health_check():
    return {"status": "ok"}


@app.get("/download/{file_name}")
async def download_file(file_name: str):
    file_path = os.path.join(OUTPUT_DIR, file_name)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(file_path, filename=file_name, media_type="application/octet-stream")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
