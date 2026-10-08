"""
AI 停车助手 — Agent 服务入口
FastAPI + LangGraph 多 Agent 架构

端点:
- POST /agent/chat        一次性返回（兼容旧前端）
- POST /agent/chat/stream SSE 流式返回（推荐，前端默认使用）
- GET  /agent/health      健康检查
- GET  /agent/stats       占用率统计（代理 Java 内部 API，内部密钥不出服务）
- DELETE /agent/session/{id} 清除会话
"""
import json
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from config import config
from graph.builder import compile_graph
from memory.session import session_manager


# ==================== 全局变量 ====================

app_graph = None

# 图中节点的名称（用于过滤事件）
NODE_NAMES = ("router", "recommend", "service", "analytics", "chitchat")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时初始化 LangGraph 和 RAG 知识库"""
    global app_graph

    # 初始化 LangGraph
    app_graph = compile_graph()
    print("[Agent] LangGraph 多 Agent 图已编译")

    # 初始化 RAG 知识库（如果 Qdrant 可用）
    try:
        from rag.retriever import init_knowledge_base
        init_knowledge_base()
        print("[Agent] RAG 知识库初始化完成")
    except Exception as e:
        print(f"[Agent] RAG 初始化跳过（Qdrant 未启动）: {e}")

    yield
    print("[Agent] 服务关闭")


# ==================== FastAPI 应用 ====================

app = FastAPI(
    title="AI 停车助手 Agent",
    description="基于 LangGraph 多 Agent 架构的智能停车推荐系统",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ==================== 请求/响应模型 ====================

class ChatRequest(BaseModel):
    session_id: str
    user_id: str = ""
    message: str


class ChatResponse(BaseModel):
    session_id: str
    reply: str
    intent: str
    intent_confidence: float


# ==================== 工具函数 ====================

def get_langfuse_handler():
    """
    Langfuse 可观测性（可选）。
    三项配置齐全时返回 CallbackHandler，记录每次 LLM 调用的
    trace、耗时和 token 用量；未配置时返回 None（默认关闭）。
    """
    if config.LANGFUSE_PUBLIC_KEY and config.LANGFUSE_SECRET_KEY and config.LANGFUSE_HOST:
        try:
            from langfuse.callback import CallbackHandler
            return CallbackHandler(
                public_key=config.LANGFUSE_PUBLIC_KEY,
                secret_key=config.LANGFUSE_SECRET_KEY,
                host=config.LANGFUSE_HOST,
            )
        except ImportError:
            print("[Agent] langfuse 未安装，跳过可观测性集成")
            return None
    return None


def build_initial_state(request: ChatRequest) -> tuple[dict, object]:
    """
    构建 LangGraph 输入状态。

    关键：将 session_manager 中存储的完整对话历史传入图，
    这样 Router 和专业 Agent 才能利用多轮上下文（否则每个
    Agent 节点里的 chat_history 永远是空的）。
    """
    history = session_manager.get_history(request.session_id)

    # 历史消息（BaseMessage 列表）+ 当前用户消息
    current_message = HumanMessage(content=request.message)
    graph_messages = list(history.messages) + [current_message]

    # 用户消息立即写入持久历史（图执行失败也不丢失）
    history.add_user_message(request.message)

    initial_state = {
        "messages": graph_messages,
        "user_id": request.user_id,
        "session_id": request.session_id,
        "intent": "",
        "intent_confidence": 0.0,
        "intent_reason": "",
        "final_reply": "",
    }
    return initial_state, history


def sse(payload: dict) -> str:
    """将 dict 序列化为 SSE data 行"""
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


# ==================== API 端点 ====================

@app.post("/agent/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """一次性返回版本（兼容）。前端推荐使用 /agent/chat/stream。"""
    initial_state, history = build_initial_state(request)

    langfuse_handler = get_langfuse_handler()
    run_config = {"callbacks": [langfuse_handler]} if langfuse_handler else {}

    try:
        result = await app_graph.ainvoke(initial_state, config=run_config)
    except Exception as e:
        error_reply = f"抱歉，处理您的请求时出现了问题: {e}"
        history.add_ai_message(error_reply)
        return ChatResponse(
            session_id=request.session_id,
            reply=error_reply,
            intent="error",
            intent_confidence=0.0,
        )

    reply = result.get("final_reply", "抱歉，我暂时无法回复。")
    history.add_ai_message(reply)

    return ChatResponse(
        session_id=request.session_id,
        reply=reply,
        intent=result.get("intent", "unknown"),
        intent_confidence=result.get("intent_confidence", 0.0),
    )


@app.post("/agent/chat/stream")
async def chat_stream(request: ChatRequest):
    """
    SSE 流式对话接口。
    事件类型:
    - node_start / node_end   进入/离开某个 Agent 节点
    - tool_start / tool_end   Agent 调用工具（工具名 + 参数）
    - token                   LLM 生成中的文字片段（跳过 router 的意图分类输出）
    - done                    最终回复（完整 reply + intent）
    - error                   执行出错
    """
    initial_state, history = build_initial_state(request)

    langfuse_handler = get_langfuse_handler()
    run_config = {"callbacks": [langfuse_handler]} if langfuse_handler else {}

    async def event_generator():
        current_node = ""
        try:
            async for event in app_graph.astream_events(initial_state, config=run_config, version="v2"):
                kind = event["event"]
                name = event.get("name", "")

                # 节点进入/离开
                if kind == "on_chain_start" and name in NODE_NAMES:
                    current_node = name
                    yield sse({"type": "node_start", "node": name})
                elif kind == "on_chain_end" and name in NODE_NAMES:
                    current_node = ""
                    yield sse({"type": "node_end", "node": name})

                # 工具调用
                elif kind == "on_tool_start":
                    tool_input = event["data"].get("input", {})
                    yield sse({
                        "type": "tool_start",
                        "tool": name,
                        "input": str(tool_input)[:300],
                    })
                elif kind == "on_tool_end":
                    yield sse({"type": "tool_end", "tool": name})

                # LLM 流式 token（router 的意图分类 JSON 不透传）
                elif kind == "on_chat_model_stream" and current_node != "router":
                    chunk = event["data"].get("chunk")
                    content = chunk.content if chunk else ""
                    if content:
                        yield sse({"type": "token", "content": content})

                # 图执行完成，输出最终状态
                elif kind == "on_chain_end" and name == "LangGraph":
                    output = event["data"].get("output", {})
                    if isinstance(output, dict) and "final_reply" in output:
                        reply = output.get("final_reply", "")
                        intent = output.get("intent", "unknown")
                        confidence = output.get("intent_confidence", 0.0)
                        history.add_ai_message(reply)
                        yield sse({
                            "type": "done",
                            "reply": reply,
                            "intent": intent,
                            "intent_confidence": confidence,
                        })
        except Exception as e:
            error_reply = f"抱歉，处理您的请求时出现了问题: {e}"
            history.add_ai_message(error_reply)
            yield sse({"type": "error", "message": error_reply})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.get("/agent/health")
async def health():
    """健康检查"""
    return {
        "status": "ok",
        "active_sessions": session_manager.active_count(),
    }


@app.get("/agent/stats")
async def agent_stats():
    """
    占用率统计 — 代理 Java 内部 API。
    内部密钥只存在于服务端，不暴露给浏览器。
    """
    try:
        resp = httpx.get(
            f"{config.JAVA_BACKEND_URL}/api/internal/stats/occupancy",
            headers={"X-Internal-Key": config.INTERNAL_API_KEY},
            timeout=10,
        )
        return resp.json()
    except Exception as e:
        return {"error": str(e)}


@app.delete("/agent/session/{session_id}")
async def clear_session(session_id: str):
    """清除指定会话"""
    cleared = session_manager.clear(session_id)
    return {"session_id": session_id, "cleared": cleared}


# ==================== 启动 ====================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=config.AGENT_PORT, reload=True)
