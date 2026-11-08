--
-- Table structure for table `g_alert_strategy`
--

DROP TABLE IF EXISTS `g_alert_strategy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `g_alert_strategy`
(
    `id`            varchar(255) NOT NULL COMMENT 'id',
    `strategy_name` varchar(255) NOT NULL COMMENT '策略名称',
    `target_name`   varchar(255) DEFAULT NULL COMMENT '告警对象',
    `namespace`     varchar(255) DEFAULT NULL COMMENT '命名空间',
    `interval`      varchar(255) DEFAULT NULL COMMENT '告警周期',
    `alert_type`    int(11) DEFAULT NULL COMMENT '告警类型：0-资源，1-日志',
    `enable`        int(11) DEFAULT NULL COMMENT '是否启用',
    `create_time`   datetime(3) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) COMMENT='告警策略';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `g_alert_rule`
--

DROP TABLE IF EXISTS `g_alert_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `g_alert_rule`
(
    `id`                varchar(255) NOT NULL COMMENT '规则id',
    `alert_strategy_id` varchar(255) DEFAULT NULL COMMENT '所属策略id',
    `alert_name`        varchar(255) DEFAULT NULL COMMENT 'alert',
    `metric`            varchar(255) DEFAULT NULL COMMENT '指标或关键词',
    `operator`          varchar(255) DEFAULT NULL COMMENT '比较关系',
    `value`             varchar(255) DEFAULT NULL COMMENT '告警阈值',
    PRIMARY KEY (`id`)
) COMMENT='告警策略附属规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `g_alert_record`
--

DROP TABLE IF EXISTS `g_alert_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `g_alert_record`
(
    `id`            varchar(255) NOT NULL COMMENT '记录Id',
    `alert_rule_id` varchar(255) NOT NULL COMMENT '所属规则id',
    `status`        varchar(255)  DEFAULT NULL COMMENT '告警状态',
    `filename`      varchar(1024) DEFAULT NULL COMMENT '日志路径',
    `node_name`     varchar(255)  DEFAULT NULL COMMENT '告警节点',
    `pod`           varchar(255)  DEFAULT NULL COMMENT '告警Pod',
    `current_value` varchar(255)  DEFAULT NULL COMMENT '告警值',
    `fingerprint`   varchar(255) NOT NULL COMMENT '告警记录指纹',
    `repeat_count`  int(11) DEFAULT '0' COMMENT '重复告警次数',
    `alert_time`    timestamp NULL DEFAULT NULL COMMENT '告警时间',
    `resolve_time`  timestamp NULL DEFAULT NULL COMMENT '告警解除时间',
    PRIMARY KEY (`id`)
)  COMMENT='告警策略触发记录';



ALTER TABLE g_database MODIFY sql_name mediumtext comment '存储在服务器上的sql文件名称';