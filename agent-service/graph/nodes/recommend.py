"""
Recommend Agent 节点 — 使用 LangChain Agent 调用推荐相关工具
"""
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import SystemMessage

from graph.state import AgentState
from prompts.recommend_prompt import RECOMMEND_SYSTEM_PROMPT
from tools.recommend_tools import recommend_spot, query_available_spots
from tools.reservation_tools import reserve_spot
from tools.vehicle_tools import query_my_vehicles, add_vehicle
from config import config


def recommend_node(state: AgentState) -> dict:
    """
    Recommend Agent 节点：处理推荐和预约相关请求。
    使用 LangChain Agent 自主决策调用哪些工具。
    """
    llm = ChatOpenAI(
        openai_api_base=config.LLM_BASE_URL,
        openai_api_key=config.LLM_API_KEY,
        model=config.LLM_MODEL,
        temperature=0.3,
        max_tokens=800,
    )

    tools = [recommend_spot, query_available_spots, reserve_spot, query_my_vehicles, add_vehicle]

    prompt = ChatPromptTemplate.from_messages([
        ("system", RECOMMEND_SYSTEM_PROMPT + "\n\n当前用户ID: {user_id}"),
        MessagesPlaceholder(variable_name="chat_history"),
        ("human", "{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])

    agent = create_tool_calling_agent(llm, tools, prompt)
    executor = AgentExecutor(agent=agent, tools=tools, verbose=True, max_iterations=5)

    # 提取对话历史（排除最新的用户消息）
    chat_history = state["messages"][:-1] if len(state["messages"]) > 1 else []
    user_input = state["messages"][-1].content

    result = executor.invoke({
        "input": user_input,
        "chat_history": chat_history,
        "user_id": state.get("user_id", ""),
    })

    reply = result.get("output", "抱歉，处理推荐请求时出现问题。")
    return {
        "final_reply": reply,
        "messages": [],  # 不追加到 messages，由 final_reply 承载
    }
