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

    /** 删单条"用户-战位"关联（厂商 `UserService.deleteUserInRole:1391` 的 `delete`）。 */
    @Delete("DELETE FROM g_user_role WHERE role_id = #{roleId} AND user_id = #{userId}")
    int deleteUserRoleByRoleIdAndUserId(@Param("roleId") String roleId, @Param("userId") String userId);

    /** 删单条"战位-软件"关联（厂商 `RoleService.roleRemoveApp:733` 的 `delete`）。 */
    @Delete("DELETE FROM g_role_software WHERE role_id = #{roleId} AND app_id = #{appId}")
    int deleteRoleSoftwareByRoleIdAndAppId(@Param("roleId") String roleId, @Param("appId") String appId);

    /**
     * 战位下的软件（厂商 `RoleService.roleSoftwareList:418`：`where role_id = ? order by \`order\``）。
     * <p>**按 `order` 升序**——这一点在库里没有数据时看不出来，容易漏。
     */
    @Select("SELECT id, role_id, app_id, app_name, url, updated_at, created_at FROM g_role_software "
            + "WHERE role_id = #{roleId} ORDER BY `order`")
    List<RoleSoftwarePO> selectSoftwareByRoleId(@Param("roleId") String roleId);

    @Select("SELECT id, role_id, app_id, app_name, url, updated_at, created_at FROM g_role_software "
            + "WHERE role_id = #{roleId} ORDER BY `order` LIMIT #{size} OFFSET #{offset}")
    List<RoleSoftwarePO> selectSoftwarePage(@Param("roleId") String roleId,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM g_role_software WHERE role_id = #{roleId}")
    long countSoftwareByRoleId(@Param("roleId") String roleId);

    /**
     * 战位关联软件的 **`key`** 列表（厂商 `generateExportExcel:805-812` 的
     * `softwareMapper.select(leftJoin g_role_software ... where role_id = ?)` 再 `.map(Software::getKey)`）。
     * <p>**没有 `ORDER BY`**——与厂商一致（顺序由 MySQL 决定）。
     */
    @Select("""
            SELECT s.`key`
            FROM g_software s
            LEFT JOIN g_role_software rs ON rs.app_id = s.id
            WHERE rs.role_id = #{roleId}
            """)
    List<String> selectSoftwareKeysByRoleId(@Param("roleId") String roleId);

    /**
     * 按 id 集合取战位，`created_at` 倒序；**空集合 ⇒ 全量**（厂商 `isInWhenPresent` 的等价语义）。
     * <p>厂商出处：`RoleService.generateExportExcel:786`。
     */
    @Select("""
            <script>
            SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role
            <if test="ids != null and ids.size() > 0">
              WHERE id IN <foreach collection="ids" item="it" open="(" separator="," close=")">#{it}</foreach>
            </if>
            ORDER BY created_at DESC
            </script>
            """)
    List<RolePO> selectByIdsOrderByCreatedAtDesc(@Param("ids") List<String> ids);

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

    /**
     * key **或** name 命中的第一条战位（厂商 `importRoleExcel:878-881` 的
     * `select(key = ? OR name = ?)` 后 `roles.get(0)`）。
     */
    @Select("""
            SELECT id, `key`, name, `desc`, type, updated_at, created_at FROM g_role
            WHERE `key` = #{key} OR name = #{name} LIMIT 1
            """)
    RolePO selectByKeyOrName(@Param("key") String key, @Param("name") String name);

    /**
     * 插一条"战位-软件"关联（厂商 `importRoleExcel:976-984` 的 `insertSelective`：
     * 只写 role_id/app_id/`order`/created_at，`app_name`/`url`/`updated_at` 留空）。
     */
    @Insert("""
            INSERT INTO g_role_software (role_id, app_id, `order`, created_at)
            VALUES (#{roleId}, #{appId}, #{order}, #{createdAt})
            """)
    int insertRoleSoftware(@Param("roleId") String roleId, @Param("appId") String appId,
                           @Param("order") int order, @Param("createdAt") long createdAt);

    /**
     * 只改 `updated_at`（厂商 `SubRoleServiceImpl.subRoleRelateRoles:415-418`：
     * `role.setUpdatedAt(now); roleMapper.updateByPrimaryKey(role)`，实体其余列是原值 ⇒ 净效果等价）。
     */
    @Update("UPDATE g_role SET updated_at = #{updatedAt} WHERE id = #{id}")
    int touchUpdatedAt(@Param("id") String id, @Param("updatedAt") long updatedAt);
}
