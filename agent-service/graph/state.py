"""
LangGraph 共享状态定义
所有 Agent 节点共享这个状态，通过 state 传递上下文
"""
from typing import Annotated, TypedDict
from langgraph.graph import add_messages


class AgentState(TypedDict):
    """多 Agent 共享状态"""

    # 对话消息列表（LangGraph 自动管理追加）
    messages: Annotated[list, add_messages]

    # Router 分类结果
    intent: str  # recommend / service / analytics / chitchat
    intent_confidence: float
    intent_reason: str

    # 用户上下文
    user_id: str
    session_id: str

    # 最终回复
    final_reply: str
