"""
RAG 检索质量评估
用法: python -m eval.evaluate
要求: Qdrant 可用且知识库已初始化（首次运行 main.py 会自动初始化）

指标:
- hit-rate@3: Top 3 检索结果中命中期望文档分类的问题比例
- avg_top1_score: Top 1 结果的平均相似度
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from rag.store import VectorStore
from eval.dataset import DATASET


def main():
    print("=" * 60)
    print("RAG 检索质量评估")
    print(f"数据集: {len(DATASET)} 条问题 | top_k=3")
    print("=" * 60)

    store = VectorStore()
    hits = 0
    top1_scores = []

    for i, item in enumerate(DATASET, 1):
        question = item["question"]
        expected = item["expected_category"]

        results = store.search(question, top_k=3)
        categories = [r["category"] for r in results]
        hit = expected in categories

        if hit:
            hits += 1
        if results:
            top1_scores.append(results[0]["score"])

        mark = "✓" if hit else "✗"
        print(f"[{mark}] {i:2d}. {question}")
        print(f"      期望: {expected} | 实际: {categories}")
        if results:
            print(f"      Top1: {results[0]['source']} (score={results[0]['score']})")
        print()

    hit_rate = hits / len(DATASET) if DATASET else 0
    avg_score = sum(top1_scores) / len(top1_scores) if top1_scores else 0

    print("=" * 60)
    print(f"hit-rate@3 : {hits}/{len(DATASET)} = {hit_rate:.0%}")
    print(f"avg Top1 score: {avg_score:.3f}")
    print("=" * 60)


if __name__ == "__main__":
    main()
