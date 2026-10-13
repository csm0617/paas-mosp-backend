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
}
