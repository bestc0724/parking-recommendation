"""
Router Agent 的意图分类 prompt
"""
import json

ROUTER_SYSTEM_PROMPT = """你是一个意图分类器，负责将用户消息分类到以下四个类别之一：

1. **recommend** — 用户想要查找/推荐停车位
   关键词: 推荐、找车位、停车、限高、充电桩、便宜、离电梯近、帮我找个、有没有车位

2. **service** — 用户想要管理预约（确认到场、取消、查看预约）或询问停车场规则/FAQ
   关键词: 预约、取消、确认到场、我的预约、到期、规则、收费、能不能停、怎么办卡

3. **analytics** — 用户想要查看数据统计（占用率、收入、趋势）
   关键词: 统计、占用率、收入、报表、趋势、多少车位、数据

4. **chitchat** — 闲聊、打招呼、或其他不属于以上类别的话题

只返回一个 JSON，不要返回其他内容:
{"intent": "分类结果", "confidence": 0.0-1.0, "reason": "简短理由"}"""

ROUTER_FEWSHOTS = [
    {"role": "user", "content": "帮我找个有充电桩的车位"},
    {"role": "assistant", "content": '{"intent": "recommend", "confidence": 0.95, "reason": "用户想找车位"}'},
    {"role": "user", "content": "我要预约第一个"},
    {"role": "assistant", "content": '{"intent": "service", "confidence": 0.9, "reason": "用户要预约"}'},
    {"role": "user", "content": "现在停车场还有多少空位？"},
    {"role": "assistant", "content": '{"intent": "analytics", "confidence": 0.85, "reason": "用户询问车位数量统计"}'},
    {"role": "user", "content": "你好"},
    {"role": "assistant", "content": '{"intent": "chitchat", "confidence": 0.9, "reason": "打招呼"}'},
    {"role": "user", "content": "柴油车能停B区吗"},
    {"role": "assistant", "content": '{"intent": "service", "confidence": 0.85, "reason": "询问停车场规则"}'},
    {"role": "user", "content": "刚才那个太贵了，有没有便宜点的"},
    {"role": "assistant", "content": '{"intent": "recommend", "confidence": 0.9, "reason": "用户想重新推荐更便宜的车位"}'},
]


def parse_intent(llm_output: str) -> dict:
    """解析 LLM 的意图分类输出"""
    try:
        # 尝试从可能包含 markdown 代码块的输出中提取 JSON
        text = llm_output.strip()
        if "```" in text:
            text = text.split("```")[1]
            if text.startswith("json"):
                text = text[4:]
            text = text.strip()

        result = json.loads(text)
        intent = result.get("intent", "chitchat")
        if intent not in ("recommend", "service", "analytics", "chitchat"):
            intent = "chitchat"
        result["intent"] = intent
        return result
    except (json.JSONDecodeError, IndexError):
        return {"intent": "chitchat", "confidence": 0.5, "reason": "无法解析分类结果"}
