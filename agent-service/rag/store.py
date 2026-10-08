"""
Qdrant 向量数据库操作 — 创建 collection、写入文档、查询
"""
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    VectorParams,
    PointStruct,
    Filter,
    FieldCondition,
    MatchValue,
)
from config import config
from rag.embedder import embed_documents, embed_query


class VectorStore:
    """Qdrant 向量存储封装"""

    def __init__(self):
        self.client = QdrantClient(host=config.QDRANT_HOST, port=config.QDRANT_PORT)
        self.collection = config.QDRANT_COLLECTION

    def init_collection(self, force: bool = False):
        """初始化 collection（如果不存在则创建）"""
        collections = [c.name for c in self.client.get_collections().collections]
        if self.collection in collections:
            if force:
                self.client.delete_collection(self.collection)
            else:
                return

        self.client.create_collection(
            collection_name=self.collection,
            vectors_config=VectorParams(size=512, distance=Distance.COSINE),
        )

    def add_documents(self, chunks: list[dict]):
        """
        将 chunks 写入 Qdrant。
        chunks: [{"content": "...", "metadata": {...}}, ...]
        """
        if not chunks:
            return

        texts = [c["content"] for c in chunks]
        embeddings = embed_documents(texts)

        points = []
        for i, (chunk, embedding) in enumerate(zip(chunks, embeddings)):
            payload = {"content": chunk["content"], **chunk["metadata"]}
            points.append(PointStruct(id=i, vector=embedding, payload=payload))

        self.client.upsert(collection_name=self.collection, points=points)

    def search(
        self,
        query: str,
        top_k: int = 3,
        category: str = None,
    ) -> list[dict]:
        """
        语义检索。
        Args:
            query: 用户问题
            top_k: 返回数量
            category: 可选过滤（"规则" / "FAQ" / "区域"）
        Returns:
            [{"content": "...", "score": 0.85, "source": "...", "category": "..."}, ...]
        """
        query_embedding = embed_query(query)

        query_filter = None
        if category:
            query_filter = Filter(
                must=[FieldCondition(key="category", match=MatchValue(value=category))]
            )

        results = self.client.query_points(
            collection_name=self.collection,
            query=query_embedding,
            query_filter=query_filter,
            limit=top_k,
        )

        return [
            {
                "content": point.payload.get("content", ""),
                "score": round(point.score, 4),
                "source": point.payload.get("source", ""),
                "category": point.payload.get("category", ""),
            }
            for point in results.points
        ]
