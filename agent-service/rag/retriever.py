"""
RAG 检索器 — 对外接口：question → 相关文档片段
"""
from rag.store import VectorStore

# 全局单例（延迟初始化）
_store: VectorStore | None = None


def get_store() -> VectorStore:
    global _store
    if _store is None:
        _store = VectorStore()
    return _store


def retrieve(question: str, top_k: int = 3, category: str = None) -> str:
    """
    检索与问题最相关的文档片段，拼成 context 字符串返回。

    Args:
        question: 用户问题
        top_k: 返回条数
        category: 可选过滤
    Returns:
        拼接好的 context 字符串，可直接插入 Agent prompt
    """
    store = get_store()
    results = store.search(question, top_k=top_k, category=category)

    if not results:
        return "（知识库中未找到相关信息）"

    context_parts = []
    for i, r in enumerate(results, 1):
        context_parts.append(
            f"[{i}] (来源: {r['source']}, 相关度: {r['score']})\n{r['content']}"
        )

    return "\n\n".join(context_parts)


def init_knowledge_base(force: bool = False):
    """
    初始化知识库：加载文档 → 切分 → 向量化 → 存入 Qdrant。
    启动时调用一次即可。
    """
    from rag.loader import load_documents

    store = get_store()
    store.init_collection(force=force)

    chunks = load_documents()
    if chunks:
        store.add_documents(chunks)
        print(f"[RAG] 已加载 {len(chunks)} 个文档片段到 Qdrant")
    else:
        print("[RAG] 未找到知识文档，跳过初始化")
