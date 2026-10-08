"""
Analytics Agent 节点 — 数据分析与洞察
"""
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from graph.state import AgentState
from prompts.analytics_prompt import ANALYTICS_SYSTEM_PROMPT
from tools.analytics_tools import get_occupancy_stats, get_revenue_report, predict_occupancy
from config import config


def analytics_node(state: AgentState) -> dict:
    """
    Analytics Agent 节点：处理数据查询和统计分析。
    """
    llm = ChatOpenAI(
        openai_api_base=config.LLM_BASE_URL,
        openai_api_key=config.LLM_API_KEY,
        model=config.LLM_MODEL,
        temperature=0.3,
        max_tokens=600,
    )

    tools = [get_occupancy_stats, get_revenue_report, predict_occupancy]

    prompt = ChatPromptTemplate.from_messages([
        ("system", ANALYTICS_SYSTEM_PROMPT),
        MessagesPlaceholder(variable_name="chat_history"),
        ("human", "{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])

    agent = create_tool_calling_agent(llm, tools, prompt)
    executor = AgentExecutor(agent=agent, tools=tools, verbose=True, max_iterations=3)

    chat_history = state["messages"][:-1] if len(state["messages"]) > 1 else []
    user_input = state["messages"][-1].content

    result = executor.invoke({
        "input": user_input,
        "chat_history": chat_history,
    })

    reply = result.get("output", "抱歉，处理数据查询时出现问题。")
    return {
        "final_reply": reply,
        "messages": [],
    }
