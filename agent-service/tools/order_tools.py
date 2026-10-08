"""
停车订单工具 — 离场结算 / 支付 / 订单查询
对应 Java /api/internal/order/* 和 /api/internal/orders
"""
import httpx
from langchain_core.tools import tool
from config import config

JAVA_URL = config.JAVA_BACKEND_URL
INTERNAL_HEADERS = {"X-Internal-Key": config.INTERNAL_API_KEY}


@tool
def checkout_order(reservation_id: str, charging_kwh: float = 0) -> str:
    """离场结算。计算停车费（时长×单价，不足1小时按1小时）和充电费（度数×1.2元），
    生成账单并释放车位。用户表示要离开、要结算时使用。

    Args:
        reservation_id: 预约号，如 "R1A2B3C4"
        charging_kwh: 本次充电度数（没用充电桩则为0）
    """
    try:
        resp = httpx.post(
            f"{JAVA_URL}/api/internal/order/checkout",
            json={"reservationId": reservation_id, "chargingKwh": str(charging_kwh)},
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        if data.get("status") == "completed":
            return (
                f"结算完成！\n"
                f"- 订单号: {data.get('orderId')}\n"
                f"- 停车费: ¥{data.get('parkingFee')}\n"
                f"- 充电费: ¥{data.get('chargingFee')}\n"
                f"- 总金额: ¥{data.get('totalAmount')}\n"
                f"- 请告知我进行支付（支付后即完成本次停车）"
            )
        return f"结算失败: {data.get('error', data)}"
    except Exception as e:
        return f"结算请求失败: {e}"


@tool
def pay_order(order_id: str) -> str:
    """支付停车订单。结算完成后用户确认支付时使用。

    Args:
        order_id: 订单号，如 "O1A2B3C4"
    """
    try:
        resp = httpx.post(
            f"{JAVA_URL}/api/internal/order/{order_id}/pay",
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        if data.get("status") == "paid":
            return f"支付成功！金额 ¥{data.get('amount')}。感谢您的使用，欢迎下次光临！"
        return f"支付失败: {data.get('error', data)}"
    except Exception as e:
        return f"支付请求失败: {e}"


@tool
def query_my_orders(user_id: str) -> str:
    """查询指定用户的停车订单记录（含停车中、已结算、已支付）。

    Args:
        user_id: 用户ID
    """
    try:
        resp = httpx.get(
            f"{JAVA_URL}/api/internal/orders",
            params={"userId": user_id},
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        orders = data.get("orders", [])
        total = data.get("total", 0)

        if total == 0:
            return "您当前没有任何停车订单。"

        lines = [f"您共有 {total} 条停车订单："]
        for o in orders:
            status_cn = {"parking": "停车中", "completed": "已结算", "cancelled": "已取消"}
            pay_cn = {"unpaid": "未支付", "paid": "已支付"}
            lines.append(
                f"- {o['orderId']} | {o['spotId']} | "
                f"{status_cn.get(o.get('status'), o.get('status'))} | "
                f"金额: ¥{o.get('totalAmount', 0)} | "
                f"{pay_cn.get(o.get('paymentStatus'), o.get('paymentStatus'))}"
            )
        return "\n".join(lines)
    except Exception as e:
        return f"查询订单失败: {e}"
