package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.UserPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IUserDao {

    @Select("""
            SELECT id, username, password, status, email, birth, phone,
                   updated_at, created_at, last_login_role, nickname, uuid, `desc`, avatar
            FROM g_user WHERE username = #{username}
            """)
    UserPO selectByUsername(@Param("username") String username);

    @Select("SELECT username, last_login_role, avatar FROM g_user ORDER BY created_at")
    java.util.List<UserPO> selectLoginList();

    @Select("SELECT COUNT(*) FROM g_user")
    long selectCount();

    /**
     * 用户管理列表：用户名模糊 + {@code created_at DESC} + 分页。
     * <p>厂商用 PageHelper（{@code UserService.java:1829,1835}）；这里显式写 LIMIT/OFFSET，
     * 语义等价（{@code likeKeyword} 为 null 时不过滤，与 {@code SqlBuilder.isLikeWhenPresent} 一致）。
     */
    @Select("""
            <script>
            SELECT id, username, password, status, email, birth, phone,
                   updated_at, created_at, last_login_role, nickname, uuid, `desc`, avatar
            FROM g_user
            <where>
              <if test="likeKeyword != null">username LIKE #{likeKeyword}</if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    java.util.List<UserPO> selectPageByKeyword(@Param("likeKeyword") String likeKeyword,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM g_user
            <where>
              <if test="likeKeyword != null">username LIKE #{likeKeyword}</if>
            </where>
            </script>
            """)
    long selectCountByKeyword(@Param("likeKeyword") String likeKeyword);

    @Select("""
            SELECT id, username, password, status, email, birth, phone,
                   updated_at, created_at, last_login_role, nickname, uuid, `desc`, avatar
            FROM g_user WHERE id = #{id}
            """)
    UserPO selectById(@Param("id") String id);

    /** 厂商 `add` 里用于分配 `uuid` 的全量查询（`UserService.java:484`）。 */
    @Select("""
            SELECT id, username, password, status, email, birth, phone,
                   updated_at, created_at, last_login_role, nickname, uuid, `desc`, avatar
            FROM g_user
            """)
    java.util.List<UserPO> selectAll();

    /** 用户关联的战位 key（厂商 `RoleList.containAdminRole()` 的判定依据）。 */
    @Select("""
            SELECT r.`key` FROM g_user_role ur
            JOIN g_user u ON u.id = ur.user_id
            JOIN g_role r ON r.id = ur.role_id
            WHERE u.username = #{username}
            """)
    java.util.List<String> selectRoleKeysByUsername(@Param("username") String username);

    // ---------- 删除（厂商 UserService.getDeleteUser，UserService.java:829-834）----------

    @Delete("DELETE FROM g_user_role WHERE user_id = #{userId}")
    int deleteUserRoleByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM g_user_sub_role WHERE user_id = #{userId}")
    int deleteUserSubRoleByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM g_user WHERE id = #{userId}")
    int deleteById(@Param("userId") String userId);

    // ---------- 新增与关系（厂商 UserService.add / rolesRelate2User / SubRoleService.userAddRole）----------

    @Insert("""
            INSERT INTO g_user (id, username, password, status, email, birth, phone, `desc`,
                                updated_at, created_at, avatar, last_login_role, nickname, uuid)
            VALUES (#{id}, #{username}, #{password}, #{status}, #{email}, #{birth}, #{phone}, #{desc},
                    #{updatedAt}, #{createdAt}, #{avatar}, #{lastLoginRole}, #{nickname}, #{uuid})
            """)
    int insert(UserPO user);

    /**
     * 更新用户。列集合对齐厂商 `updateByPrimaryKeySelective` 实际会写到的字段
     * （`uuid`/`api_token`/`tenx_username`/`last_login_role` 在厂商那边是 null，不会被写）。
     */
    @Update("""
            UPDATE g_user SET username = #{username}, password = #{password}, status = #{status},
                              nickname = #{nickname}, `desc` = #{desc}, avatar = #{avatar},
                              email = #{email}, birth = #{birth}, phone = #{phone},
                              created_at = #{createdAt}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(UserPO user);

    /**
     * 按 key 取战位/角色（id + key），供调用方按入参顺序重排。
     * <p>**必须用 PO 而不是 Map**：H2 返回的列标签是大写（`ID`/`KEY`），Map 的键会跟着变大写，
     * 而 MySQL 是小写——用 PO 让 MyBatis 做大小写不敏感的属性映射，两种库都能跑。
     */
    @Select("""
            <script>
            SELECT id, `key` FROM g_role
            WHERE `key` IN <foreach item='item' collection='keys' open='(' separator=',' close=')'>#{item}</foreach>
            </script>
            """)
    java.util.List<com.csm.paas.core.infrastructure.dao.po.RolePO> selectIdAndKeyByKeys(@Param("keys") java.util.List<String> keys);

    /**
     * 删除用户"某一类型"的战位/角色关联（厂商 `UserRoleMapper.deleteRelateUserToRole`）：
     * 按 `g_role.type` 过滤，用嵌套派生表避开 MySQL "不能在子查询里更新同一张表"的限制。
     */
    @Delete("""
            DELETE FROM g_user_role WHERE id IN (
              SELECT w.id FROM (
                SELECT ur.id FROM g_user_role ur
                JOIN g_role r ON r.id = ur.role_id
                WHERE r.type = #{type} AND ur.user_id = #{userId}
              ) w
            )
            """)
    int deleteUserRoleByUserIdAndRoleType(@Param("userId") String userId, @Param("type") int type);

    @Insert("""
            <script>
            INSERT INTO g_user_role (user_id, role_id, created_at) VALUES
            <foreach item='roleId' collection='roleIds' separator=','>
              (#{userId}, #{roleId}, #{createdAt})
            </foreach>
            </script>
            """)
    int insertUserRoles(@Param("userId") String userId,
                        @Param("roleIds") java.util.List<String> roleIds,
                        @Param("createdAt") long createdAt);

    @Insert("""
            INSERT INTO g_user_sub_role (id, user_id, sub_role_id, updated_at, created_at)
            VALUES (#{id}, #{userId}, #{subRoleId}, #{updatedAt}, #{createdAt})
            """)
    int insertUserSubRole(@Param("id") String id, @Param("userId") String userId,
                          @Param("subRoleId") String subRoleId,
                          @Param("updatedAt") long updatedAt, @Param("createdAt") long createdAt);
}
