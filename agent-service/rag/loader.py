"""
文档加载器 — 读取 docs/ 下的知识文档并切分为 chunks
"""
import os
from pathlib import Path
from langchain.text_splitter import RecursiveCharacterTextSplitter


def load_documents(docs_dir: str = None) -> list[dict]:
    """
    加载 docs/ 目录下的所有 .txt 文件，切分为 chunks。
    每个 chunk 附带元数据：source（文件名）、category（规则/FAQ/区域）。

    Returns:
        [{"content": "...", "metadata": {"source": "parking_rules.txt", "category": "规则"}}, ...]
    """
    if docs_dir is None:
        # 默认找项目根目录下的 docs/
        docs_dir = Path(__file__).parent.parent.parent / "docs"

    docs_path = Path(docs_dir)
    if not docs_path.exists():
        return []

    # 文件名 → 分类映射
    category_map = {
        "parking_rules": "规则",
        "faq": "FAQ",
        "spot_features": "区域",
    }

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=500,
        chunk_overlap=50,
        separators=["\n\n", "\n", "。", ".", "；", ";", " "],
    )

    chunks = []
    for txt_file in sorted(docs_path.glob("*.txt")):
        content = txt_file.read_text(encoding="utf-8")
        if not content.strip():
            continue

        stem = txt_file.stem
        category = category_map.get(stem, "其他")

        splits = splitter.split_text(content)
        for i, text in enumerate(splits):
            chunks.append({
                "content": text,
                "metadata": {
                    "source": txt_file.name,
                    "category": category,
                    "chunk_index": i,
                },
            })

    return chunks
