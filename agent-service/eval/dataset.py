"""
RAG 检索评估数据集
每条: 问题 + 期望命中的文档分类（规则/FAQ/区域）
问题覆盖 docs/ 下三份知识文档，用于计算 hit-rate@k
"""

DATASET = [
    # ── 规则类（parking_rules.txt）──
    {"question": "停车场内限速多少公里", "expected_category": "规则"},
    {"question": "预约车位多久不确认会被自动取消", "expected_category": "规则"},
    {"question": "预约没到场会不会收费", "expected_category": "规则"},
    {"question": "无障碍车位谁能使用", "expected_category": "规则"},
    {"question": "遇到紧急情况打什么电话", "expected_category": "规则"},
    {"question": "停车场里可以洗车吗", "expected_category": "规则"},
    {"question": "超高车辆进场有什么后果", "expected_category": "规则"},
    {"question": "车里的贵重物品丢了停车场负责吗", "expected_category": "规则"},

    # ── FAQ 类（faq.txt）──
    {"question": "柴油车能停B区吗", "expected_category": "FAQ"},
    {"question": "我的车高2.3米能停哪个区", "expected_category": "FAQ"},
    {"question": "新能源车必须停B区吗", "expected_category": "FAQ"},
    {"question": "充电桩怎么收费", "expected_category": "FAQ"},
    {"question": "预约了车位但是来不了怎么办", "expected_category": "FAQ"},
    {"question": "月卡去哪里办理", "expected_category": "FAQ"},
    {"question": "车被刮了怎么处理", "expected_category": "FAQ"},
    {"question": "停车场提供代客泊车吗", "expected_category": "FAQ"},

    # ── 区域类（spot_features.txt）──
    {"question": "B区有多少个充电桩", "expected_category": "区域"},
    {"question": "A区距离电梯要走多久", "expected_category": "区域"},
    {"question": "C区的车位宽度是多少", "expected_category": "区域"},
    {"question": "哪个区域最适合SUV停", "expected_category": "区域"},
    {"question": "各区域的基础价格分别是多少", "expected_category": "区域"},
]
