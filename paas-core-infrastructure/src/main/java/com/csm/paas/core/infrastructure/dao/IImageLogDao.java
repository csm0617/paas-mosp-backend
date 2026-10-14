package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.ImageLogPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 镜像上传日志（表 `g_image_log`；厂商 `dao/ImageLogMapper`）。
 * <p>字段与列名一一对应（`map-underscore-to-camel-case: true`）。
 */
@Mapper
public interface IImageLogDao {

    /** 厂商 `DockerService.getImageList`：`file_name like ?`、`created_at` 倒序、分页。 */
    @Select("""
            <script>
            SELECT id, file_name, name, version, status, created_at, updated_at, username, log FROM g_image_log
            <where><if test="likeKeyword != null">file_name LIKE #{likeKeyword}</if></where>
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ImageLogPO> selectPage(@Param("likeKeyword") String likeKeyword,
                               @Param("offset") int offset, @Param("size") int size);

    /** PageHelper 的 count 查询（`total` 用）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM g_image_log
            <where><if test="likeKeyword != null">file_name LIKE #{likeKeyword}</if></where>
            </script>
            """)
    long count(@Param("likeKeyword") String likeKeyword);

    @Select("SELECT id, file_name, name, version, status, created_at, updated_at, username, log "
            + "FROM g_image_log WHERE id = #{id}")
    ImageLogPO selectById(@Param("id") long id);

    @Insert("INSERT INTO g_image_log (file_name, name, version, status, created_at, updated_at, username, log) "
            + "VALUES (#{fileName}, #{name}, #{version}, #{status}, #{createdAt}, #{updatedAt}, #{username}, #{log})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImageLogPO po);

    /** 厂商 `updateByPrimaryKeySelective`：只更新非 null 字段。 */
    @Update("""
            <script>
            UPDATE g_image_log
            <set>
              <if test="fileName != null">file_name = #{fileName},</if>
              <if test="name != null">name = #{name},</if>
              <if test="version != null">version = #{version},</if>
              <if test="status != null">status = #{status},</if>
              <if test="createdAt != null">created_at = #{createdAt},</if>
              <if test="updatedAt != null">updated_at = #{updatedAt},</if>
              <if test="username != null">username = #{username},</if>
              <if test="log != null">log = #{log},</if>
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateSelective(ImageLogPO po);

    /**
     * 厂商 `ImageLogMapper` 里给 `getRepositories` 用的查询：
     * `status = 1` 且 `updated_at` 倒序（取"最后一次成功上传的用户名"）。
     */
    @Select("SELECT id, file_name, name, version, status, created_at, updated_at, username, log "
            + "FROM g_image_log WHERE status = 1 ORDER BY updated_at DESC")
    List<ImageLogPO> selectSuccessOrderByUpdatedAtDesc();
}
