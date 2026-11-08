#删除所有外键约束
alter table g_user_role drop foreign key g_user_role_ibfk_1;
alter table g_user_role drop foreign key g_user_role_ibfk_2;

alter table g_user_audit drop foreign key g_user_audit_ibfk_1;
alter table g_user_audit drop foreign key g_user_audit_ibfk_2;

alter table g_role_software drop foreign key g_role_software_ibfk_1;





#修改所有表以及字段的排序规则
ALTER TABLE g_user CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_user_role CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_user_audit CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_role_software CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_service_port CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_database CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_database_record CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_role CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_setting CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_software CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_software_app CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_upload_store_record CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';
ALTER TABLE g_user_login CONVERT TO CHARACTER SET utf8mb4 COLLATE 'utf8mb4_bin';

#添加外键约束
ALTER TABLE g_user_role ADD CONSTRAINT g_user_role_ibfk_1 FOREIGN KEY(user_id) REFERENCES g_user (id);
ALTER TABLE g_user_role ADD CONSTRAINT g_user_role_ibfk_2 FOREIGN KEY(role_id) REFERENCES g_role (id);


ALTER TABLE g_user_audit ADD CONSTRAINT g_user_audit_ibfk_1 FOREIGN KEY (user_id) references g_user (id);
ALTER TABLE g_user_audit ADD CONSTRAINT g_user_audit_ibfk_2 FOREIGN KEY (role_id) references g_role (id);


ALTER TABLE g_role_software ADD CONSTRAINT g_role_software_ibfk_1  FOREIGN KEY (role_id) references g_role (id);

