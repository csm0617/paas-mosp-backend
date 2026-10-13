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

    @Select("SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role WHERE `key` = #{key}")
    RolePO selectByKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM g_role")
    long selectCount();

    @Select("SELECT id, role_id, app_id, app_name, url, updated_at, created_at FROM g_role_software WHERE role_id = #{roleId}")
    List<RoleSoftwarePO> selectSoftwareByRoleId(@Param("roleId") String roleId);

    /**
     * 用户名关联的战位/角色名，按 {@code g_role.type} 过滤、按 {@code g_role.created_at} 倒序。
     * <p>厂商出处：{@code RoleService.java:181}（三表 left join 的等价写法）。
     */
    @Select("""
            SELECT r.name FROM g_user_role ur
            JOIN g_user u ON u.id = ur.user_id
            JOIN g_role r ON r.id = ur.role_id
            WHERE u.username = #{username} AND r.type = #{type}
            ORDER BY r.created_at DESC
            """)
    List<String> selectRoleNamesByUsernameAndType(@Param("username") String username, @Param("type") int type);
}
