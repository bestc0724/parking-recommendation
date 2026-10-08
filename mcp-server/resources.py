"""
MCP Resources — 可被 AI 客户端读取的数据
"""
import httpx
from config import JAVA_BACKEND_URL, INTERNAL_HEADERS


async def get_available_spots_resource() -> str:
    """parking://spots/available — 当前所有可用车位"""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{JAVA_BACKEND_URL}/api/internal/spots/available", headers=INTERNAL_HEADERS)
        data = resp.json()
    return f"可用车位 ({data.get('total', 0)} 个):\n{data}"


async def get_parking_rules_resource() -> str:
    """parking://rules — 停车场规则"""
    from pathlib import Path

    rules_path = Path(__file__).parent.parent / "docs" / "parking_rules.txt"
    if rules_path.exists():
        return rules_path.read_text(encoding="utf-8")
    return "暂无停车场规则文档。"


async def get_user_reservations_resource(user_id: str) -> str:
    """parking://user/{user_id}/reservations — 用户预约记录"""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(
            f"{JAVA_BACKEND_URL}/api/internal/reservations",
            params={"userId": user_id},
            headers=INTERNAL_HEADERS,
        )
        data = resp.json()
    return f"用户 {user_id} 的预约记录 ({data.get('total', 0)} 条):\n{data}"
