import logging
import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel, Field

from service.agent_service import AgentService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

app = FastAPI(title="IMFlowPilot Agent", version="1.0.0")
agent_service = AgentService()


class ProcessRequest(BaseModel):
    message: str = Field(..., min_length=1, description="User chat message")


class ProcessResponse(BaseModel):
    type: str = Field(..., description="Classification: ignore | suggestion | task")
    content: str = Field(default="", description="Response content")


@app.post("/agent/process", response_model=ProcessResponse)
async def process_message(request: ProcessRequest) -> ProcessResponse:
    result = await agent_service.process(request.message)
    return ProcessResponse(type=result.type, content=result.content)


@app.get("/health")
async def health_check():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
