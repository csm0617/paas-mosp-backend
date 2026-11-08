ALTER TABLE `g_software`
    ADD COLUMN `order_top`  int(11) NULL DEFAULT 0 COMMENT '置顶等级' AFTER `active_expire`,
ADD COLUMN `order_time`  bigint(20) NULL DEFAULT 0 COMMENT '置顶等级相同，则按时间排' AFTER `order_top`;