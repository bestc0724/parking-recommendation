"""
Router Agent 节点 — 意图分类
规则优先 + LLM 兜底
"""
import re
from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage, HumanMessage
from graph.state import AgentState
from prompts.router_prompt import ROUTER_SYSTEM_PROMPT, ROUTER_FEWSHOTS, parse_intent
from config import config


# 快速规则匹配（优先级高于 LLM）
# 注意检查顺序：analytics → service → recommend
# "停车场占用率多少" 含 "停车" 但不属于推荐意图，必须先查 analytics；
# "停车规则是什么" 含 "停车" 但属于规则问答，必须先查 service。
INTENT_PATTERNS = {
    "analytics": re.compile(
        r"(统计|占用率|收入|报表|趋势|数据|空位|还剩多少|满不满|预测|有位置|"
        r"stats|occupancy|revenue|report)", re.IGNORECASE
    ),
    "service": re.compile(
        r"(预约|取消|确认到场|我的预约|到期|过期|规则|收费|怎么办卡|能停|可以停|能不能停|"
        r"结算|离场|支付|订单|"
        r"reserve|cancel|confirm|reservation|rule)", re.IGNORECASE
    ),
    "recommend": re.compile(
        r"(推荐|找[个到]?|停车|限高|充电桩?|便宜|离电梯|有没有车位|帮我[找个]|车辆|车牌|"
        r"recommend|find.*spot|parking)", re.IGNORECASE
    ),
}


def router_node(state: AgentState) -> dict:
    """
    Router 节点：先用规则匹配，匹配不上再用 LLM 分类。
    返回: {"intent": ..., "intent_confidence": ..., "intent_reason": ...}
    """
    last_message = state["messages"][-1]
    user_text = last_message.content if hasattr(last_message, "content") else str(last_message)

    # Step 1: 规则快速匹配
    for intent, pattern in INTENT_PATTERNS.items():
        if pattern.search(user_text):
            return {
                "intent": intent,
                "intent_confidence": 0.9,
                "intent_reason": f"关键词匹配: {intent}",
            }

    # Step 2: LLM 兜底分类
    try:
        llm = ChatOpenAI(
            openai_api_base=config.LLM_BASE_URL,
            openai_api_key=config.LLM_API_KEY,
            model=config.LLM_MODEL,
            temperature=0,
            max_tokens=100,
        )

        messages = [SystemMessage(content=ROUTER_SYSTEM_PROMPT)]
        # 加入 few-shot 示例
        for example in ROUTER_FEWSHOTS:
            if example["role"] == "user":
                messages.append(HumanMessage(content=example["content"]))
            else:
                from langchain_core.messages import AIMessage
                messages.append(AIMessage(content=example["content"]))
        messages.append(HumanMessage(content=user_text))

        response = llm.invoke(messages)
        result = parse_intent(response.content)
        return {
            "intent": result["intent"],
            "intent_confidence": result.get("confidence", 0.7),
            "intent_reason": result.get("reason", "LLM 分类"),
        }
    except Exception as e:
        # LLM 调用失败，默认走闲聊
        return {
            "intent": "chitchat",
            "intent_confidence": 0.5,
            "intent_reason": f"LLM 调用失败({e})，默认闲聊",
        }
