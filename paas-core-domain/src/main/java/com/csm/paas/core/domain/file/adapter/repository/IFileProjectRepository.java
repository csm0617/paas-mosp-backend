package com.csm.paas.core.domain.file.adapter.repository;

import com.csm.paas.core.domain.file.model.entity.FileProjectEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code g_file_project} 仓储（厂商 {@code FileProjectMapper}，见
 * {@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/service/FileProjectService.java}）。
 *
 * <p>照抄点：列表只取 {@code deleted = false} 且按 {@code created_at DESC}；
 * 删除是**物理删除**（{@code deleteByPrimaryKey}），不是把 {@code deleted} 置 true。
 */
public interface IFileProjectRepository {

    /**
     * 一页业务软件（厂商 {@code FileProjectService.list:34-42}：PageHelper +
     * {@code name like} + {@code deleted=false} + {@code order by created_at desc}）。
     *
     * @param likeKeyword 调用方拼好的 {@code %kw%}；**null 表示不过滤**（厂商这里没有 trim/isBlank）
     */
    List<FileProjectEntity> queryFileProjectPage(String likeKeyword, int offset, int size);

    /** 同一条 where 的总数（PageHelper 的 total）。 */
    long countFileProjects(String likeKeyword);

    /** 按 id 取且 {@code deleted = false}（厂商各处的 {@code where id=? and deleted=false}）。 */
    Optional<FileProjectEntity> queryByIdAndNotDeleted(String id);

    /**
     * 按主键取行，**不过滤 deleted**（厂商 {@code selectByPrimaryKey}）。
     *
     * <p>文件列表组装 {@code projectName} 时用的就是它（{@code FileMinioService.java:94}、
     * {@code FileService.java:357}），所以已被逻辑删除的项目名也会被回填。
     */
    Optional<FileProjectEntity> queryById(String id);

    /** 按 name 取且 {@code deleted = false}（新增查重与新增后反查都用它）。 */
    Optional<FileProjectEntity> queryByNameAndNotDeleted(String name);

    /** 按 name 取且 {@code deleted = false} 且 {@code id <> excludeId}（修改时的查重）。 */
    Optional<FileProjectEntity> queryByNameAndNotDeletedExceptId(String name, String excludeId);

    /** 插入一行，null 列不进 INSERT 列表（厂商 {@code insertSelective}）。 */
    int insertFileProjectSelective(FileProjectEntity project);

    /**
     * 整行更新（厂商 {@code updateByPrimaryKey}：**无条件 SET 全部列**，
     * 包括 {@code created_at}/{@code deleted}，null 也会写进去）。
     */
    int updateFileProject(FileProjectEntity project);

    /** 按主键物理删除。 */
    int deleteFileProjectById(String id);
}
