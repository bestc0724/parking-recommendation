"""
车辆档案工具 — 支撑"无感推荐"（只说"帮我停车"即可自动关联车辆信息）
对应 Java /api/internal/vehicles
"""
import httpx
from langchain_core.tools import tool
from config import config

JAVA_URL = config.JAVA_BACKEND_URL
INTERNAL_HEADERS = {"X-Internal-Key": config.INTERNAL_API_KEY}


@tool
def add_vehicle(user_id: str, plate_number: str, model: str = "",
                vehicle_height: float = 0, is_ev: bool = False) -> str:
    """为用户添加车辆档案。用户首次使用或提供车辆信息时使用。
    车辆高度用于自动补全限高要求，新能源标识用于自动偏好充电车位。

    Args:
        user_id: 用户ID
        plate_number: 车牌号，如 "京A12345"
        model: 车型（可选），如 "特斯拉 Model 3"
        vehicle_height: 车辆高度（米），如 1.44
        is_ev: 是否新能源车（True/False）
    """
    try:
        resp = httpx.post(
            f"{JAVA_URL}/api/internal/vehicles",
            json={
                "userId": user_id,
                "plateNumber": plate_number,
                "model": model,
                "vehicleHeight": vehicle_height if vehicle_height > 0 else None,
                "isEv": str(is_ev).lower(),
            },
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        if data.get("status") == "added":
            return (
                f"车辆档案添加成功！\n"
                f"- 车辆ID: {data.get('vehicleId')}\n"
                f"- 车牌号: {data.get('plateNumber')}\n"
                f"以后您只需说'帮我停车'，我会自动按您的车辆信息推荐车位。"
            )
        return f"添加失败: {data.get('error', data)}"
    except Exception as e:
        return f"添加车辆失败: {e}"


@tool
def query_my_vehicles(user_id: str) -> str:
    """查询用户的车辆档案列表。用户说"帮我停车"但未指明车辆时，
    先调用此工具获取其车辆信息（高度、是否新能源），再据此推荐。

    Args:
        user_id: 用户ID
    """
    try:
        resp = httpx.get(
            f"{JAVA_URL}/api/internal/vehicles",
            params={"userId": user_id},
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()
        vehicles = data.get("vehicles", [])
        total = data.get("total", 0)

        if total == 0:
            return "该用户尚未添加车辆档案。"

        lines = [f"该用户有 {total} 辆车："]
        for v in vehicles:
            lines.append(
                f"- {v['vehicleId']} | {v.get('plateNumber', '?')} | "
                f"{v.get('model', '未知车型')} | 车高: {v.get('vehicleHeight', '?')}米 | "
                f"{'新能源' if v.get('isEv') else '燃油车'}"
            )
        return "\n".join(lines)
    except Exception as e:
        return f"查询车辆失败: {e}"
