package com.csm.paas.core.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 子角色表（K1）。切片 3a 只用到"用户关联的子角色名"。 */
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
}
