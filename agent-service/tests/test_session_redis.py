"""
Redis 会话模式测试 — 未配置 REDIS_URL 时跳过（默认环境即跳过）
配置 REDIS_URL 后可用：REDIS_URL=redis://localhost:6379/1 pytest tests/test_session_redis.py
"""
import pytest

from memory.session import SessionManager


@pytest.mark.skipif(
    not SessionManager().using_redis,
    reason="未配置 REDIS_URL，Redis 模式测试跳过",
)
class TestSessionManagerRedis:
    def test_redis_get_history(self):
        sm = SessionManager(ttl_minutes=60)
        history = sm.get_history("redis-test-1")
        history.clear()  # 先清干净
        history.add_user_message("hello")
        assert len(history.messages) == 1

    def test_redis_clear(self):
        sm = SessionManager(ttl_minutes=60)
        history = sm.get_history("redis-test-2")
        history.add_user_message("to be deleted")
        assert sm.clear("redis-test-2") is True
        assert len(sm.get_history("redis-test-2").messages) == 0

    def test_redis_sessions_isolated(self):
        sm = SessionManager(ttl_minutes=60)
        h1 = sm.get_history("redis-test-a")
        h2 = sm.get_history("redis-test-b")
        h1.clear(); h2.clear()
        h1.add_user_message("from a")
        assert len(h2.messages) == 0
