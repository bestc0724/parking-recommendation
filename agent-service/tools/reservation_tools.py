"""
预约管理工具 — 调用 Java 后端预约相关接口
"""
import httpx
from langchain_core.tools import tool
from config import config

JAVA_URL = config.JAVA_BACKEND_URL
INTERNAL_HEADERS = {"X-Internal-Key": config.INTERNAL_API_KEY}


@tool
def reserve_spot(user_id: str, spot_id: str) -> str:
    """预约指定车位。预约后1小时内有效，超时自动取消。
    用户到达车位后需调用 confirm_arrival 确认到场。

    Args:
        user_id: 用户ID
        spot_id: 要预约的车位ID，如 "S001"
    """
    try:
        resp = httpx.post(
            f"{JAVA_URL}/api/internal/reserve",
            json={"userId": user_id, "spotId": spot_id},
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        status = data.get("status")

        if status == "reserved":
            return (
                f"预约成功！\n"
                f"- 预约号: {data.get('reservationId')}\n"
                f"- 车位: {data.get('spotId')}\n"
                f"- 有效期至: {data.get('expiresAt')}\n"
                f"- 请在1小时内到达车位，到达后告诉我\"确认到场\""
            )

        return f"预约失败: {data.get('error', data)}"
    except Exception as e:
        return f"预约请求失败: {e}"


@tool
def confirm_arrival(reservation_id: str) -> str:
    """确认用户已到达车位，将预约状态从 pending 变为 confirmed。

    Args:
        reservation_id: 预约号，如 "R1A2B3C4"
    """
    try:
        resp = httpx.put(
            f"{JAVA_URL}/api/internal/reserve/{reservation_id}/confirm",
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        if data.get("status") == "confirmed":
            return "已确认到场，祝您停车愉快！"
        return f"确认到场失败: {data.get('error', data)}"
    except Exception as e:
        return f"确认到场请求失败: {e}"


@tool
def cancel_reservation(reservation_id: str) -> str:
    """取消已有的预约，车位将被释放。

    Args:
        reservation_id: 预约号，如 "R1A2B3C4"
    """
    try:
        resp = httpx.delete(
            f"{JAVA_URL}/api/internal/reserve/{reservation_id}/cancel",
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        if data.get("status") == "cancelled":
            return f"预约 {reservation_id} 已取消，车位已释放。"
        return f"取消失败: {data.get('error', data)}"
    except Exception as e:
        return f"取消预约请求失败: {e}"


@tool
def query_my_reservations(user_id: str) -> str:
    """查询指定用户的所有预约记录。

    Args:
        user_id: 用户ID
    """
    try:
        resp = httpx.get(
            f"{JAVA_URL}/api/internal/reservations",
            params={"userId": user_id},
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        reservations = data.get("reservations", [])
        total = data.get("total", 0)

        if total == 0:
            return "您当前没有任何预约记录。"

        lines = [f"您共有 {total} 条预约记录："]
        for r in reservations:
            lines.append(
                f"- 预约号: {r['reservationId']} | 车位: {r['spotId']} | "
                f"状态: {r['status']} | 预约时间: {r.get('reservedAt', '?')} | "
                f"过期时间: {r.get('expiresAt', '无')}"
            )
        return "\n".join(lines)
    except Exception as e:
        return f"查询预约失败: {e}"
