
SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for g_mode
-- ----------------------------
DROP TABLE IF EXISTS `g_mode`;
CREATE TABLE `g_mode` (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
                          `type` int(32) DEFAULT NULL COMMENT '模式类型',
                          `mode` varchar(255) COMMENT '模式标识',
                          `mode_name` varchar(255) DEFAULT NULL COMMENT '模式名',
                          `used` int(10) DEFAULT NULL COMMENT '是否使用 0未使用 1使用',
                          `create_time` bigint(20) DEFAULT NULL COMMENT '创建时间',
                          PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COMMENT='模式列表';


-- ----------------------------
-- Records of g_mode
-- ----------------------------
INSERT INTO `g_mode` VALUES ('1', '1', '作战模式', 'FIGHT', '0', '0');
INSERT INTO `g_mode` VALUES ('2', '2', '训练模式', 'TRAIN', '1', '0');
INSERT INTO `g_mode` VALUES ('3', '3', '维护模式', 'MAINTAIN', '0', '0');
