"""
Embedding 封装 — 使用 fastembed 本地模型（无需外部 API）

模型: BAAI/bge-small-zh-v1.5（中文优化，512维，轻量级）
备选: BAAI/bge-small-en-v1.5（英文场景）

首次运行会自动下载模型到本地缓存（~30MB）
"""
import os

# 必须在 fastembed / huggingface_hub 导入前设置
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")

from typing import List

from fastembed import TextEmbedding
from config import config

# 全局单例（延迟加载）
_model: TextEmbedding = None


def get_embedder() -> TextEmbedding:
    """获取 Embedding 模型实例（本地推理，首次调用会下载模型）"""
    global _model
    if _model is None:
        model_name = config.EMBEDDING_MODEL
        # fastembed 模型名与 OpenAI 不同，做映射
        if model_name in ("text-embedding-3-small", "text-embedding-3-large"):
            model_name = "BAAI/bge-small-zh-v1.5"  # 中文优化，默认选择
        _model = TextEmbedding(model_name=model_name)
    return _model


def embed_documents(texts: List[str]) -> List[List[float]]:
    """批量向量化文档"""
    model = get_embedder()
    embeddings = list(model.embed(texts))
    return [e.tolist() for e in embeddings]


def embed_query(text: str) -> List[float]:
    """向量化查询文本"""
    model = get_embedder()
    embeddings = list(model.embed([text]))
    return embeddings[0].tolist()
