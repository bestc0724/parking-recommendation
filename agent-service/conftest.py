"""
pytest 根配置 — 将 agent-service 目录加入 sys.path，
使 tests/ 可以直接 import graph/tools/memory 等模块
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
