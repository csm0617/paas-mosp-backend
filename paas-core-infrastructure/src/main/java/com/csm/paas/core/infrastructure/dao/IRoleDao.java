package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.RolePO;
import com.csm.paas.core.infrastructure.dao.po.RoleSoftwarePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IRoleDao {

    /** 厂商：全量按 created_at 倒序（RoleService.java:366）。`key` 是 MySQL 关键字，必须加反引号。 */
    @Select("SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role ORDER BY created_at DESC")
    List<RolePO> selectOrderByCreatedAtDesc();

    @Select("SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role WHERE `key` = #{key}")
    RolePO selectByKey(@Param("key") String key);

    @Select("SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role WHERE id = #{id}")
    RolePO selectById(@Param("id") String id);

    @Select("""
            SELECT id, `key`, name, `desc`, type, updated_at, created_at
            FROM g_role ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<RolePO> selectPage(@Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT id, `key`, name, `desc`, type, updated_at, created_at
            FROM g_role WHERE type = #{type} ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<RolePO> selectPageByType(@Param("type") int type, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM g_role WHERE type = #{type}")
    long selectCountByType(@Param("type") int type);

    @Select("""
            SELECT id, `key`, name, `desc`, type, updated_at, created_at
            FROM g_role WHERE type = #{type} AND name LIKE #{likeKeyword} ORDER BY created_at DESC
            """)
    List<RolePO> selectByTypeAndNameLike(@Param("type") int type, @Param("likeKeyword") String likeKeyword);

    @Select("""
            SELECT id, `key`, name, `desc`, type, updated_at, created_at
            FROM g_role WHERE name LIKE #{likeKeyword} ORDER BY created_at DESC
            """)
    List<RolePO> selectByNameLike(@Param("likeKeyword") String likeKeyword);

    /**
     * 某用户关联的战位（厂商 `RoleService.java:369` 的三表 join；该分支不分页）。
     */
    @Select("""
            SELECT r.id, r.`key`, r.name, r.`desc`, r.type, r.updated_at, r.created_at
            FROM g_role r
            JOIN g_user_role ur ON ur.role_id = r.id
            JOIN g_user u ON u.id = ur.user_id
            WHERE u.username = #{username}
            ORDER BY r.created_at DESC
            """)
    List<RolePO> selectByUsername(@Param("username") String username);

    /**
     * 战位关联用户。厂商原文：{@code RoleMapper.getRoleUser}——
     * {@code select ur.user_id as userId, u.username as userName ... LEFT JOIN g_user ... ORDER BY ur.created_at desc}
     * （**LEFT JOIN**：用户被删时仍返回该行、`userName` 为 null）。
     */
    @Select("""
            SELECT ur.user_id AS user_id, u.username AS user_name
            FROM g_user_role ur
            LEFT JOIN g_user u ON u.id = ur.user_id
            WHERE ur.role_id = #{roleId}
            ORDER BY ur.created_at DESC
            """)
    List<com.csm.paas.core.infrastructure.dao.po.RoleUserPO> selectUsersByRoleId(@Param("roleId") String roleId);

    /** 战位关联的子角色 id（厂商 `getSubRoleListByRoleId` 的第一段查询）。 */
    @Select("SELECT sub_role_id FROM g_sub_role_role WHERE role_id = #{roleId}")
    List<String> selectSubRoleIdsByRoleId(@Param("roleId") String roleId);

    @Select("""
            SELECT r.id, r.`key`, r.name, r.`desc`, r.type, r.updated_at, r.created_at
            FROM g_role r
            JOIN g_user_role ur ON ur.role_id = r.id
            JOIN g_user u ON u.id = ur.user_id
            WHERE u.username = #{username} AND r.type = #{type}
            ORDER BY r.created_at DESC
            """)
    List<RolePO> selectByUsernameAndType(@Param("username") String username, @Param("type") int type);

    /**
     * 按 key 取战位（id + key），供调用方按入参顺序重排。
     * <p>**必须用 PO 而不是 Map**：H2 返回的列标签是大写（`ID`/`KEY`），Map 的键会跟着变大写，
     * 而 MySQL 是小写——用 PO 让 MyBatis 做大小写不敏感的属性映射，两种库都能跑。
     */
    @Select("""
            <script>
            SELECT id, `key` FROM g_role
            WHERE `key` IN <foreach item='item' collection='keys' open='(' separator=',' close=')'>#{item}</foreach>
            </script>
            """)
    List<RolePO> selectIdAndKeyByKeys(@Param("keys") List<String> keys);

    @Select("SELECT COUNT(*) FROM g_role")
    long selectCount();

    /** 过滤出真实存在的战位/角色 id（厂商 `rolesRelate2User` 里逐个 `selectByPrimaryKey` 的等价写法）。 */
    @Select("""
            <script>
            SELECT id FROM g_role
            WHERE id IN <foreach item='item' collection='ids' open='(' separator=',' close=')'>#{item}</foreach>
            </script>
            """)
    List<String> selectExistingIds(@Param("ids") List<String> ids);

    // ---------- 删除（厂商 RoleService.deleteRoleRelation 的 6 张表，顺序见 Case 层）----------

    @Delete("DELETE FROM g_sub_role_role WHERE role_id = #{roleId}")
    int deleteSubRoleRoleByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM g_user_audit WHERE role_id = #{roleId}")
    int deleteUserAuditByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM g_user_role WHERE role_id = #{roleId}")
    int deleteUserRoleByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM g_user_login WHERE role_id = #{roleId}")
    int deleteUserLoginByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM g_role_software WHERE role_id = #{roleId}")
    int deleteRoleSoftwareByRoleId(@Param("roleId") String roleId);

    @Delete("DELETE FROM g_role WHERE id = #{roleId}")
    int deleteById(@Param("roleId") String roleId);

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

    // ---------- 写（新增 / 修改）----------

    /** key **或** name 命中数（厂商 `existKeyName` 是 **OR**）。 */
    @Select("SELECT COUNT(*) FROM g_role WHERE `key` = #{key} OR name = #{name}")
    long countByKeyOrName(@Param("key") String key, @Param("name") String name);

    @Select("SELECT COUNT(*) FROM g_role WHERE `key` = #{key} AND id <> #{id}")
    long countByKeyExcludingId(@Param("key") String key, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM g_role WHERE name = #{name} AND id <> #{id}")
    long countByNameExcludingId(@Param("name") String name, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM g_role WHERE name = #{name}")
    long countByName(@Param("name") String name);

    @Insert("""
            INSERT INTO g_role (id, `key`, name, `desc`, type, created_at, updated_at)
            VALUES (#{id}, #{key}, #{name}, #{desc}, #{type}, #{createdAt}, #{updatedAt})
            """)
    int insert(RolePO role);

    @Update("""
            UPDATE g_role SET `key` = #{key}, name = #{name}, `desc` = #{desc}, type = #{type},
                              created_at = #{createdAt}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(RolePO role);

    @Insert("""
            INSERT INTO g_user_role (user_id, role_id, created_at) VALUES (#{userId}, #{roleId}, #{createdAt})
            """)
    int insertUserRole(@Param("userId") String userId, @Param("roleId") String roleId,
                       @Param("createdAt") long createdAt);

    @Insert("""
            INSERT INTO g_sub_role_role (sub_role_id, role_id, created_at)
            VALUES (#{subRoleId}, #{roleId}, #{createdAt})
            """)
    int insertSubRoleRole(@Param("subRoleId") String subRoleId, @Param("roleId") String roleId,
                          @Param("createdAt") long createdAt);
}
