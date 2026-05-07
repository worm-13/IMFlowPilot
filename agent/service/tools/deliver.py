import logging
import os
from typing import Any
from model.plan import PlanStep
from service.tools.base import BaseTool

logger = logging.getLogger(__name__)

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "output")


class DeliverTool(BaseTool):

    @property
    def tool_name(self) -> str:
        return "deliver_files"

    async def execute(self, step: PlanStep, context: dict[str, Any]) -> dict[str, Any]:
        args = context.get("args", {})
        pipeline = args.get("_pipeline", {})
        task = context.get("task", "")

        file_paths: list[str] = []
        for key in ("build_ppt", "build_doc", "format_doc"):
            fp = pipeline.get(key, "")
            if fp and os.path.isfile(fp):
                file_paths.append(fp)

        deliverables: list[dict[str, Any]] = []
        for fp in file_paths:
            file_name = os.path.basename(fp)
            file_size = os.path.getsize(fp)
            ext = os.path.splitext(file_name)[1].lower()

            content_type = "unknown"
            if ext == ".pptx":
                content_type = "ppt"
            elif ext == ".docx":
                content_type = "doc"

            deliverables.append({
                "file_name": file_name,
                "file_size": file_size,
                "download_url": f"/download/{file_name}",
                "content_type": content_type,
            })

        generated_content = ""
        if task in ("generate_doc", "modify_doc"):
            generated_content = pipeline.get("generate_content") or pipeline.get("merge_content") or ""
        elif task in ("generate_ppt", "modify_ppt"):
            outline = pipeline.get("generate_outline", "")
            slides = pipeline.get("generate_slides", "")
            merged = pipeline.get("merge_content", "")
            generated_content = merged or (outline + "\n\n" + slides if outline or slides else "")

        logger.info("DeliverTool: %d deliverable(s) for task=%s", len(deliverables), task)

        return {
            "status": "completed",
            "output": f"交付 {len(deliverables)} 个文件",
            "deliverables": deliverables,
            "delivery_content": generated_content,
        }
