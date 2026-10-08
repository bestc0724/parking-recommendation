"""
Chitchat 节点 — 闲聊 / 打招呼 / 不属于其他类别的对话
不调用任何工具，LLM 直接回复
"""
from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage

from graph.state import AgentState
from config import config

CHITCHAT_SYSTEM_PROMPT = """你是一个智能停车助手。你主要帮助用户找车位、预约车位和管理预约。

当用户打招呼或闲聊时，礼貌回应，并简要介绍你的能力：
- 帮用户根据需求推荐车位
- 预约、确认到场、取消预约
- 查询停车场规则和数据统计

引导用户描述他们的停车需求。用用户使用的语言回复。"""


def chitchat_node(state: AgentState) -> dict:
    """闲聊节点：不调工具，LLM 直接回复"""
    llm = ChatOpenAI(
        openai_api_base=config.LLM_BASE_URL,
        openai_api_key=config.LLM_API_KEY,
        model=config.LLM_MODEL,
        temperature=0.5,
        max_tokens=300,
    )

    messages = [SystemMessage(content=CHITCHAT_SYSTEM_PROMPT)] + list(state["messages"])
    response = llm.invoke(messages)

    return {
        "final_reply": response.content,
        "messages": [],
    }
