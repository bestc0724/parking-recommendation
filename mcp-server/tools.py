"""
MCP Tools — 停车服务操作工具
每个 Tool 对应 Java 后端的一个内部 API 端点
"""
import httpx
from config import JAVA_BACKEND_URL, INTERNAL_HEADERS


async def recommend_parking(query: str, user_id: str = "") -> str:
    """根据自然语言描述推荐停车位。
    用户可以描述限高、充电需求、价格偏好等条件。
    返回最匹配的 Top 3 车位及推荐理由。"""
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{JAVA_BACKEND_URL}/api/internal/recommend",
            json={"query": query, "userId": user_id},
            headers=INTERNAL_HEADERS,
        )
        data = resp.json()

    status = data.get("status")
    if status == "success":
        recs = data.get("recommendations", [])
        lines = [f"找到 {len(recs)} 个推荐车位："]
        for i, r in enumerate(recs, 1):
            charging = "有充电桩" if r.get("hasCharging") else "无充电桩"
            lines.append(
                f"{i}. {r['spotId']} | 限高{r.get('heightLimit', '?')}米 | "
                f"{charging} | 距电梯{r.get('distanceToElevator', '?')}米 | "
                f"{r.get('pricePerHour', '?')}元/小时 | 评分: {r.get('score', '?')}"
            )
            if r.get("reason"):
                lines.append(f"   推荐理由: {r['reason']}")
        return "\n".join(lines)

    if status == "no_match":
        return f"没有找到符合条件的车位。{data.get('message', '建议放宽部分条件重试。')}"

    if status == "no_available":
        return "当前没有可用车位，请稍后再试。"

    return f"推荐结果: {data}"


async def reserve_parking(user_id: str, spot_id: str) -> str:
    """预约指定车位。预约后1小时内有效，超时自动取消。到达车位后需确认到场。"""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{JAVA_BACKEND_URL}/api/internal/reserve",
            json={"userId": user_id, "spotId": spot_id},
            headers=INTERNAL_HEADERS,
        )
        data = resp.json()

    if data.get("status") == "reserved":
        return (
            f"预约成功！\n"
            f"预约号: {data.get('reservationId')}\n"
            f"车位: {data.get('spotId')}\n"
            f"有效期至: {data.get('expiresAt')}\n"
            f"请在1小时内到达车位。"
        )
    return f"预约失败: {data.get('error', data)}"


async def cancel_reservation(reservation_id: str) -> str:
    """取消已有的预约，车位将被释放。"""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.delete(
            f"{JAVA_BACKEND_URL}/api/internal/reserve/{reservation_id}/cancel",
            headers=INTERNAL_HEADERS,
        )
        data = resp.json()

    if data.get("status") == "cancelled":
        return f"预约 {reservation_id} 已取消，车位已释放。"
    return f"取消失败: {data.get('error', data)}"


async def confirm_arrival(reservation_id: str) -> str:
    """确认用户已到达车位，将预约状态从 pending 变为 confirmed。"""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.put(
            f"{JAVA_BACKEND_URL}/api/internal/reserve/{reservation_id}/confirm",
            headers=INTERNAL_HEADERS,
        )
        data = resp.json()

    if data.get("status") == "confirmed":
        return "已确认到场，祝您停车愉快！"
    return f"确认到场失败: {data.get('error', data)}"


async def query_available_spots() -> str:
    """查询当前所有可用车位的数量和列表。"""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{JAVA_BACKEND_URL}/api/internal/spots/available", headers=INTERNAL_HEADERS)
        data = resp.json()

    spots = data.get("spots", [])
    total = data.get("total", 0)
    if total == 0:
        return "当前没有可用车位。"

    lines = [f"当前共有 {total} 个可用车位："]
    for s in spots[:10]:
        charging = "充电" if s.get("hasCharging") else "普通"
        lines.append(
            f"- {s['spotId']}: {s.get('spotType', 'regular')} | "
            f"限高{s.get('heightLimit', '?')}m | {charging} | "
            f"距电梯{s.get('distanceToElevator', '?')}m | "
            f"{s.get('pricePerHour', '?')}元/h"
        )
    if total > 10:
        lines.append(f"...还有 {total - 10} 个车位")
    return "\n".join(lines)
