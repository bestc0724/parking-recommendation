-- ============================================================
-- V2 迁移脚本：停车订单 + 车辆档案 + 占用率快照
-- 用于已有数据库的增量升级（全新部署直接用 schema.sql，无需本文件）
-- 执行方式: mysql -u parking -p parking_recommendation < migration_v2.sql
-- ============================================================

-- 停车订单表（业务闭环：parking → completed → paid）
CREATE TABLE IF NOT EXISTS `orders` (
    `order_id` VARCHAR(32) NOT NULL COMMENT '订单号（主键）',
    `reservation_id` VARCHAR(32) NOT NULL COMMENT '关联预约ID',
    `spot_id` VARCHAR(32) NOT NULL COMMENT '车位ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `status` ENUM('parking','completed','cancelled') NOT NULL DEFAULT 'parking' COMMENT '订单状态',
    `entered_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入场时间',
    `exited_at` TIMESTAMP NULL COMMENT '离场时间',
    `parking_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '停车费',
    `charging_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '充电费',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    `payment_status` ENUM('unpaid','paid') NOT NULL DEFAULT 'unpaid' COMMENT '支付状态',
    `paid_at` TIMESTAMP NULL COMMENT '支付时间',
    PRIMARY KEY (`order_id`),
    KEY `idx_order_reservation` (`reservation_id`),
    KEY `idx_order_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车订单表';

-- 车辆档案表（支撑无感推荐）
CREATE TABLE IF NOT EXISTS `vehicles` (
    `vehicle_id` VARCHAR(32) NOT NULL COMMENT '车辆ID（主键）',
    `user_id` VARCHAR(32) NOT NULL COMMENT '所属用户ID',
    `plate_number` VARCHAR(20) NOT NULL COMMENT '车牌号',
    `model` VARCHAR(50) DEFAULT NULL COMMENT '车型',
    `vehicle_height` DECIMAL(4,2) DEFAULT NULL COMMENT '车辆高度（米）',
    `is_ev` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否新能源车',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`vehicle_id`),
    KEY `idx_vehicle_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆档案表';

-- 占用率快照表（需求预测训练数据）
CREATE TABLE IF NOT EXISTS `occupancy_snapshots` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `snapshot_time` TIMESTAMP NOT NULL COMMENT '快照时间',
    `available_count` INT NOT NULL COMMENT '可用车位数',
    `total_count` INT NOT NULL COMMENT '总车位数',
    `occupancy_rate` DECIMAL(5,2) NOT NULL COMMENT '占用率（百分比）',
    PRIMARY KEY (`id`),
    KEY `idx_snapshot_time` (`snapshot_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='占用率快照表';

-- 种子数据：车辆档案
INSERT INTO `vehicles` (`vehicle_id`, `user_id`, `plate_number`, `model`, `vehicle_height`, `is_ev`) VALUES
('V001', 'U001', '京A12345', '特斯拉 Model 3', 1.44, 1),
('V002', 'U002', '沪B88888', '丰田汉兰达', 1.72, 0)
ON DUPLICATE KEY UPDATE plate_number = VALUES(plate_number);

-- 注意：occupancy_snapshots 无需种子数据，服务首次调用预测接口时
-- 会自动生成 14 天合成历史数据（冷启动策略）
