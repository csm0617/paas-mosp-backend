package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.ImageGroupPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 镜像分组（表 `g_group`；厂商 `K3sImageServiceImpl` 的 `imageGroupMapper`）。 */
@Mapper
public interface IImageGroupDao {

    @Select("""
            <script>
            SELECT id, create_date, name FROM g_group
            <where><if test="likeName != null">name LIKE #{likeName}</if></where>
            ORDER BY create_date DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ImageGroupPO> selectPage(@Param("likeName") String likeName,
                                  @Param("offset") int offset, @Param("size") int size);

    /** 厂商用 `isLikeWhenPresent(name)` 且**不带通配符** ⇒ 等价于等值匹配。 */
    @Select("SELECT id, create_date, name FROM g_group WHERE name LIKE #{name} LIMIT 1")
    ImageGroupPO selectByName(@Param("name") String name);

    @Insert("INSERT INTO g_group (create_date, name) VALUES (#{createDate}, #{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImageGroupPO group);

    @Select("SELECT id, create_date, name FROM g_group WHERE id = #{id}")
    ImageGroupPO selectById(@Param("id") int id);
}
