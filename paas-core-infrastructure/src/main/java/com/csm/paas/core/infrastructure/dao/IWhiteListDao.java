package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.WhiteListPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 白名单表（`g_white_list`）。 */
@Mapper
public interface IWhiteListDao {

    /**
     * 分页 + `path LIKE`（keyword 为 null 时不加条件）+ `created_at DESC`
     * （厂商 `WhiteListService.list:36-44`）。
     */
    @Select("""
            <script>
            SELECT id, path, created_at, updated_at FROM g_white_list
            <if test="likeKeyword != null">WHERE path LIKE #{likeKeyword}</if>
            ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<WhiteListPO> selectPage(@Param("likeKeyword") String likeKeyword,
                                 @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM g_white_list
            <if test="likeKeyword != null">WHERE path LIKE #{likeKeyword}</if>
            </script>
            """)
    long selectCount(@Param("likeKeyword") String likeKeyword);

    @Select("SELECT id, path, created_at, updated_at FROM g_white_list WHERE path = #{path} LIMIT 1")
    WhiteListPO selectByPath(@Param("path") String path);

    @Select("SELECT id, path, created_at, updated_at FROM g_white_list WHERE id = #{id}")
    WhiteListPO selectById(@Param("id") long id);

    @Insert("INSERT INTO g_white_list (path, created_at) VALUES (#{path}, #{createdAt})")
    int insert(@Param("path") String path, @Param("createdAt") long createdAt);

    @Delete("DELETE FROM g_white_list WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
