-- ============================================================
-- OA 低代码系统 数据库初始化脚本（含测试数据）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `oa_lowcode`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `oa_lowcode`;

-- ============================================================
-- 1. 部门表（测试数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_department` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `parent_id`   BIGINT       DEFAULT 0,
    `dept_name`   VARCHAR(64)  NOT NULL,
    `dept_code`   VARCHAR(32)  NOT NULL UNIQUE,
    `leader_id`   BIGINT,
    `sort_order`  INT          DEFAULT 0,
    `status`      TINYINT      DEFAULT 1,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO `sys_department` (`id`, `parent_id`, `dept_name`, `dept_code`, `leader_id`, `sort_order`) VALUES
(1, 0, '总公司',     'HQ',      3, 1),
(2, 1, '研发部',     'RND',     2, 2),
(3, 1, '产品部',     'PROD',    2, 3),
(4, 1, '行政人事部', 'HR_ADMIN', 3, 4)
ON DUPLICATE KEY UPDATE `dept_name`=VALUES(`dept_name`);

-- ============================================================
-- 2. 用户表（测试数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `username`    VARCHAR(64)  NOT NULL UNIQUE,
    `password`    VARCHAR(128) NOT NULL,
    `real_name`   VARCHAR(32)  NOT NULL,
    `email`       VARCHAR(64),
    `phone`       VARCHAR(16),
    `dept_id`     BIGINT,
    `avatar`      VARCHAR(255),
    `status`      TINYINT      DEFAULT 1,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `dept_id`) VALUES
(1, 'zhangsan', '123456', '张三', 2),
(2, 'wangwu',   '123456', '王五', 2),
(3, 'zhaoliu',  '123456', '赵六', 1),
(4, 'lisi',     '123456', '李四', 3),
(5, 'hr_xu',    '123456', '许人事', 4)
ON DUPLICATE KEY UPDATE `real_name`=VALUES(`real_name`);

-- ============================================================
-- 3. 角色表 & 用户-角色关联（测试数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `role_name`   VARCHAR(32)  NOT NULL,
    `role_code`   VARCHAR(32)  NOT NULL UNIQUE,
    `description` VARCHAR(255),
    `status`      TINYINT      DEFAULT 1,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`) VALUES
(1, '普通员工',   'ROLE_EMPLOYEE', '普通员工'),
(2, '部门负责人', 'ROLE_MANAGER',  '部门负责人'),
(3, '系统管理员', 'ROLE_ADMIN',    '系统管理员，兼任HR')
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`);

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`          BIGINT   PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL,
    `role_id`     BIGINT   NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 1), (2, 2),
(3, 1), (3, 2), (3, 3),
(4, 1),
(5, 1), (5, 3);

-- ============================================================
-- 4. 表单 Schema（演示数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `form_schema` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL,
    `code`        VARCHAR(50)  NOT NULL,
    `version`     INT          DEFAULT 1,
    `schema_json` JSON         NOT NULL,
    `status`      TINYINT      DEFAULT 1 COMMENT '1=草稿 2=已发布 3=已停用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code_version` (`code`, `version`),
    INDEX `idx_code` (`code`)
);
INSERT INTO `form_schema` (`id`, `name`, `code`, `version`, `schema_json`, `status`) VALUES
(1, '请假申请表', 'leave_apply', 1, '{
  "labelWidth": 100,
  "fields": [
    {"key":"leave_type","type":"select","label":"请假类型","placeholder":"请选择","rules":[{"required":true,"message":"请选择请假类型"}],"options":[{"label":"年假","value":"annual"},{"label":"事假","value":"personal"},{"label":"病假","value":"sick"},{"label":"婚假","value":"marriage"},{"label":"调休","value":"compensatory"}]},
    {"key":"start_date","type":"date","label":"开始日期","rules":[{"required":true,"message":"请选择开始日期"}]},
    {"key":"end_date","type":"date","label":"结束日期","rules":[{"required":true,"message":"请选择结束日期"}]},
    {"key":"days","type":"number","label":"请假天数","rules":[{"required":true,"message":"请输入天数"},{"min":0.5,"message":"最少0.5天"}]},
    {"key":"reason","type":"textarea","label":"请假原因","placeholder":"请输入请假原因","rules":[{"required":true,"message":"请输入原因"},{"maxLength":500,"message":"原因不能超过500字"}]}
  ]
}', 2)
ON DUPLICATE KEY UPDATE `schema_json`=VALUES(`schema_json`);

-- ============================================================
-- 5. 审批节点库（预置数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `approval_node` (
    `id`           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `node_code`    VARCHAR(50)  NOT NULL UNIQUE,
    `node_name`    VARCHAR(100) NOT NULL,
    `handler_type` VARCHAR(200) NOT NULL,
    `description`  VARCHAR(500),
    `sort_order`   INT          DEFAULT 0,
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO `approval_node` (`node_code`, `node_name`, `handler_type`, `description`, `sort_order`) VALUES
('DIRECT_LEADER', '直属主管', 'com.oa.lowcode.handler.DirectLeaderHandler', '申请人所在部门的负责人', 1),
('DEPT_MANAGER',  '部门经理', 'com.oa.lowcode.handler.DeptManagerHandler',  '上级部门的负责人',   2),
('HR',            '人事',     'com.oa.lowcode.handler.HrHandler',           'HR最终确认、归档',   3),
('GM',            '总经理',   'com.oa.lowcode.handler.GmHandler',           '公司总经理终审',     4)
ON DUPLICATE KEY UPDATE `node_name`=VALUES(`node_name`);

-- ============================================================
-- 6. 流程 Schema（演示数据）—— 与 leave_apply 关联
-- ============================================================
CREATE TABLE IF NOT EXISTS `flow_schema` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL,
    `code`        VARCHAR(50)  NOT NULL,
    `version`     INT          DEFAULT 1,
    `schema_json` JSON         NOT NULL,
    `status`      TINYINT      DEFAULT 1 COMMENT '1=草稿 2=已发布 3=已停用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code_version` (`code`, `version`),
    INDEX `idx_code` (`code`)
);
INSERT INTO `flow_schema` (`id`, `name`, `code`, `version`, `schema_json`, `status`) VALUES
(1, '请假审批流程', 'leave_apply', 1, '{
  "nodes": [
    {"nodeId":"n1","nodeCode":"DIRECT_LEADER","nodeName":"直属主管","order":1,"conditions":[]},
    {"nodeId":"n2","nodeCode":"DEPT_MANAGER","nodeName":"部门经理","order":2,"conditions":[
      {"field":"days","operator":">","value":3,"action":"REQUIRE"}
    ]},
    {"nodeId":"n3","nodeCode":"HR","nodeName":"人事","order":3,"conditions":[]}
  ]
}', 2)
ON DUPLICATE KEY UPDATE `schema_json`=VALUES(`schema_json`);

-- ============================================================
-- 7. 考勤规则 Schema（演示数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `attendance_schema` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL,
    `is_current`  TINYINT      DEFAULT 0 COMMENT '1=当前生效',
    `schema_json` JSON         NOT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO `attendance_schema` (`id`, `name`, `is_current`, `schema_json`) VALUES
(1, '2025 标准考勤规则', 1, '{
  "baseRule": {
    "workStart": "09:00",
    "workEnd": "18:00",
    "flexMinutes": 15,
    "lateThreshold": 30,
    "earlyThreshold": 30
  },
  "specialDays": [
    {"date":"2025-01-01","type":"HOLIDAY","desc":"元旦"},
    {"date":"2025-01-28","type":"WORKDAY","desc":"春节调休"},
    {"date":"2025-01-29","type":"HOLIDAY","desc":"春节"},
    {"date":"2025-01-30","type":"HOLIDAY","desc":"春节"},
    {"date":"2025-01-31","type":"HOLIDAY","desc":"春节"},
    {"date":"2025-02-01","type":"HOLIDAY","desc":"春节"},
    {"date":"2025-04-04","type":"HOLIDAY","desc":"清明节"},
    {"date":"2025-05-01","type":"HOLIDAY","desc":"劳动节"},
    {"date":"2025-05-02","type":"HOLIDAY","desc":"劳动节"},
    {"date":"2025-05-05","type":"WORKDAY","desc":"劳动节调休"},
    {"date":"2025-06-02","type":"HOLIDAY","desc":"端午节"},
    {"date":"2025-09-15","type":"HOLIDAY","desc":"中秋节"},
    {"date":"2025-10-01","type":"HOLIDAY","desc":"国庆节"},
    {"date":"2025-10-02","type":"HOLIDAY","desc":"国庆节"},
    {"date":"2025-10-03","type":"HOLIDAY","desc":"国庆节"},
    {"date":"2025-10-06","type":"WORKDAY","desc":"国庆调休"}
  ],
  "syncConfig": {
    "apiUrl": "https://timor.tech/api/holiday/year",
    "lastSyncTime": null,
    "autoSync": false
  }
}')
ON DUPLICATE KEY UPDATE `schema_json`=VALUES(`schema_json`);

-- ============================================================
-- 8. 请假业务实例（测试数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `leave_instance` (
    `id`             BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `form_schema_id` BIGINT      NOT NULL,
    `applicant_id`   BIGINT      NOT NULL,
    `applicant_name` VARCHAR(50),
    `form_data`      JSON        NOT NULL,
    `status`         VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    `create_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_applicant` (`applicant_id`),
    INDEX `idx_status` (`status`)
);
INSERT INTO `leave_instance` (`id`, `form_schema_id`, `applicant_id`, `applicant_name`, `form_data`, `status`, `create_time`) VALUES
(1, 1, 1, '张三', '{"leave_type":"annual","start_date":"2025-03-10","end_date":"2025-03-12","days":3,"reason":"个人旅游"}', 'APPROVED', '2025-03-08 09:00:00'),
(2, 1, 1, '张三', '{"leave_type":"sick","start_date":"2025-03-20","end_date":"2025-03-20","days":0.5,"reason":"身体不适去医院"}', 'APPROVED', '2025-03-19 14:00:00'),
(3, 1, 1, '张三', '{"leave_type":"personal","start_date":"2025-04-15","end_date":"2025-04-17","days":3,"reason":"家里有事"}', 'PENDING', '2025-04-14 10:00:00'),
(4, 1, 4, '李四', '{"leave_type":"annual","start_date":"2025-03-15","end_date":"2025-03-21","days":5,"reason":"长途旅行"}', 'APPROVED', '2025-03-10 08:00:00')
ON DUPLICATE KEY UPDATE `form_data`=VALUES(`form_data`);

-- ============================================================
-- 9. 流程实例 & 审批记录（测试数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `process_instance` (
    `id`              BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `flow_schema_id`  BIGINT      NOT NULL,
    `business_id`     BIGINT      NOT NULL,
    `business_type`   VARCHAR(50) DEFAULT 'LEAVE',
    `current_node_id` VARCHAR(50),
    `status`          VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/APPROVED/REJECTED',
    `snapshot_json`   JSON,
    `create_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `finish_time`     DATETIME,
    INDEX `idx_business` (`business_id`, `business_type`)
);
INSERT INTO `process_instance` (`id`, `flow_schema_id`, `business_id`, `current_node_id`, `status`, `snapshot_json`, `create_time`, `finish_time`) VALUES
(1, 1, 1, NULL, 'APPROVED', '{"resolvedNodes":[{"nodeId":"n1","nodeCode":"DIRECT_LEADER","nodeName":"直属主管","order":1,"required":true,"conditions":[]},{"nodeId":"n2","nodeCode":"DEPT_MANAGER","nodeName":"部门经理","order":2,"required":false,"skipReason":"条件不满足: days(3) > 3 = false","conditions":[{"field":"days","operator":">","value":3,"action":"REQUIRE"}]},{"nodeId":"n3","nodeCode":"HR","nodeName":"人事","order":3,"required":true,"conditions":[]}]}', '2025-03-08 09:00:00', '2025-03-09 16:00:00'),
(2, 1, 2, NULL, 'APPROVED', '{"resolvedNodes":[{"nodeId":"n1","nodeCode":"DIRECT_LEADER","nodeName":"直属主管","order":1,"required":true,"conditions":[]},{"nodeId":"n2","nodeCode":"DEPT_MANAGER","nodeName":"部门经理","order":2,"required":false,"skipReason":"条件不满足: days(0.5) > 3 = false","conditions":[{"field":"days","operator":">","value":3,"action":"REQUIRE"}]},{"nodeId":"n3","nodeCode":"HR","nodeName":"人事","order":3,"required":true,"conditions":[]}]}', '2025-03-19 14:00:00', '2025-03-20 10:00:00'),
(3, 1, 3, 'DIRECT_LEADER', 'IN_PROGRESS', '{"resolvedNodes":[{"nodeId":"n1","nodeCode":"DIRECT_LEADER","nodeName":"直属主管","order":1,"required":true,"conditions":[]},{"nodeId":"n2","nodeCode":"DEPT_MANAGER","nodeName":"部门经理","order":2,"required":false,"skipReason":"条件不满足: days(3) > 3 = false","conditions":[{"field":"days","operator":">","value":3,"action":"REQUIRE"}]},{"nodeId":"n3","nodeCode":"HR","nodeName":"人事","order":3,"required":true,"conditions":[]}]}', '2025-04-14 10:00:00', NULL),
(4, 1, 4, NULL, 'APPROVED', '{"resolvedNodes":[{"nodeId":"n1","nodeCode":"DIRECT_LEADER","nodeName":"直属主管","order":1,"required":true,"conditions":[]},{"nodeId":"n2","nodeCode":"DEPT_MANAGER","nodeName":"部门经理","order":2,"required":true,"conditions":[{"field":"days","operator":">","value":3,"action":"REQUIRE"}]},{"nodeId":"n3","nodeCode":"HR","nodeName":"人事","order":3,"required":true,"conditions":[]}]}', '2025-03-10 08:00:00', '2025-03-12 15:00:00')
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`);

CREATE TABLE IF NOT EXISTS `approval_record` (
    `id`            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `process_id`    BIGINT       NOT NULL,
    `node_id`       VARCHAR(50)  NOT NULL,
    `node_name`     VARCHAR(100),
    `approver_id`   BIGINT,
    `approver_name` VARCHAR(50),
    `action`        VARCHAR(20)  COMMENT 'APPROVE/REJECT/SKIP/PENDING',
    `comment`       VARCHAR(1000),
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `handle_time`   DATETIME,
    INDEX `idx_process` (`process_id`)
);
INSERT INTO `approval_record` (`id`, `process_id`, `node_id`, `node_name`, `approver_id`, `approver_name`, `action`, `comment`, `create_time`, `handle_time`) VALUES
-- 张三的3天年假（直属主管+HR，部门经理条件不满足跳过）
(1, 1, 'n1', '直属主管', 2, '王五（直属主管）', 'APPROVE', '同意', '2025-03-08 09:00:00', '2025-03-08 10:00:00'),
(2, 1, 'n3', '人事',       3, '赵六（人事）',     'APPROVE', '已备案', '2025-03-09 10:00:00', '2025-03-09 16:00:00'),
-- 张三的半天病假（直属主管+HR）
(3, 2, 'n1', '直属主管', 2, '王五（直属主管）', 'APPROVE', '注意身体', '2025-03-19 14:00:00', '2025-03-19 15:00:00'),
(4, 2, 'n3', '人事',       3, '赵六（人事）',     'APPROVE', '已备案', '2025-03-20 10:00:00', '2025-03-20 10:00:00'),
-- 张三的3天事假（待审批）
(5, 3, 'n1', '直属主管', 2, '王五（直属主管）', 'PENDING', NULL, '2025-04-14 10:00:00', NULL),
-- 李四的5天年假（直属主管+部门经理+HR，全部走完）
(6, 4, 'n1', '直属主管', 2, '王五（直属主管）', 'APPROVE', '同意', '2025-03-10 08:00:00', '2025-03-10 10:00:00'),
(7, 4, 'n2', '部门经理', 3, '赵六（部门经理）', 'APPROVE', '同意', '2025-03-11 10:00:00', '2025-03-11 14:00:00'),
(8, 4, 'n3', '人事',       3, '赵六（人事）',     'APPROVE', '已归档', '2025-03-12 10:00:00', '2025-03-12 15:00:00')
ON DUPLICATE KEY UPDATE `action`=VALUES(`action`);

-- ============================================================
-- 10. 考勤打卡记录（测试数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS `attendance_record` (
    `id`              BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `user_id`         BIGINT      NOT NULL,
    `user_name`       VARCHAR(32) NOT NULL,
    `dept_id`         BIGINT      NOT NULL,
    `record_date`     DATE        NOT NULL,
    `sign_in_time`    DATETIME,
    `sign_out_time`   DATETIME,
    `status`          VARCHAR(16) DEFAULT 'NORMAL',
    `create_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_date` (`user_id`, `record_date`)
);
INSERT INTO `attendance_record` (`user_id`, `user_name`, `dept_id`, `record_date`, `sign_in_time`, `sign_out_time`, `status`) VALUES
-- 张三 3月 考勤
(1, '张三', 2, '2025-03-03', '2025-03-03 08:55:00', '2025-03-03 18:05:00', 'NORMAL'),
(1, '张三', 2, '2025-03-04', '2025-03-04 09:10:00', '2025-03-04 18:00:00', 'NORMAL'),
(1, '张三', 2, '2025-03-05', '2025-03-05 09:20:00', '2025-03-05 17:55:00', 'LATE'),
(1, '张三', 2, '2025-03-06', '2025-03-06 09:35:00', '2025-03-06 18:10:00', 'SERIOUS_LATE'),
(1, '张三', 2, '2025-03-07', '2025-03-07 08:58:00', '2025-03-07 17:30:00', 'EARLY'),
-- 请假期间（3/10-3/12），这些日期由请假优先级覆盖显示为"请假"
(1, '张三', 2, '2025-03-10', '2025-03-10 09:00:00', NULL, 'NORMAL'),
(1, '张三', 2, '2025-03-11', NULL, NULL, 'NORMAL'),
(1, '张三', 2, '2025-03-12', NULL, '2025-03-12 18:00:00', 'NORMAL'),
(1, '张三', 2, '2025-03-13', '2025-03-13 08:50:00', '2025-03-13 18:02:00', 'NORMAL'),
(1, '张三', 2, '2025-03-14', '2025-03-14 09:01:00', NULL, 'NORMAL'),
(1, '张三', 2, '2025-03-17', '2025-03-17 08:45:00', '2025-03-17 18:00:00', 'NORMAL'),
(1, '张三', 2, '2025-03-18', '2025-03-18 09:00:00', '2025-03-18 18:00:00', 'NORMAL'),
-- 李四 部分考勤
(4, '李四', 3, '2025-03-03', '2025-03-03 09:15:00', '2025-03-03 18:00:00', 'NORMAL'),
(4, '李四', 3, '2025-03-04', '2025-03-04 09:40:00', '2025-03-04 18:05:00', 'SERIOUS_LATE'),
(4, '李四', 3, '2025-03-05', NULL, NULL, 'MISSING')
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`);

-- ============================================================
-- 完成
-- ============================================================
SELECT '数据库初始化完成！已插入测试数据。' AS message;
