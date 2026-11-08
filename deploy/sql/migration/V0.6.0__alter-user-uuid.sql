alter table g_user
    add uuid int(10) null comment 'uuid唯一标识';

set
@rownum=0;
update g_user
SET uuid = (
    select @rownum := @rownum +1 as nid);