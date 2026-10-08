"""
会话记忆测试
"""
from memory.session import SessionManager


class TestSessionManager:
    def test_get_history_creates_session(self):
        sm = SessionManager(ttl_minutes=60, max_history=10)
        history = sm.get_history("s1")
        history.add_user_message("hi")
        assert sm.active_count() == 1
        assert len(history.messages) == 1

    def test_get_context_returns_role_content(self):
        sm = SessionManager(ttl_minutes=60, max_history=10)
        history = sm.get_history("s2")
        history.add_user_message("你好")
        history.add_ai_message("您好")
        ctx = sm.get_context("s2")
        assert ctx == [
            {"role": "human", "content": "你好"},
            {"role": "ai", "content": "您好"},
        ]

    def test_clear_session(self):
        sm = SessionManager(ttl_minutes=60, max_history=10)
        sm.get_history("s3")
        assert sm.clear("s3") is True
        assert sm.clear("s3") is False
        assert sm.active_count() == 0

    def test_sessions_are_isolated(self):
        sm = SessionManager(ttl_minutes=60, max_history=10)
        h1 = sm.get_history("a")
        h2 = sm.get_history("b")
        h1.add_user_message("message from a")
        assert len(h2.messages) == 0

    def test_history_trimmed_to_max(self):
        sm = SessionManager(ttl_minutes=60, max_history=3)
        history = sm.get_history("s4")
        for i in range(10):
            history.add_user_message(f"msg {i}")
        # 裁剪在 get_history 时触发（对应每次请求进入 main.py 的真实流程）
        history = sm.get_history("s4")
        # max_history=3 → 每轮 2 条，最多 6 条，10 条会被裁剪
        assert len(history.messages) <= 6
        # 保留的是最新的消息
        assert history.messages[-1].content == "msg 9"
