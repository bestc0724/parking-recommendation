"""
MCP Server — 将停车服务标准化暴露为 MCP Tools / Resources / Prompts
支持 stdio（本地 Claude Desktop 集成）和 SSE（远程部署）两种传输方式
"""
import sys
import asyncio
from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, Resource, Prompt

from tools import (
    recommend_parking,
    reserve_parking,
    cancel_reservation,
    confirm_arrival,
    query_available_spots,
)
from resources import (
    get_available_spots_resource,
    get_parking_rules_resource,
    get_user_reservations_resource,
)
from prompts import find_parking_prompt, check_reservations_prompt

# 创建 MCP Server 实例
server = Server("parking-assistant")


# ======================== Tools ========================

@server.list_tools()
async def list_tools() -> list[Tool]:
    return [
        Tool(
            name="recommend_parking",
            description="根据自然语言描述推荐停车位。用户可以描述限高、充电需求、价格偏好等条件。",
            inputSchema={
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "用户的停车需求描述，如'限高2米3，需要充电桩，离电梯近'",
                    },
                    "user_id": {
                        "type": "string",
                        "description": "用户ID（可选）",
                        "default": "",
                    },
                },
                "required": ["query"],
            },
        ),
        Tool(
            name="reserve_parking",
            description="预约指定车位。预约后1小时内有效，超时自动取消。",
            inputSchema={
                "type": "object",
                "properties": {
                    "user_id": {"type": "string", "description": "用户ID"},
                    "spot_id": {"type": "string", "description": "车位ID，如 S001"},
                },
                "required": ["user_id", "spot_id"],
            },
        ),
        Tool(
            name="cancel_reservation",
            description="取消已有的预约，车位将被释放。",
            inputSchema={
                "type": "object",
                "properties": {
                    "reservation_id": {"type": "string", "description": "预约号，如 R1A2B3C4"},
                },
                "required": ["reservation_id"],
            },
        ),
        Tool(
            name="confirm_arrival",
            description="确认用户已到达车位，将预约状态从 pending 变为 confirmed。",
            inputSchema={
                "type": "object",
                "properties": {
                    "reservation_id": {"type": "string", "description": "预约号"},
                },
                "required": ["reservation_id"],
            },
        ),
        Tool(
            name="query_available_spots",
            description="查询当前所有可用车位的数量和列表。不需要参数。",
            inputSchema={"type": "object", "properties": {}},
        ),
    ]


@server.call_tool()
async def call_tool(name: str, arguments: dict) -> list:
    """执行 Tool 调用"""
    handlers = {
        "recommend_parking": lambda: recommend_parking(
            arguments["query"], arguments.get("user_id", "")
        ),
        "reserve_parking": lambda: reserve_parking(
            arguments["user_id"], arguments["spot_id"]
        ),
        "cancel_reservation": lambda: cancel_reservation(arguments["reservation_id"]),
        "confirm_arrival": lambda: confirm_arrival(arguments["reservation_id"]),
        "query_available_spots": lambda: query_available_spots(),
    }

    handler = handlers.get(name)
    if not handler:
        return [{"type": "text", "text": f"未知工具: {name}"}]

    result = await handler()
    return [{"type": "text", "text": result}]


# ======================== Resources ========================

@server.list_resources()
async def list_resources() -> list[Resource]:
    return [
        Resource(
            uri="parking://spots/available",
            name="可用车位",
            description="当前所有可用车位列表",
            mimeType="text/plain",
        ),
        Resource(
            uri="parking://rules",
            name="停车场规则",
            description="停车场规章制度和收费标准",
            mimeType="text/plain",
        ),
    ]


@server.read_resource()
async def read_resource(uri: str) -> str:
    """读取 Resource 数据"""
    if uri == "parking://spots/available":
        return await get_available_spots_resource()
    if uri == "parking://rules":
        return await get_parking_rules_resource()
    if uri.startswith("parking://user/") and uri.endswith("/reservations"):
        user_id = uri.split("/")[2]
        return await get_user_reservations_resource(user_id)
    return f"未知资源: {uri}"


# ======================== Prompts ========================

@server.list_prompts()
async def list_prompts() -> list[Prompt]:
    return [
        Prompt(
            name="find-parking",
            description="引导用户描述停车需求并推荐车位",
        ),
        Prompt(
            name="check-my-reservations",
            description="查询用户的预约记录",
            arguments=[
                {"name": "user_id", "description": "用户ID", "required": True}
            ],
        ),
    ]


@server.get_prompt()
async def get_prompt(name: str, arguments: dict) -> str:
    """获取 Prompt 模板"""
    if name == "find-parking":
        return find_parking_prompt()
    if name == "check-my-reservations":
        user_id = arguments.get("user_id", "")
        return check_reservations_prompt(user_id)
    return f"未知 prompt: {name}"


# ======================== 启动 ========================

async def main():
    """以 stdio 模式启动 MCP Server（供 Claude Desktop 等本地客户端使用）"""
    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
