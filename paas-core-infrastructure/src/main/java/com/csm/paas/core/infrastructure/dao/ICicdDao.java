package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.CicdPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * `g_ci_cd` 的数据访问（厂商 `dao/CicdMapper.java` + mybatis-dynamic-sql）。
 *
 * <p>归属：本次任务白名单允许新建 `dao/ICicdDao.java` 与 `dao/po/Cicd*.java`。
 *
 * <p>照抄点：
 * <ul>
 *   <li>`user_id = #{userId}` 在 userId 为 null 时是 `= NULL`（**恒不命中**）——
 *       与厂商 `SqlBuilder.isEqualTo(null)` 的 SQL 语义一致，不要写成动态条件；</li>
 *   <li>`insertSelective` 只写**非 null** 列（厂商 `insertSelective`）——
 *       厂商那 13 列里没有默认值依赖，故非 null 列之外的列交给 DDL 默认；</li>
 *   <li>`updateByPrimaryKey` 是**整行 13 列全覆盖**（厂商 `updateByPrimaryKey`，null 会写成 NULL）；</li>
 *   <li>`updateByPrimaryKeySelective` 只写非 null 列（厂商 `updateByPrimaryKeySelective`）。</li>
 * </ul>
 */
@Mapper
public interface ICicdDao {

    @Select("SELECT id, user_id, image_name, image_tag, dockerfile_id, dockerfile_content, buildfile_id, "
            + "job_name, job_status, description, create_at, update_at, image_storage "
            + "FROM g_ci_cd WHERE id = #{id}")
    CicdPO selectById(@Param("id") Integer id);

    /**
     * 厂商 `CicdService:287/302/310/430` 的 `where image_name = ? and user_id = ?`（无排序）。
     * <p>注意调用方只取 `get(0)` 或判空，所以不排序与厂商一致。
     */
    @Select("SELECT id, user_id, image_name, image_tag, dockerfile_id, dockerfile_content, buildfile_id, "
            + "job_name, job_status, description, create_at, update_at, image_storage "
            + "FROM g_ci_cd WHERE image_name = #{imageName} AND user_id = #{userId}")
    List<CicdPO> selectByImageNameAndUserId(@Param("imageName") String imageName,
                                            @Param("userId") String userId);

    /** 厂商 `CicdService.cicdList:213` 的分页查询（`like` 仅在 likeKeyword 非 null 时出现）。 */
    @Select("<script>"
            + "SELECT id, user_id, image_name, image_tag, dockerfile_id, dockerfile_content, buildfile_id, "
            + "job_name, job_status, description, create_at, update_at, image_storage FROM g_ci_cd "
            + "WHERE user_id = #{userId} "
            + "<if test='likeKeyword != null'> AND image_name LIKE #{likeKeyword} </if>"
            + "ORDER BY create_at DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<CicdPO> selectPage(@Param("userId") String userId, @Param("likeKeyword") String likeKeyword,
                            @Param("limit") int limit, @Param("offset") int offset);

    /** PageHelper 的 count 查询等价物（过滤条件与 {@link #selectPage} 完全一致）。 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM g_ci_cd WHERE user_id = #{userId} "
            + "<if test='likeKeyword != null'> AND image_name LIKE #{likeKeyword} </if>"
            + "</script>")
    long countByUserAndKeyword(@Param("userId") String userId, @Param("likeKeyword") String likeKeyword);

    /** 厂商 `insertSelective`：只写非 null 列。 */
    @Insert("<script>"
            + "INSERT INTO g_ci_cd "
            + "<trim prefix='(' suffix=')' suffixOverrides=','>"
            + "  <if test='id != null'>id,</if>"
            + "  <if test='userId != null'>user_id,</if>"
            + "  <if test='imageName != null'>image_name,</if>"
            + "  <if test='imageTag != null'>image_tag,</if>"
            + "  <if test='dockerfileId != null'>dockerfile_id,</if>"
            + "  <if test='dockerfileContent != null'>dockerfile_content,</if>"
            + "  <if test='buildfileId != null'>buildfile_id,</if>"
            + "  <if test='jobName != null'>job_name,</if>"
            + "  <if test='jobStatus != null'>job_status,</if>"
            + "  <if test='description != null'>description,</if>"
            + "  <if test='createAt != null'>create_at,</if>"
            + "  <if test='updateAt != null'>update_at,</if>"
            + "  <if test='imageStorage != null'>image_storage,</if>"
            + "</trim>"
            + "<trim prefix='VALUES (' suffix=')' suffixOverrides=','>"
            + "  <if test='id != null'>#{id},</if>"
            + "  <if test='userId != null'>#{userId},</if>"
            + "  <if test='imageName != null'>#{imageName},</if>"
            + "  <if test='imageTag != null'>#{imageTag},</if>"
            + "  <if test='dockerfileId != null'>#{dockerfileId},</if>"
            + "  <if test='dockerfileContent != null'>#{dockerfileContent},</if>"
            + "  <if test='buildfileId != null'>#{buildfileId},</if>"
            + "  <if test='jobName != null'>#{jobName},</if>"
            + "  <if test='jobStatus != null'>#{jobStatus},</if>"
            + "  <if test='description != null'>#{description},</if>"
            + "  <if test='createAt != null'>#{createAt},</if>"
            + "  <if test='updateAt != null'>#{updateAt},</if>"
            + "  <if test='imageStorage != null'>#{imageStorage},</if>"
            + "</trim>"
            + "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(CicdPO po);

    /** 厂商 `updateByPrimaryKey`：整行更新（null ⇒ 写 NULL）。 */
    @Update("UPDATE g_ci_cd SET user_id = #{userId}, image_name = #{imageName}, image_tag = #{imageTag}, "
            + "dockerfile_id = #{dockerfileId}, dockerfile_content = #{dockerfileContent}, "
            + "buildfile_id = #{buildfileId}, job_name = #{jobName}, job_status = #{jobStatus}, "
            + "description = #{description}, create_at = #{createAt}, update_at = #{updateAt}, "
            + "image_storage = #{imageStorage} WHERE id = #{id}")
    int updateByPrimaryKey(CicdPO po);

    /** 厂商 `updateByPrimaryKeySelective`：只更新非 null 列。 */
    @Update("<script>"
            + "UPDATE g_ci_cd "
            + "<set>"
            + "  <if test='userId != null'>user_id = #{userId},</if>"
            + "  <if test='imageName != null'>image_name = #{imageName},</if>"
            + "  <if test='imageTag != null'>image_tag = #{imageTag},</if>"
            + "  <if test='dockerfileId != null'>dockerfile_id = #{dockerfileId},</if>"
            + "  <if test='dockerfileContent != null'>dockerfile_content = #{dockerfileContent},</if>"
            + "  <if test='buildfileId != null'>buildfile_id = #{buildfileId},</if>"
            + "  <if test='jobName != null'>job_name = #{jobName},</if>"
            + "  <if test='jobStatus != null'>job_status = #{jobStatus},</if>"
            + "  <if test='description != null'>description = #{description},</if>"
            + "  <if test='createAt != null'>create_at = #{createAt},</if>"
            + "  <if test='updateAt != null'>update_at = #{updateAt},</if>"
            + "  <if test='imageStorage != null'>image_storage = #{imageStorage},</if>"
            + "</set>"
            + "WHERE id = #{id}"
            + "</script>")
    int updateByPrimaryKeySelective(CicdPO po);

    @Delete("DELETE FROM g_ci_cd WHERE id = #{id}")
    int deleteById(@Param("id") Integer id);
}
