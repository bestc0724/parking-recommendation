"""
RAG 知识库查询工具 — 供 Service Agent 回答规则类问题
"""
from langchain_core.tools import tool


@tool
def query_knowledge_base(question: str, category: str = "") -> str:
    """查询停车场知识库，回答关于规则、FAQ、区域特色等问题。
    当用户询问停车场规定、收费标准、区域限制等问题时使用。

    Args:
        question: 要查询的问题，如"柴油车能停B区吗"
        category: 可选过滤，"规则"/"FAQ"/"区域"，留空则全部搜索
    """
    from rag.retriever import retrieve

    cat = category if category in ("规则", "FAQ", "区域") else None
    result = retrieve(question, top_k=3, category=cat)
    return f"以下是从知识库中检索到的相关信息：\n\n{result}"
