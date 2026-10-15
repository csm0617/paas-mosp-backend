package com.csm.paas.core.domain.file.adapter.repository;

import com.csm.paas.core.domain.file.model.entity.FileEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code g_file} 仓储（厂商 {@code FileMapper}，见
 * {@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/dao/FileMapper.java}）。
 *
 * <p>方法与厂商的一一对应，**包括它的 SQL 怪点**（见各方法 javadoc）。
 */
public interface IFileRepository {

    /**
     * 一页文件（厂商 {@code FileMapper.FileListInfo:132}）。
     *
     * <p>照抄点（三条，都会影响结果，不能"修好"）：
     * <ol>
     *   <li>分页发生在**关键字过滤之前**：先按 {@code file_project_id} + 时间条件取
     *       {@code LIMIT startNumber, limitNumber} 的 id 集，再在外层套 {@code name like}，
     *       所以命中的行数可能少于 limitNumber；</li>
     *   <li>{@code end} 条件厂商写成 {@code created_at >= #{end}}（本该是 {@code <=}），等于"只要晚于该时刻的行"；</li>
     *   <li>{@code deleted} 列被 select 出来但**不过滤**。</li>
     * </ol>
     *
     * @param startNumber 由调用方算好的偏移（厂商 {@code (page-1)*size}）
     * @param likeKeyword 调用方已经拼好 {@code %kw%} 并且 trim 过；null 表示不过滤
     */
    List<FileEntity> queryFilePage(String projectId, Long start, Long end, String likeKeyword,
                                   int startNumber, int limitNumber);

    /**
     * 某业务软件下的文件总数（厂商 {@code FileMapper.countFileSize:129}）。
     *
     * <p>照抄点：**只按 {@code file_project_id} 计数**，忽略 start/end/keyword，
     * 所以列表带关键字时 {@code total} 与实际条数不一致。
     */
    long countFilesByProjectId(String projectId);

    /** 按主键取行，**不过滤 deleted**（厂商 {@code selectByPrimaryKey}，删除路径用它）。 */
    Optional<FileEntity> queryById(String id);

    /** 按主键取行且 {@code deleted = false}（厂商下载路径的 {@code selectOne(id=? and deleted=false)}）。 */
    Optional<FileEntity> queryByIdAndNotDeleted(String id);

    /** 按 {@code path} 取行（厂商上传后按 path 反查整行返回，不过滤 deleted）。 */
    Optional<FileEntity> queryByPath(String path);

    /** 插入一行，null 列不进 INSERT 列表（厂商 {@code insertSelective}）。 */
    int insertFileSelective(FileEntity file);

    /** 按主键物理删除（厂商 {@code deleteByPrimaryKey}）。 */
    int deleteById(String id);
}
