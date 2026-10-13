package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.RolePO;
import com.csm.paas.core.infrastructure.dao.po.RoleSoftwarePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IRoleDao {

    /** 厂商：全量按 created_at 倒序（RoleService.java:366）。`key` 是 MySQL 关键字，必须加反引号。 */
    @Select("SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role ORDER BY created_at DESC")
    List<RolePO> selectOrderByCreatedAtDesc();

    @Select("SELECT COUNT(*) FROM g_role")
    long selectCount();

    @Select("SELECT id, role_id, app_id, app_name, url, updated_at, created_at FROM g_role_software WHERE role_id = #{roleId}")
    List<RoleSoftwarePO> selectSoftwareByRoleId(@Param("roleId") String roleId);
}
