-- AI 停车推荐系统 - 数据库建表脚本
-- Database: parking_recommendation

CREATE DATABASE IF NOT EXISTS parking_recommendation DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE parking_recommendation;

-- 车位表
CREATE TABLE IF NOT EXISTS `parking_spots` (
    `spot_id` VARCHAR(32) NOT NULL COMMENT '车位编号（主键）',
    `height_limit` DECIMAL(4,2) NOT NULL COMMENT '限高（米）',
    `has_charging` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有充电桩 (0:否, 1:是)',
    `spot_type` VARCHAR(20) NOT NULL DEFAULT 'standard' COMMENT '车位类型 (standard/large/accessible)',
    `distance_to_elevator` INT NOT NULL DEFAULT 999 COMMENT '距电梯距离（米）',
    `price_per_hour` DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '每小时价格（元）',
    `status` ENUM('available','occupied','maintenance','reserved') NOT NULL DEFAULT 'available' COMMENT '车位状态',
    `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`spot_id`),
    KEY `idx_status` (`status`),
    KEY `idx_height_charging` (`height_limit`, `has_charging`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场车位信息表';

-- 用户表
CREATE TABLE IF NOT EXISTS `users` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID（主键）',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    `password` VARCHAR(100) DEFAULT NULL COMMENT '密码',
    `role` VARCHAR(20) DEFAULT 'driver' COMMENT '角色 (driver/admin)',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 预约记录表
CREATE TABLE IF NOT EXISTS `reservations` (
    `reservation_id` VARCHAR(32) NOT NULL COMMENT '预约ID（主键）',
    `spot_id` VARCHAR(32) NOT NULL COMMENT '车位ID（外键）',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID（外键）',
    `status` ENUM('pending','confirmed','expired','cancelled') NOT NULL DEFAULT 'pending' COMMENT '预约状态',
    `reserved_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约创建时间',
    `expires_at` TIMESTAMP NOT NULL COMMENT '锁定过期时间（1小时后）',
    PRIMARY KEY (`reservation_id`),
    KEY `idx_spot_status` (`spot_id`, `status`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_expires` (`expires_at`),
    CONSTRAINT `fk_reservation_spot` FOREIGN KEY (`spot_id`) REFERENCES `parking_spots` (`spot_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约记录表';

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

-- 测试数据：用户
INSERT INTO `users` (`user_id`, `username`, `password`, `role`) VALUES
('U001', 'u001', '123456', 'driver'),
('U002', 'u002', '123456', 'driver'),
('U003', 'u003', '123456', 'driver'),
('UADMIN01', 'admin', 'admin123', 'admin');

-- 测试数据：车辆档案（U001 的电动车，用于无感推荐演示）
INSERT INTO `vehicles` (`vehicle_id`, `user_id`, `plate_number`, `model`, `vehicle_height`, `is_ev`) VALUES
('V001', 'U001', '京A12345', '特斯拉 Model 3', 1.44, 1),
('V002', 'U002', '沪B88888', '丰田汉兰达', 1.72, 0);

-- 测试数据：车位（100条）
INSERT INTO `parking_spots` (`spot_id`, `height_limit`, `has_charging`, `spot_type`, `distance_to_elevator`, `price_per_hour`, `status`, `version`) VALUES
('S001', 2.50, 1, 'standard', 10, 10.00, 'available', 0),
('S002', 2.00, 0, 'standard', 50, 5.00, 'available', 0),
('S003', 3.00, 1, 'large', 20, 15.00, 'available', 0),
('S004', 2.20, 0, 'standard', 5, 20.00, 'available', 0),
('S005', 2.80, 1, 'accessible', 15, 18.00, 'available', 0),
('S006', 2.40, 0, 'standard', 30, 8.00, 'available', 0),
('S007', 2.20, 0, 'standard', 25, 6.00, 'available', 0),
('S008', 2.50, 1, 'standard', 8, 12.00, 'available', 0),
('S009', 2.00, 0, 'standard', 40, 4.00, 'available', 0),
('S010', 3.00, 1, 'large', 18, 14.00, 'available', 0),
('S011', 2.10, 0, 'standard', 35, 5.50, 'available', 0),
('S012', 2.40, 1, 'standard', 12, 11.00, 'available', 0),
('S013', 2.80, 0, 'large', 22, 7.00, 'available', 0),
('S014', 2.30, 1, 'standard', 6, 13.00, 'available', 0),
('S015', 2.00, 0, 'standard', 45, 3.50, 'available', 0),
('S016', 2.60, 1, 'large', 10, 15.00, 'available', 0),
('S017', 2.10, 0, 'standard', 30, 5.00, 'available', 0),
('S018', 2.50, 1, 'standard', 14, 10.50, 'available', 0),
('S019', 2.20, 0, 'standard', 28, 6.50, 'available', 0),
('S020', 3.00, 1, 'large', 16, 16.00, 'available', 0),
('S021', 2.00, 0, 'standard', 50, 3.00, 'available', 0),
('S022', 2.40, 1, 'standard', 9, 12.50, 'available', 0),
('S023', 2.70, 0, 'large', 20, 8.00, 'available', 0),
('S024', 2.10, 1, 'standard', 7, 11.50, 'available', 0),
('S025', 2.30, 0, 'standard', 32, 5.00, 'available', 0),
('S026', 2.50, 1, 'standard', 11, 13.50, 'available', 0),
('S027', 2.00, 0, 'standard', 38, 4.50, 'available', 0),
('S028', 2.80, 1, 'large', 15, 14.50, 'available', 0),
('S029', 2.20, 0, 'standard', 26, 6.00, 'available', 0),
('S030', 2.60, 1, 'standard', 13, 10.00, 'available', 0),
('S031', 2.10, 0, 'standard', 33, 5.50, 'available', 0),
('S032', 2.40, 1, 'standard', 8, 12.00, 'available', 0),
('S033', 3.00, 0, 'large', 24, 7.50, 'available', 0),
('S034', 2.30, 1, 'standard', 5, 14.00, 'available', 0),
('S035', 2.00, 0, 'standard', 42, 3.50, 'available', 0),
('S036', 2.50, 1, 'large', 17, 15.50, 'available', 0),
('S037', 2.10, 0, 'standard', 29, 5.00, 'available', 0),
('S038', 2.70, 1, 'standard', 10, 11.00, 'available', 0),
('S039', 2.20, 0, 'standard', 36, 6.50, 'available', 0),
('S040', 2.40, 1, 'standard', 7, 13.00, 'available', 0),
('S041', 2.00, 0, 'standard', 48, 3.00, 'available', 0),
('S042', 2.60, 1, 'large', 19, 16.50, 'available', 0),
('S043', 2.30, 0, 'standard', 27, 6.00, 'available', 0),
('S044', 2.50, 1, 'standard', 12, 10.50, 'available', 0),
('S045', 2.10, 0, 'standard', 34, 5.50, 'available', 0),
('S046', 2.80, 1, 'standard', 9, 12.50, 'available', 0),
('S047', 2.00, 0, 'large', 21, 8.00, 'available', 0),
('S048', 2.40, 1, 'standard', 6, 14.00, 'available', 0),
('S049', 2.20, 0, 'standard', 31, 5.00, 'available', 0),
('S050', 2.60, 1, 'standard', 14, 11.50, 'available', 0),
('S051', 2.10, 0, 'standard', 37, 4.50, 'available', 0),
('S052', 2.50, 1, 'large', 16, 15.00, 'available', 0),
('S053', 2.30, 0, 'standard', 23, 6.00, 'available', 0),
('S054', 2.70, 1, 'standard', 11, 10.00, 'available', 0),
('S055', 2.00, 0, 'standard', 44, 3.50, 'available', 0),
('S056', 2.40, 1, 'standard', 8, 13.50, 'available', 0),
('S057', 2.80, 0, 'large', 25, 7.00, 'available', 0),
('S058', 2.20, 1, 'standard', 13, 12.00, 'available', 0),
('S059', 2.10, 0, 'standard', 35, 5.50, 'available', 0),
('S060', 2.50, 1, 'standard', 7, 14.50, 'available', 0),
('S061', 2.00, 0, 'standard', 46, 3.00, 'available', 0),
('S062', 2.60, 1, 'large', 18, 16.00, 'available', 0),
('S063', 2.30, 0, 'standard', 28, 6.50, 'available', 0),
('S064', 2.40, 1, 'standard', 10, 11.00, 'available', 0),
('S065', 2.10, 0, 'standard', 39, 4.50, 'available', 0),
('S066', 2.70, 1, 'standard', 6, 13.00, 'available', 0),
('S067', 2.20, 0, 'large', 20, 8.00, 'available', 0),
('S068', 2.50, 1, 'standard', 15, 10.50, 'available', 0),
('S069', 2.00, 0, 'standard', 41, 3.50, 'available', 0),
('S070', 2.80, 1, 'standard', 9, 12.50, 'available', 0),
('S071', 2.30, 0, 'standard', 30, 5.00, 'available', 0),
('S072', 2.60, 1, 'large', 17, 15.50, 'available', 0),
('S073', 2.10, 0, 'standard', 33, 6.00, 'available', 0),
('S074', 2.40, 1, 'standard', 11, 11.50, 'available', 0),
('S075', 2.20, 0, 'standard', 36, 5.50, 'available', 0),
('S076', 2.50, 1, 'standard', 8, 14.00, 'available', 0),
('S077', 2.00, 0, 'large', 22, 7.50, 'available', 0),
('S078', 2.70, 1, 'standard', 12, 10.00, 'available', 0),
('S079', 2.30, 0, 'standard', 27, 6.50, 'available', 0),
('S080', 2.40, 1, 'standard', 7, 13.50, 'available', 0),
('S081', 2.10, 0, 'standard', 43, 3.00, 'available', 0),
('S082', 2.60, 1, 'large', 19, 16.50, 'available', 0),
('S083', 2.20, 0, 'standard', 29, 6.00, 'available', 0),
('S084', 2.50, 1, 'standard', 14, 11.00, 'available', 0),
('S085', 2.00, 0, 'standard', 47, 3.50, 'available', 0),
('S086', 2.80, 1, 'standard', 10, 12.00, 'available', 0),
('S087', 2.30, 0, 'large', 24, 8.00, 'available', 0),
('S088', 2.40, 1, 'standard', 6, 14.50, 'available', 0),
('S089', 2.10, 0, 'standard', 32, 5.00, 'available', 0),
('S090', 2.60, 1, 'standard', 13, 10.50, 'available', 0),
('S091', 2.20, 0, 'standard', 38, 4.50, 'available', 0),
('S092', 2.50, 1, 'large', 16, 15.00, 'available', 0),
('S093', 2.00, 0, 'standard', 40, 3.00, 'available', 0),
('S094', 2.70, 1, 'standard', 9, 13.00, 'available', 0),
('S095', 2.30, 0, 'standard', 26, 6.00, 'available', 0),
('S096', 2.40, 1, 'standard', 11, 12.50, 'available', 0),
('S097', 2.10, 0, 'large', 21, 7.00, 'available', 0),
('S098', 2.50, 1, 'standard', 15, 11.50, 'available', 0),
('S099', 2.20, 0, 'standard', 34, 5.50, 'available', 0),
('S100', 2.80, 1, 'standard', 8, 14.00, 'available', 0);
