CREATE TABLE `g_auth`
(
    `id` int(30) primary key  AUTO_INCREMENT COMMENT '主键id',
    `jar_name` varchar(50)  COMMENT '上传jar包名称',
    `store_jar_name` varchar(50)  COMMENT '上传jar包存储的名称',
    `class_name` varchar(50)  COMMENT '上传jar包注入时的类名称',
    `create_at` bigint(20)  COMMENT '上传时间',
    `update_at` bigint(20)  COMMENT '最后更新时间',
    `used` int(10)  COMMENT '是否使用当前jar包获取授权信息(0未使用 1使用 )',
    `public_key` varchar(500)  COMMENT '获取到的公钥字符串信息',
    `private_key` varchar(2000)  COMMENT '获取到的私钥字符串信息'
) COMMENT = '授权jar包管理';



