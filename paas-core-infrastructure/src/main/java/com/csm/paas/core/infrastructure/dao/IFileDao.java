package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.FilePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * `g_file` 的 SQL（厂商 {@code com.glriverside.gaopfdfs.dao.FileMapper}，
 * 见 {@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/dao/FileMapper.java}）。
 *
 * <p>三条 SELECT 都是**逐字照抄**，包括厂商的 SQL 怪点（见各方法 javadoc）。
 */
@Mapper
public interface IFileDao {

    /**
     * 一页文件，照抄厂商 {@code FileMapper.FileListInfo}（{@code FileMapper.java:131-132}）。
     *
     * <p>照抄点：
     * <ol>
     *   <li>{@code LIMIT} 在内层（先分页取 id），关键字过滤在外层——**分页先于过滤**；</li>
     *   <li>时间上界条件厂商写成 {@code created_at >= #{end}}（本该是 {@code <=}）；</li>
     *   <li>{@code deleted} 列被选出来但**不过滤**；</li>
     *   <li>内层再套一层派生表 {@code t2}，是 MySQL"LIMIT 不能直接用于 IN 子查询"的绕法，不是冗余。</li>
     * </ol>
     */
    @Select("""
            <script>
            select t1.id, t1.name, t1.file_project_id, t1.path, t1.created_at, t1.updated_at, t1.deleted,
                   t1.current_size, t1.size, t1.md5
            FROM g_file t1 WHERE t1.id in (
              select t2.* FROM (
                select id FROM g_file WHERE file_project_id = #{projectId}
                <if test='start != null'> and created_at &gt;= #{start}</if>
                <if test='end != null'> and created_at &gt;= #{end}</if>
                ORDER BY created_at desc LIMIT #{startNumber},#{limitNumber}
              ) as t2)
            <if test='keyword != null'> and t1.`name` like #{keyword}</if>
            </script>
            """)
    List<FilePO> selectFilePage(@Param("start") Long start,
                                @Param("end") Long end,
                                @Param("keyword") String keyword,
                                @Param("projectId") String projectId,
                                @Param("startNumber") Integer startNumber,
                                @Param("limitNumber") Integer limitNumber);

    /**
     * 某业务软件下的文件总数，照抄厂商 {@code FileMapper.countFileSize}（{@code FileMapper.java:129}）。
     *
     * <p>照抄点：**只按 {@code file_project_id} 计数**，既不筛时间也不筛关键字、更不筛 deleted。
     */
    @Select("select count(gf.id) FROM g_file gf WHERE gf.file_project_id = #{projectId}")
    Long countFileSize(@Param("projectId") String projectId);

    /** 按主键取行（厂商 {@code selectByPrimaryKey}，不过滤 deleted）。 */
    @Select("SELECT id, name, file_project_id, path, created_at, updated_at, deleted, current_size, size, md5 "
            + "FROM g_file WHERE id = #{id}")
    FilePO selectById(@Param("id") String id);

    /** 按主键取行且 {@code deleted = 0}（厂商下载路径的 {@code where id=? and deleted=false}）。 */
    @Select("SELECT id, name, file_project_id, path, created_at, updated_at, deleted, current_size, size, md5 "
            + "FROM g_file WHERE id = #{id} AND deleted = 0")
    FilePO selectByIdAndNotDeleted(@Param("id") String id);

    /** 按 {@code path} 取行（厂商上传后 {@code where path = ?} 反查整行，不过滤 deleted）。 */
    @Select("SELECT id, name, file_project_id, path, created_at, updated_at, deleted, current_size, size, md5 "
            + "FROM g_file WHERE path = #{path}")
    FilePO selectByPath(@Param("path") String path);

    /** 插入一行，null 列不进 INSERT 列表（厂商 {@code insertSelective}）。 */
    @Insert("<script>"
            + "INSERT INTO g_file "
            + "<trim prefix='(' suffix=')' suffixOverrides=','>"
            + "  <if test='id != null'>id,</if>"
            + "  <if test='name != null'>name,</if>"
            + "  <if test='fileProjectId != null'>file_project_id,</if>"
            + "  <if test='path != null'>path,</if>"
            + "  <if test='createdAt != null'>created_at,</if>"
            + "  <if test='updatedAt != null'>updated_at,</if>"
            + "  <if test='deleted != null'>deleted,</if>"
            + "  <if test='currentSize != null'>current_size,</if>"
            + "  <if test='size != null'>size,</if>"
            + "  <if test='md5 != null'>md5,</if>"
            + "</trim>"
            + "<trim prefix='VALUES (' suffix=')' suffixOverrides=','>"
            + "  <if test='id != null'>#{id},</if>"
            + "  <if test='name != null'>#{name},</if>"
            + "  <if test='fileProjectId != null'>#{fileProjectId},</if>"
            + "  <if test='path != null'>#{path},</if>"
            + "  <if test='createdAt != null'>#{createdAt},</if>"
            + "  <if test='updatedAt != null'>#{updatedAt},</if>"
            + "  <if test='deleted != null'>#{deleted},</if>"
            + "  <if test='currentSize != null'>#{currentSize},</if>"
            + "  <if test='size != null'>#{size},</if>"
            + "  <if test='md5 != null'>#{md5},</if>"
            + "</trim>"
            + "</script>")
    int insertFileSelective(FilePO file);

    /** 按主键物理删除（厂商 {@code deleteByPrimaryKey}）。 */
    @Delete("DELETE FROM g_file WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}
