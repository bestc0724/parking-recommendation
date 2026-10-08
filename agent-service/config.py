import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    # LLM
    LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "https://api.xiaomimimo.com/v1")
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "mimo-v2.5-pro")

    # Java 后端
    JAVA_BACKEND_URL: str = os.getenv("JAVA_BACKEND_URL", "http://localhost:8080")
    # 调用 Java 内部 API 的密钥（与 Java application-dev.yml 的 internal.api-key 一致）
    INTERNAL_API_KEY: str = os.getenv("INTERNAL_API_KEY", "parking-internal-key-2024")

    # Qdrant
    QDRANT_HOST: str = os.getenv("QDRANT_HOST", "localhost")
    QDRANT_PORT: int = int(os.getenv("QDRANT_PORT", "6333"))
    QDRANT_COLLECTION: str = os.getenv("QDRANT_COLLECTION", "parking_knowledge")
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")

    # Agent
    AGENT_PORT: int = int(os.getenv("AGENT_PORT", "8001"))
    SESSION_TTL_MINUTES: int = int(os.getenv("SESSION_TTL_MINUTES", "30"))
    MAX_CHAT_HISTORY: int = int(os.getenv("MAX_CHAT_HISTORY", "20"))

    # Redis 会话存储（留空则使用内存会话）
    REDIS_URL: str = os.getenv("REDIS_URL", "")

    # Langfuse 可观测性（三项齐全才启用，留空则关闭）
    LANGFUSE_PUBLIC_KEY: str = os.getenv("LANGFUSE_PUBLIC_KEY", "")
    LANGFUSE_SECRET_KEY: str = os.getenv("LANGFUSE_SECRET_KEY", "")
    LANGFUSE_HOST: str = os.getenv("LANGFUSE_HOST", "")


config = Config()
