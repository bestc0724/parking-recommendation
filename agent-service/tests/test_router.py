"""
Router Agent 测试 — 规则匹配路径 + parse_intent 解析
"""
from langchain_core.messages import HumanMessage

from graph.nodes.router import router_node
from prompts.router_prompt import parse_intent


def _state(text: str) -> dict:
    return {"messages": [HumanMessage(content=text)], "intent": "", "intent_confidence": 0.0}


class TestRouterKeywordRules:
    """规则关键词匹配（不触发 LLM）"""

    def test_recommend_charging(self):
        result = router_node(_state("帮我找个有充电桩的车位"))
        assert result["intent"] == "recommend"

    def test_recommend_height(self):
        result = router_node(_state("我的车限高2.3米，想停车"))
        assert result["intent"] == "recommend"

    def test_recommend_cheaper(self):
        result = router_node(_state("刚才那个太贵了，有没有便宜点的"))
        assert result["intent"] == "recommend"

    def test_service_reserve(self):
        result = router_node(_state("我要预约第一个车位"))
        assert result["intent"] == "service"

    def test_service_rule_question(self):
        result = router_node(_state("柴油车能停B区吗"))
        assert result["intent"] == "service"

    def test_analytics_occupancy(self):
        result = router_node(_state("现在停车场占用率多少"))
        assert result["intent"] == "analytics"

    def test_analytics_revenue(self):
        result = router_node(_state("这周收入报表给我看看"))
        assert result["intent"] == "analytics"

    def test_rule_question_not_recommend(self):
        """含"停车"但属于规则问答，service 优先于 recommend"""
        result = router_node(_state("停车规则是什么"))
        assert result["intent"] == "service"

    def test_can_park_question(self):
        """"X能停Y区吗" 类规则询问"""
        result = router_node(_state("我的SUV可以停在C区吗"))
        assert result["intent"] == "service"


class TestParseIntent:
    """LLM 兜底分类输出的 JSON 解析"""

    def test_valid_json(self):
        result = parse_intent('{"intent": "recommend", "confidence": 0.95, "reason": "用户想找车位"}')
        assert result["intent"] == "recommend"
        assert result["confidence"] == 0.95

    def test_markdown_wrapped_json(self):
        result = parse_intent('```json\n{"intent": "service", "confidence": 0.8, "reason": "预约管理"}\n```')
        assert result["intent"] == "service"

    def test_invalid_json_falls_back_to_chitchat(self):
        result = parse_intent("这不是一个合法的JSON")
        assert result["intent"] == "chitchat"

    def test_unknown_intent_normalized_to_chitchat(self):
        result = parse_intent('{"intent": "something_else", "confidence": 0.5}')
        assert result["intent"] == "chitchat"
