alter table g_ci_cd
    add image_storage  varchar(100) null comment '镜像仓库组名称';


update g_ci_cd cd
set cd.image_storage  = 'gaop'
where cd.image_storage is NULL;