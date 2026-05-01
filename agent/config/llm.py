import os
from pathlib import Path
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI


def load_env():
    env_path = Path(__file__).parent.parent / ".env"
    if env_path.exists():
        load_dotenv(dotenv_path=env_path)


load_env()


def get_llm() -> ChatOpenAI:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise ValueError("OPENAI_API_KEY environment variable is not set")

    base_url = os.getenv("OPENAI_BASE_URL", "sk-1705d992e2a7421eb9928a0f9c0baedd")
    model_name = os.getenv("OPENAI_MODEL", "deepseek-v4-flash")
    temperature = float(os.getenv("AGENT_TEMPERATURE", "0.3"))

    return ChatOpenAI(
        api_key=api_key,
        base_url=base_url,
        model=model_name,
        temperature=temperature,
    )
