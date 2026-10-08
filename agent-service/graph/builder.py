"""
LangGraph 状态图构建
Router → Recommend / Service / Analytics / Chitchat → END
"""
from langgraph.graph import StateGraph, END

from graph.state import AgentState
from graph.nodes.router import router_node
from graph.nodes.recommend import recommend_node
from graph.nodes.service import service_node
from graph.nodes.analytics import analytics_node
from graph.nodes.chitchat import chitchat_node


def route_by_intent(state: AgentState) -> str:
    """根据 Router 分类结果决定走向哪个 Agent"""
    intent = state.get("intent", "chitchat")
    return intent


def build_graph() -> StateGraph:
    """
    构建多 Agent 状态图：

        [START] → router ─┬→ recommend → [END]
                          ├→ service   → [END]
                          ├→ analytics → [END]
                          └→ chitchat  → [END]
    """
    graph = StateGraph(AgentState)

    # 添加所有节点
    graph.add_node("router", router_node)
    graph.add_node("recommend", recommend_node)
    graph.add_node("service", service_node)
    graph.add_node("analytics", analytics_node)
    graph.add_node("chitchat", chitchat_node)

    # 入口：从 router 开始
    graph.set_entry_point("router")

    # 条件边：router → 根据意图分发到专业 Agent
    graph.add_conditional_edges(
        "router",
        route_by_intent,
        {
            "recommend": "recommend",
            "service": "service",
            "analytics": "analytics",
            "chitchat": "chitchat",
        },
    )

    # 所有专业 Agent 执行完后 → END
    graph.add_edge("recommend", END)
    graph.add_edge("service", END)
    graph.add_edge("analytics", END)
    graph.add_edge("chitchat", END)

    return graph


def compile_graph():
    """编译状态图，返回可执行的 app"""
    graph = build_graph()
    return graph.compile()
