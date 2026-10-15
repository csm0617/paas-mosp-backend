package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.FileProjectPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * `g_file_project` 的 SQL（厂商 {@code com.glriverside.gaop.be.dao.FileProjectMapper}，
 * 见 {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/service/FileProjectService.java}）。
 *
 * <p>厂商用 PageHelper + MyBatis Dynamic SQL；这里换成分页参数直写，
 * where / order by 与厂商逐条对齐（见各方法 javadoc）。
 */
@Mapper
public interface IFileProjectDao {

    /**
     * 一页业务软件（厂商 {@code FileProjectService.list:37} 的动态 SQL：
     * {@code deleted = false} + 可选 {@code name like} + {@code order by created_at desc}）。
     */
    @Select("""
            <script>
            SELECT id, name, created_at, updated_at, deleted FROM g_file_project
            WHERE deleted = 0
            <if test='likeKeyword != null'> AND name LIKE #{likeKeyword}</if>
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<FileProjectPO> selectFileProjectPage(@Param("likeKeyword") String likeKeyword,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    /** 同一条 where 的总数（厂商 PageHelper 的 total）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM g_file_project
            WHERE deleted = 0
            <if test='likeKeyword != null'> AND name LIKE #{likeKeyword}</if>
            </script>
            """)
    long countFileProjects(@Param("likeKeyword") String likeKeyword);

    /** 按主键取行（厂商 {@code selectByPrimaryKey}，**不过滤 deleted**）。 */
    @Select("SELECT id, name, created_at, updated_at, deleted FROM g_file_project WHERE id = #{id}")
    FileProjectPO selectById(@Param("id") String id);

    /** 按 id 取且 {@code deleted = 0}。 */
    @Select("SELECT id, name, created_at, updated_at, deleted FROM g_file_project "
            + "WHERE id = #{id} AND deleted = 0")
    FileProjectPO selectByIdAndNotDeleted(@Param("id") String id);

    /** 按 name 取且 {@code deleted = 0}（新增查重 + 新增后反查）。 */
    @Select("SELECT id, name, created_at, updated_at, deleted FROM g_file_project "
            + "WHERE name = #{name} AND deleted = 0")
    FileProjectPO selectByNameAndNotDeleted(@Param("name") String name);

    /** 修改查重：{@code name = ?} 且 {@code id <> ?} 且 {@code deleted = 0}。 */
    @Select("SELECT id, name, created_at, updated_at, deleted FROM g_file_project "
            + "WHERE name = #{name} AND id <> #{excludeId} AND deleted = 0")
    FileProjectPO selectByNameAndNotDeletedExceptId(@Param("name") String name,
                                                    @Param("excludeId") String excludeId);

    /** 插入一行，null 列不进 INSERT 列表（厂商 {@code insertSelective}）。 */
    @Insert("<script>"
            + "INSERT INTO g_file_project "
            + "<trim prefix='(' suffix=')' suffixOverrides=','>"
            + "  <if test='id != null'>id,</if>"
            + "  <if test='name != null'>name,</if>"
            + "  <if test='createdAt != null'>created_at,</if>"
            + "  <if test='updatedAt != null'>updated_at,</if>"
            + "  <if test='deleted != null'>deleted,</if>"
            + "</trim>"
            + "<trim prefix='VALUES (' suffix=')' suffixOverrides=','>"
            + "  <if test='id != null'>#{id},</if>"
            + "  <if test='name != null'>#{name},</if>"
            + "  <if test='createdAt != null'>#{createdAt},</if>"
            + "  <if test='updatedAt != null'>#{updatedAt},</if>"
            + "  <if test='deleted != null'>#{deleted},</if>"
            + "</trim>"
            + "</script>")
    int insertFileProjectSelective(FileProjectPO project);

    /**
     * 整行更新（厂商 {@code updateByPrimaryKey}：SET 全部业务列，**null 也照写**）。
     */
    @Update("UPDATE g_file_project SET name = #{name}, created_at = #{createdAt}, "
            + "updated_at = #{updatedAt}, deleted = #{deleted} WHERE id = #{id}")
    int updateFileProject(FileProjectPO project);

    /** 按主键物理删除（厂商 {@code deleteByPrimaryKey}）。 */
    @Delete("DELETE FROM g_file_project WHERE id = #{id}")
    int deleteFileProjectById(@Param("id") String id);
}
