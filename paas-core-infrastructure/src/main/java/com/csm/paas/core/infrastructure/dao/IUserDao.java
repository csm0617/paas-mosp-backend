package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.UserPO;
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
}
