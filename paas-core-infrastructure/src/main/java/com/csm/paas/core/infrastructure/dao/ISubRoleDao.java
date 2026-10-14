package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.SubRolePO;
import com.csm.paas.core.infrastructure.dao.po.SubRoleRolePO;
import com.csm.paas.core.infrastructure.dao.po.SubRoleUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 子角色表（K1）。`key` 是 MySQL 关键字，必须加反引号。 */
@Mapper
public interface ISubRoleDao {

    /**
     * 用户关联的子角色名。
     * <p>厂商出处：{@code SubRoleService.getSubRoleName(userId)}（{@code UserService.java:1855} 调用）。
     */
    @Select("""
            SELECT sr.name FROM g_user_sub_role usr
            JOIN g_sub_role sr ON sr.id = usr.sub_role_id
            WHERE usr.user_id = #{userId}
            """)
    List<String> selectNamesByUserId(@Param("userId") String userId);

    /** 子角色列表：按 created_at 倒序 + 分页（厂商 `SubRoleServiceImpl.java:190`）。 */
    @Select("""
            SELECT id, `key`, name, `desc`, updated_at, created_at
            FROM g_sub_role ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<SubRolePO> selectPageOrderByCreatedAtDesc(@Param("offset") int offset, @Param("size") int size);

    /** 某用户关联的子角色（厂商 `SubRoleService.listByUserId`）。 */
    @Select("""
            SELECT sr.id, sr.`key`, sr.name, sr.`desc`, sr.updated_at, sr.created_at
            FROM g_user_sub_role usr
            JOIN g_sub_role sr ON sr.id = usr.sub_role_id
            WHERE usr.user_id = #{userId}
            """)
    List<SubRolePO> selectByUserId(@Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM g_sub_role")
    long selectCount();

    /** 按主键取子角色（厂商 `SubRoleServiceImpl.detail:341-344`）。 */
    @Select("""
            SELECT id, `key`, name, `desc`, updated_at, created_at FROM g_sub_role WHERE id = #{id}
            """)
    SubRolePO selectById(@Param("id") String id);

    /** 删子角色（厂商 `deleteByIds:288` 的 `deleteByPrimaryKey`）。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM g_sub_role WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    /** 删"子角色-战位"关联（厂商 `deleteSubRoleInRoleBySubRoleId:454-456`）。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM g_sub_role_role WHERE sub_role_id = #{subRoleId}")
    int deleteSubRoleRolesBySubRoleId(@Param("subRoleId") String subRoleId);

    /** 删"用户-子角色"关联（厂商 `deleteByIds:289` 的 `userSubRoleMapper.delete`）。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM g_user_sub_role WHERE sub_role_id = #{subRoleId}")
    int deleteUserSubRolesBySubRoleId(@Param("subRoleId") String subRoleId);

    /**
     * 按名称模糊（厂商 `SubRoleServiceImpl.java:194`）。
     * <p>厂商该分支**不分页**（漏了 `startPage`），这里同样不加 LIMIT。
     */
    @Select("""
            SELECT id, `key`, name, `desc`, updated_at, created_at
            FROM g_sub_role WHERE name LIKE #{likeKeyword} ORDER BY created_at DESC
            """)
    List<SubRolePO> selectByNameLike(@Param("likeKeyword") String likeKeyword);

    /**
     * 按 `key` 取子角色（厂商在用户导入里逐个 `subRoleMapper.select(key = ?)` 再取 `get(0)`，
     * `UserService.java:1222,1291`）。`g_sub_role.key` 有唯一索引 ⇒ 至多一行。
     */
    @Select("SELECT id, `key`, name, `desc`, updated_at, created_at FROM g_sub_role WHERE `key` = #{key}")
    SubRolePO selectByKey(@Param("key") String key);

    /**
     * 按 id 批量取子角色（厂商 `getSubRoleListByRoleId` 的第二段查询）。
     */
    @Select("""
            <script>
            SELECT id, `key`, name, `desc`, updated_at, created_at FROM g_sub_role
            WHERE id IN <foreach item='item' collection='ids' open='(' separator=',' close=')'>#{item}</foreach>
            </script>
            """)
    List<SubRolePO> selectByIds(@Param("ids") List<String> ids);

    /**
     * 子角色关联的用户（厂商 `getRoleUser`：先查 `g_user_sub_role`，再按 id 查 `g_user`，
     * 用户不存在则跳过）。
     */
    @Select("""
            SELECT u.id AS user_id, u.username AS user_name
            FROM g_user_sub_role usr
            JOIN g_user u ON u.id = usr.user_id
            WHERE usr.sub_role_id = #{subRoleId}
            """)
    List<SubRoleUserPO> selectUsersBySubRoleId(@Param("subRoleId") String subRoleId);

    /**
     * 子角色关联的战位（厂商 `getRoleRole`：先查 `g_sub_role_role`，再按 id 查 `g_role`，
     * 战位不存在则跳过；`roleName` 过一层 i18n，本环境等价于库里的 name）。
     */
    @Select("""
            SELECT r.id AS role_id, r.`key` AS role_key, r.name AS role_name
            FROM g_sub_role_role srr
            JOIN g_role r ON r.id = srr.role_id
            WHERE srr.sub_role_id = #{subRoleId}
            """)
    List<SubRoleRolePO> selectRolesBySubRoleId(@Param("subRoleId") String subRoleId);
}
