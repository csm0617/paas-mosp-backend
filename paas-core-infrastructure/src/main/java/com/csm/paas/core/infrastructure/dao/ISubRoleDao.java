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

    /**
     * 导出用：按 id 集合取子角色（**ids 为空 ⇒ 不加 where，即全量**），
     * 按 `created_at DESC` 排序（厂商 `generateExportExcel:473` 的 `isInWhenPresent` + `orderBy`）。
     */
    @Select("""
            <script>
            SELECT id, `key`, name, `desc`, updated_at, created_at FROM g_sub_role
            <if test="ids != null and ids.size() > 0">
              WHERE id IN
              <foreach collection="ids" item="it" open="(" separator="," close=")">#{it}</foreach>
            </if>
            ORDER BY created_at DESC
            </script>
            """)
    List<SubRolePO> selectForExport(@Param("ids") java.util.List<String> ids);

    /** 该子角色关联用户的**用户名**列表（厂商 `generateExportExcel:486-488`）。 */
    @Select("""
            SELECT u.username FROM g_user_sub_role usr
            JOIN g_user u ON u.id = usr.user_id
            WHERE usr.sub_role_id = #{subRoleId}
            """)
    List<String> selectUsernamesBySubRoleId(@Param("subRoleId") String subRoleId);

    /** 该子角色关联战位的 **key** 列表（厂商 `generateExportExcel:492-494`）。 */
    @Select("""
            SELECT r.`key` FROM g_sub_role_role srr
            JOIN g_role r ON r.id = srr.role_id
            WHERE srr.sub_role_id = #{subRoleId}
            """)
    List<String> selectRoleKeysBySubRoleId(@Param("subRoleId") String subRoleId);

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
     * 新增子角色时的重名检查（厂商 `existKeyName:108-110`）。
     * <p><b>注意查的是 `g_role`（战位表）</b>——厂商用的是 `subRoleMapper` 但 where 写的是
     * `RoleDynamicSqlSupport.role.key/name`，SQL 落在 `g_role` 上（照抄）。
     */
    @Select("""
            SELECT COUNT(*) FROM g_role WHERE `key` = #{key} OR name = #{name}
            """)
    long countRoleByKeyOrName(@Param("key") String key, @Param("name") String name);

    /** 修改时的 key 重复检查（厂商 `duplicateKey:361-363`：`key = ? AND id <> ?`）。 */
    @Select("""
            SELECT COUNT(*) FROM g_sub_role WHERE `key` = #{key} AND id <> #{id}
            """)
    long countByKeyExcludingId(@Param("key") String key, @Param("id") String id);

    /** 修改时的 name 重复检查（厂商 `duplicateName:365-367`）。 */
    @Select("""
            SELECT COUNT(*) FROM g_sub_role WHERE name = #{name} AND id <> #{id}
            """)
    long countByNameExcludingId(@Param("name") String name, @Param("id") String id);

    /** 插子角色（厂商 `addRole:135` 的 `insert`）。 */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO g_sub_role (id, `key`, name, `desc`, updated_at, created_at)
            VALUES (#{id}, #{key}, #{name}, #{desc}, #{updatedAt}, #{createdAt})
            """)
    int insertSubRole(@Param("id") String id, @Param("key") String key, @Param("name") String name,
                      @Param("desc") String desc, @Param("updatedAt") long updatedAt,
                      @Param("createdAt") long createdAt);

    /**
     * 选择性更新（厂商 `updateByPrimaryKeySelective`）：**只更新非 null 的字段**，
     * `desc` 允许显式置 null（厂商在 blank 时 setDesc(null)，然后选择性更新会跳过 null ⇒ 不写 desc）。
     */
    @org.apache.ibatis.annotations.Update("""
            <script>
            UPDATE g_sub_role
            <set>
              <if test="key != null">`key` = #{key},</if>
              <if test="name != null">name = #{name},</if>
              <if test="desc != null">`desc` = #{desc},</if>
              updated_at = #{updatedAt},
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateSubRoleSelective(@Param("id") String id, @Param("key") String key, @Param("name") String name,
                               @Param("desc") String desc, @Param("updatedAt") long updatedAt);

    /** 该用户是否存在（厂商 `usersRelateRole:148-149` 对每个 userId `selectByPrimaryKey`，查不到就跳过）。 */
    @Select("SELECT COUNT(*) FROM g_user WHERE id = #{userId}")
    long countUserById(@Param("userId") String userId);

    /** 删该用户的全部"用户-子角色"关联（厂商 `usersRelateRole:145`）。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM g_user_sub_role WHERE user_id = #{userId}")
    int deleteUserSubRolesByUserId(@Param("userId") String userId);

    /** 插一条"用户-子角色"关联。 */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO g_user_sub_role (id, user_id, sub_role_id, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{subRoleId}, #{createdAt}, #{updatedAt})
            """)
    int insertUserSubRole(@Param("id") String id, @Param("userId") String userId,
                          @Param("subRoleId") String subRoleId, @Param("createdAt") long createdAt,
                          @Param("updatedAt") long updatedAt);

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
