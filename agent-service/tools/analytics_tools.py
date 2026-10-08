"""
数据分析工具 — 调用 Java 后端统计接口，供 Analytics Agent 使用
"""
import httpx
from langchain_core.tools import tool
from config import config

JAVA_URL = config.JAVA_BACKEND_URL
INTERNAL_HEADERS = {"X-Internal-Key": config.INTERNAL_API_KEY}


@tool
def get_occupancy_stats() -> str:
    """查询停车场整体占用率统计，包括各状态车位数量和分类型占用率。"""
    try:
        resp = httpx.get(f"{JAVA_URL}/api/internal/stats/occupancy", headers=INTERNAL_HEADERS, timeout=10)
        data = resp.json()

        lines = [
            "=== 停车场占用率统计 ===",
            f"总车位: {data.get('totalSpots', '?')}",
            f"可用: {data.get('available', '?')} | "
            f"已预约: {data.get('reserved', '?')} | "
            f"已占用: {data.get('occupied', '?')} | "
            f"维护中: {data.get('maintenance', '?')}",
            f"整体占用率: {data.get('occupancyRate', '?')}%",
            "",
            "--- 按类型统计 ---",
        ]

        for t in data.get("byType", []):
            lines.append(
                f"  {t.get('type', '?')}: 总{t.get('total', '?')}个, "
                f"可用{t.get('available', '?')}个, "
                f"占用率{t.get('occupancyRate', '?')}%"
            )

        reservations = data.get("reservations", {})
        if reservations:
            lines.append("")
            lines.append("--- 预约统计 ---")
            for status, count in reservations.items():
                lines.append(f"  {status}: {count}")

        return "\n".join(lines)
    except Exception as e:
        return f"查询占用率失败: {e}"


@tool
def get_revenue_report(days: int = 7) -> str:
    """查询最近 N 天的收入统计报表。

    Args:
        days: 统计天数，默认7天
    """
    try:
        resp = httpx.get(
            f"{JAVA_URL}/api/internal/stats/revenue",
            params={"days": days},
            headers=INTERNAL_HEADERS,
            timeout=10,
        )
        data = resp.json()

        lines = [
            f"=== {data.get('period', f'最近{days}天')} 收入报表 ===",
            f"总收入: ¥{data.get('totalRevenue', 0)}",
            f"总预约数: {data.get('totalReservations', 0)}",
            "",
            "--- 每日明细 ---",
        ]

        for d in data.get("dailyBreakdown", []):
            lines.append(f"  {d.get('date', '?')}: {d.get('count', 0)}笔, ¥{d.get('revenue', 0)}")

        return "\n".join(lines)
    except Exception as e:
        return f"查询收入报表失败: {e}"


@tool
def predict_occupancy(hour: int) -> str:
    """预测指定小时的停车场占用率（基于历史快照的移动平均模型）。
    用户询问"X点去有位置吗"、"什么时间段车位紧张"时使用。

    Args:
        hour: 目标小时（0-23）。若已过当天该小时，自动预测明天同时段。
    """
    try:
        resp = httpx.get(
            f"{JAVA_URL}/api/internal/stats/predict",
            params={"hour": hour},
            headers=INTERNAL_HEADERS,
            timeout=30,
        )
        data = resp.json()
        if "error" in data:
            return f"预测失败: {data['error']}"

        return (
            f"=== 占用率预测 ===\n"
            f"- 目标时段: {data.get('targetDayOfWeek', '?')} {hour}:00 前后\n"
            f"- 预测占用率: {data.get('predictedRate', '?')}%\n"
            f"- 置信度: {round(data.get('confidence', 0) * 100)}% "
            f"(基于 {data.get('sampleCount', 0)} 个同组历史样本)\n"
            f"- 历史快照总量: {data.get('totalSnapshots', '?')} 条"
        )
    except Exception as e:
        return f"预测请求失败: {e}"
