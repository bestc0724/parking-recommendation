"""
推荐相关工具 — 调用 Java 后端 /api/internal/recommend 和 /spots
"""
import httpx
from langchain_core.tools import tool
from config import config

JAVA_URL = config.JAVA_BACKEND_URL
INTERNAL_HEADERS = {"X-Internal-Key": config.INTERNAL_API_KEY}


@tool
def recommend_spot(query: str, user_id: str = "", vehicle_id: str = "") -> str:
    """根据用户的自然语言描述推荐停车位。
    用户可以描述限高要求、是否需要充电桩、价格偏好、距电梯距离等条件。
    返回最匹配的 Top 3 车位及推荐理由。

    Args:
        query: 用户的停车需求描述，如"限高2米3，需要充电桩，离电梯近"；也可只说"帮我停车"
        user_id: 用户ID（可选）
        vehicle_id: 车辆ID（可选）。用户未说明限高/充电需求时，
                    系统自动从车辆档案补全（限高→硬条件，新能源→充电软偏好）
    """
    try:
        body = {"query": query, "userId": user_id}
        if vehicle_id:
            body["vehicleId"] = vehicle_id
        resp = httpx.post(
            f"{JAVA_URL}/api/internal/recommend",
            json=body,
            headers=INTERNAL_HEADERS,
            timeout=30,
        )
        data = resp.json()

        status = data.get("status")
        if status == "success":
            recs = data.get("recommendations", [])
            lines = [f"找到 {len(recs)} 个推荐车位（共 {data.get('totalAvailable', '?')} 个可用车位）："]
            for i, r in enumerate(recs, 1):
                charging = "有充电桩" if r.get("hasCharging") else "无充电桩"
                lines.append(
                    f"{i}. {r['spotId']} | 限高{r.get('heightLimit', '?')}米 | "
                    f"{charging} | 距电梯{r.get('distanceToElevator', '?')}米 | "
                    f"{r.get('pricePerHour', '?')}元/小时 | "
                    f"评分: {r.get('score', '?')}"
                )
                if r.get("reason"):
                    lines.append(f"   理由: {r['reason']}")
            return "\n".join(lines)

        if status == "no_match":
            parsed = data.get("parsed", {})
            hard = parsed.get("hard_conditions", [])
            cond_desc = ", ".join(f"{c.get('type')}: {c.get('value')}" for c in hard) if hard else "无"
            return f"没有找到符合条件的车位。您当前的硬性条件: {cond_desc}。建议放宽部分条件重试。"

        if status == "needs_confirmation":
            return f"需求理解不确定: {data.get('message', '')}。解析结果: {data.get('parsed', {})}"

        if status == "no_available":
            return "当前没有可用车位，请稍后再试。"

        return f"推荐请求返回: {data}"
    except Exception as e:
        return f"推荐请求失败: {e}"


@tool
def query_available_spots() -> str:
    """查询当前所有可用车位的数量和列表。
    不需要任何参数，直接返回可用车位信息。"""
    try:
        resp = httpx.get(f"{JAVA_URL}/api/internal/spots/available", headers=INTERNAL_HEADERS, timeout=10)
        data = resp.json()
        spots = data.get("spots", [])
        total = data.get("total", 0)

        if total == 0:
            return "当前没有可用车位。"

        lines = [f"当前共有 {total} 个可用车位："]
        for s in spots[:10]:  # 最多显示10个
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
    except Exception as e:
        return f"查询可用车位失败: {e}"
