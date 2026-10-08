"""
Service Agent 节点 — 预约管理 + 规则问答（RAG 增强）
"""
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from graph.state import AgentState
from prompts.service_prompt import SERVICE_SYSTEM_PROMPT
from tools.reservation_tools import reserve_spot, confirm_arrival, cancel_reservation, query_my_reservations
from tools.order_tools import checkout_order, pay_order, query_my_orders
from tools.rag_tools import query_knowledge_base
from config import config


def service_node(state: AgentState) -> dict:
    """
    Service Agent 节点：处理预约管理和规则问答。
    集成 RAG 知识库检索。
    """
    llm = ChatOpenAI(
        openai_api_base=config.LLM_BASE_URL,
        openai_api_key=config.LLM_API_KEY,
        model=config.LLM_MODEL,
        temperature=0.3,
        max_tokens=600,
    )

    tools = [
        reserve_spot, confirm_arrival, cancel_reservation, query_my_reservations,
        checkout_order, pay_order, query_my_orders,
        query_knowledge_base,
    ]

    prompt = ChatPromptTemplate.from_messages([
        ("system", SERVICE_SYSTEM_PROMPT + "\n\n当前用户ID: {user_id}"),
        MessagesPlaceholder(variable_name="chat_history"),
        ("human", "{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])

    agent = create_tool_calling_agent(llm, tools, prompt)
    executor = AgentExecutor(agent=agent, tools=tools, verbose=True, max_iterations=5)

    chat_history = state["messages"][:-1] if len(state["messages"]) > 1 else []
    user_input = state["messages"][-1].content

    result = executor.invoke({
        "input": user_input,
        "chat_history": chat_history,
        "user_id": state.get("user_id", ""),
    })

    reply = result.get("output", "抱歉，处理服务请求时出现问题。")
    return {
        "final_reply": reply,
        "messages": [],
    }
