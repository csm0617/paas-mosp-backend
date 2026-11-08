CREATE TABLE `g_ci_cd`
(
    `id`                 int(50) primary key auto_increment COMMENT '主键id',
    `user_id`            varchar(36) COMMENT '创建者用户id',
    `image_name`         varchar(50) COMMENT '构建镜像名称',
    `image_tag`          varchar(50) COMMENT '构建镜像名称',
    `dockerfile_id`      varchar(50) COMMENT '上传dockerfile文件id',
    `dockerfile_content` varchar(2000) COMMENT 'dockerfile内容',
    `buildfile_id`       varchar(50) COMMENT '上传构建压缩包文件id',
    `job_name`           varchar(50) COMMENT '构建job的名称',
    `job_status`         int(10) COMMENT '构建job的状态(0失败1成功3失败)',
    `description`           varchar(255) COMMENT '描述',
    `create_at`          bigint(20) COMMENT '创建时间',
    `update_at`          bigint(20) COMMENT '最后更新时间'
) COMMENT = 'cicd构建信息表';

