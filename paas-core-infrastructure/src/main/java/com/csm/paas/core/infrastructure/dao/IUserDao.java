package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.UserPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
