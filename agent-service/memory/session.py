"""
会话记忆管理
- 每个 session_id 维护独立的对话历史
- 配置 REDIS_URL 时使用 Redis 持久化（重启不丢、多实例共享），否则使用内存
- 自动清理过期会话（Redis 用 key TTL，内存用时间戳扫描）
"""
import time
from typing import Optional

from langchain_core.chat_history import InMemoryChatMessageHistory

from config import config


def _make_redis_history(session_id: str, ttl_seconds: int):
    """
    创建 Redis 会话历史并设置 TTL。
    RedisChatMessageHistory 本身不设置过期时间，需要手动 expire。
    """
    from langchain_community.chat_message_histories import RedisChatMessageHistory
    from redis import Redis

    history = RedisChatMessageHistory(
        session_id=session_id,
        url=config.REDIS_URL,
        ttl=ttl_seconds,
    )
    # ttl 参数在部分版本不生效，手动兜底设置一次
    try:
        client: Redis = history.redis_client
        client.expire(history.key, ttl_seconds)
    except Exception:
        pass  # 设置 TTL 失败不影响会话读写
    return history


class SessionManager:
    """管理多用户会话，每个 session_id 对应独立的对话历史"""

    def __init__(self, ttl_minutes: int = None, max_history: int = None):
        self._sessions: dict[str, dict] = {}  # 内存模式: {session_id: {history, last_active}}
        self._ttl = (ttl_minutes or config.SESSION_TTL_MINUTES) * 60  # 转为秒
        self._max_history = max_history or config.MAX_CHAT_HISTORY
        self._redis_mode = bool(config.REDIS_URL)

    @property
    def using_redis(self) -> bool:
        return self._redis_mode

    def get_history(self, session_id: str):
        """获取或创建会话历史，自动清理过期会话"""
        if self._redis_mode:
            return _make_redis_history(session_id, self._ttl)

        # 内存模式
        self._cleanup_expired()
        now = time.time()

        if session_id not in self._sessions:
            self._sessions[session_id] = {
                "history": InMemoryChatMessageHistory(),
                "last_active": now,
            }

        self._sessions[session_id]["last_active"] = now
        history = self._sessions[session_id]["history"]

        # 裁剪过长的历史（保留最近 max_history 条）
        messages = history.messages
        if len(messages) > self._max_history * 2:  # 每轮有 human + ai 两条
            history.messages = messages[-(self._max_history * 2):]

        return history

    def get_context(self, session_id: str) -> list[dict]:
        """获取会话上下文（用于构建 LangGraph state）"""
        history = self.get_history(session_id)
        return [
            {"role": msg.type, "content": msg.content}
            for msg in history.messages
        ]

    def clear(self, session_id: str) -> bool:
        """清除指定会话"""
        if self._redis_mode:
            try:
                history = _make_redis_history(session_id, self._ttl)
                history.clear()
                return True
            except Exception:
                return False

        if session_id in self._sessions:
            del self._sessions[session_id]
            return True
        return False

    def active_count(self) -> int:
        """当前活跃会话数。Redis 模式下返回 -1（无法低成本枚举）"""
        if self._redis_mode:
            return -1
        self._cleanup_expired()
        return len(self._sessions)

    def _cleanup_expired(self):
        """清理过期会话（仅内存模式）"""
        now = time.time()
        expired = [
            sid for sid, data in self._sessions.items()
            if now - data["last_active"] > self._ttl
        ]
        for sid in expired:
            del self._sessions[sid]


# 全局单例
session_manager = SessionManager()
