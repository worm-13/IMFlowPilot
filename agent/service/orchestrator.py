import asyncio
import json
import logging
import re
import httpx
import os
from langchain_core.messages import SystemMessage, HumanMessage

from config.llm import get_llm
from model.plan import Plan
from tools.ppt_generator import PPTGenerator
from tools.doc_generator import DocGenerator

logger = logging.getLogger(__name__)


PPT_SYSTEM_PROMPT = """
你是一个专业的PPT内容策划助手。你需要根据用户提供的原始素材，生成PPT大纲和每页的详细内容。

请返回JSON格式，结构如下：
{"outline": ["页面1标题", "页面2标题"], "slides_content": [{"title": "标题1", "content": "内容1"}]}

注意：
- outline是页面标题数组
- slides_content是每页详情，必须包含title和content字段
- content内容要充实，至少3-5行
- 必须基于用户提供的原始素材生成内容
"""

DOC_SYSTEM_PROMPT = """
你是一个专业的文档助手。你需要根据用户提供的原始素材，按照指定的大纲结构，生成文档的各个部分内容。

用户会提供：
1. 原始素材内容
2. 文档大纲（各章节标题列表）

你需要为每个章节生成对应的详细内容，内容要：
- 基于用户提供的原始素材
- 简洁有条理
- 符合中文文档规范
- 每个章节内容控制在100-200字
- 不要使用markdown格式（如**加粗**等），直接输出纯文本
- 输出格式：每个章节的标题后面直接跟内容，不要换行
"""


class OrchestratorService:

    def __init__(self) -> None:
        self.llm = get_llm()

    async def _call_llm(self, content: str) -> str:
        messages = [
            SystemMessage(content=PPT_SYSTEM_PROMPT),
            HumanMessage(content=f"素材内容：\n\n{content}\n\n请根据以上素材生成PPT大纲和每页内容，返回JSON格式。")
        ]
        response = await self.llm.ainvoke(messages)
        return response.content

    async def _call_llm_doc(self, content: str, outline: list) -> dict:
        outline_str = "\n".join([f"{i+1}. {s}" for i, s in enumerate(outline)])
        messages = [
            SystemMessage(content=DOC_SYSTEM_PROMPT),
            HumanMessage(content=f"原始素材：\n\n{content}\n\n文档大纲：\n{outline_str}\n\n请为上述大纲的每个章节生成详细内容。按照以下格式返回（所有内容在同一行）：\n1. 一、 项目概况：本项目名为...内容...\n2. 二、 背景介绍：xxx内容...\n...以此类推\n\n注意：每个章节的标题和内容在同一行，用冒号分隔，不要换行，不要使用**等markdown符号。")
        ]
        response = await self.llm.ainvoke(messages)
        return response.content

    def _parse_json(self, raw_output: str) -> dict:
        cleaned = raw_output.strip()
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            cleaned = "\n".join(lines[1:])
            if cleaned.endswith("```"):
                cleaned = cleaned[:-3]
            cleaned = cleaned.strip()
        if cleaned.startswith("json"):
            cleaned = cleaned[4:].strip()

        try:
            return json.loads(cleaned)
        except json.JSONDecodeError as e:
            logger.warning("JSON parse error: %s, trying to fix...", e)
            cleaned = re.sub(r'\\(?!["\\/bfnrtu])', '\\\\', cleaned)
            try:
                return json.loads(cleaned)
            except:
                return None

    def _parse_doc_content(self, raw_output: str, outline: list) -> dict:
        result = {}
        lines = raw_output.split("\n")
        current_section = None
        current_content = []

        for line in lines:
            line = line.strip()
            if not line:
                continue

            line = re.sub(r'\*+', '', line)
            line = re.sub(r'^[\d]+\.\s*', '', line)

            matched = False
            for i, section in enumerate(outline):
                section_num = str(i + 1)
                section_name = section.split(".")[-1].strip() if "." in section else section
                section_clean = re.sub(r'[\s\d、]', '', section_name)
                prefixes = [f"{section_num}.", f"{section_name}：", f"{section_name}:", f"{section_name}"]
                for prefix in prefixes:
                    if line.startswith(prefix) and len(line) > len(prefix) + 1:
                        if current_section:
                            result[current_section] = "\n\n".join(current_content)
                        current_section = section
                        content_after_prefix = line[len(prefix):].strip()
                        if content_after_prefix:
                            current_content = [content_after_prefix]
                        else:
                            current_content = []
                        matched = True
                        break
                if matched:
                    break

                line_clean = re.sub(r'[\s\d、]', '', line)
                if line_clean.startswith(section_clean) and '：' in line:
                    if current_section:
                        result[current_section] = "\n\n".join(current_content)
                    current_section = section
                    colon_idx = line.index('：')
                    content_after_colon = line[colon_idx + 1:].strip()
                    if content_after_colon:
                        current_content = [content_after_colon]
                    else:
                        current_content = []
                    matched = True
                    break

            if not matched and current_section:
                if line:
                    current_content.append(line)

        if current_section:
            result[current_section] = "\n\n".join(current_content)

        for section in outline:
            if section not in result or not result[section]:
                result[section] = f"关于{section}的详细内容，请参考原始素材。"

        return result

    async def execute(self, plan: Plan, callback_url: str = "") -> Plan:
        if not callback_url:
            for step in plan.steps:
                step.status = "completed"
            return plan

        result_data = {}
        for step in plan.steps:
            step.status = "running"
            await self._notify(callback_url, plan, step)

            try:
                if plan.task == "generate_ppt":
                    result_data = await self._handle_generate_ppt_step(step, plan.message, result_data)
                elif plan.task == "generate_doc":
                    result_data = await self._handle_generate_doc_step(step, plan.message, result_data)
                else:
                    await asyncio.sleep(1.5)

                step.status = "completed"
                await self._notify(callback_url, plan, step)
            except Exception as e:
                logger.error("Step %s failed: %s", step.step, e)
                step.status = "failed"
                await self._notify(callback_url, plan, step)
                break

        if result_data.get("file_path"):
            plan.result = {
                "file_name": os.path.basename(result_data["file_path"]),
                "file_size": os.path.getsize(result_data["file_path"]),
                "download_url": f"/download/{os.path.basename(result_data['file_path'])}"
            }
        return plan

    async def _handle_generate_ppt_step(self, step, message, result_data):
        if step.step == "analyze_requirements":
            result_data["topic"] = message
            await asyncio.sleep(0.5)
        elif step.step == "generate_outline":
            try:
                raw_output = await self._call_llm(message)
                logger.info("LLM outline output: %s", raw_output)

                ppt_data = self._parse_json(raw_output)
                if ppt_data is None:
                    raise ValueError("Failed to parse LLM output as JSON")

                result_data["outline"] = ppt_data.get("outline", [])
                result_data["slides_content"] = ppt_data.get("slides_content", [])
                logger.info("Generated %d outline items, %d slides", len(result_data["outline"]), len(result_data["slides_content"]))
            except Exception as e:
                logger.error("Failed to generate outline: %s", e)
                result_data["outline"] = ["封面页", "目录页", "内容页1", "内容页2", "总结页"]
                result_data["slides_content"] = [{"title": "封面页", "content": message}]
            await asyncio.sleep(1)
        elif step.step == "generate_slides":
            if not result_data.get("slides_content"):
                try:
                    raw_output = await self._call_llm(message)
                    logger.info("LLM slides output: %s", raw_output)

                    ppt_data = self._parse_json(raw_output)
                    if ppt_data is None:
                        raise ValueError("Failed to parse LLM output as JSON")

                    result_data["slides_content"] = ppt_data.get("slides_content", [])
                except Exception as e:
                    logger.error("Failed to generate slides: %s", e)
                    result_data["slides_content"] = [{"title": "内容页", "content": message}]
            await asyncio.sleep(1.5)
        elif step.step == "build_ppt":
            slides_content = result_data.get("slides_content", [])
            if not slides_content:
                slides_content = [{"title": "主题", "content": message}]
            file_path = PPTGenerator.generate_ppt(result_data["topic"], slides_content)
            result_data["file_path"] = file_path
        return result_data

    async def _handle_generate_doc_step(self, step, message, result_data):
        if step.step == "analyze_requirements":
            result_data["topic"] = self._extract_title_from_content(message)
            await asyncio.sleep(1)
        elif step.step == "generate_outline":
            outline_from_content = self._extract_outline_from_content(message)
            if outline_from_content:
                result_data["outline"] = outline_from_content
            else:
                result_data["outline"] = [
                    "1. 文档概述",
                    "2. 背景介绍",
                    "3. 核心内容",
                    "4. 实施方案",
                    "5. 效果评估",
                    "6. 总结与展望"
                ]
            await asyncio.sleep(1.5)
        elif step.step == "generate_content":
            try:
                raw_output = await self._call_llm_doc(message, result_data.get("outline", []))
                logger.info("LLM doc content output: %s", raw_output)
                result_data["content"] = self._parse_doc_content(raw_output, result_data.get("outline", []))
            except Exception as e:
                logger.error("Failed to generate doc content: %s", e)
                result_data["content"] = {}
                for section in result_data.get("outline", []):
                    result_data["content"][section] = f"这是{section}的详细内容，基于您提供的主题生成。"
            await asyncio.sleep(2)
        elif step.step == "format_doc":
            file_path = DocGenerator.generate_doc(result_data["topic"], result_data["content"])
            result_data["file_path"] = file_path
        return result_data

    def _extract_outline_from_content(self, content: str) -> list:
        lines = content.split("\n")
        outline = []
        for line in lines:
            line = line.strip()
            if not line:
                continue
            if line.startswith("#") or line.startswith("一、") or line.startswith("二、") or line.startswith("三、") or line.startswith("四、") or line.startswith("五、") or line.startswith("六、"):
                if line.startswith("#"):
                    clean_title = line.lstrip("#").strip()
                else:
                    clean_title = line
                if clean_title and clean_title not in outline:
                    outline.append(clean_title)
        return outline

    def _extract_title_from_content(self, content: str) -> str:
        lines = content.split("\n")
        for line in lines:
            line = line.strip()
            if not line:
                continue
            if line.startswith("#"):
                continue
            if len(line) <= 50:
                return line
        return "文档"

    async def _notify(self, callback_url: str, plan: Plan, current_step):
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(10)) as client:
                payload = {
                    "task": plan.task,
                    "message": plan.message,
                    "steps": [s.to_dict() for s in plan.steps],
                }
                if hasattr(plan, "result") and plan.result:
                    payload["result"] = plan.result
                await client.post(callback_url, json=payload)
        except Exception as e:
            logger.warning("Failed to notify progress to %s: %s", callback_url, e)