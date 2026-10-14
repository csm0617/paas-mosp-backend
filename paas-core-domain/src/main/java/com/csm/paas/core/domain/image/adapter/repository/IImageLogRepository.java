package com.csm.paas.core.domain.image.adapter.repository;

import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;

import java.util.List;

/**
 * 镜像上传日志仓储（表 `g_image_log`；厂商 `dao/ImageLogMapper` + `service/DockerService`）。
 *
 * <p>注意厂商的 `DockerService.getImageList:172-197` 的分页口径：`PageHelper.startPage` 后执行查询，
 * `total` 取 `Page.getTotal()`（**满足条件的总行数**，不是当页行数）；
 * 并且会**顺手把超时的"处理中"行改成失败**（status 2 → 0 且追加一行日志），照抄。
 */
public interface IImageLogRepository {

    /** 按 `file_name like ?` 分页（`created_at` 倒序）；`likeKeyword` 为 null 表示不过滤。 */
    List<ImageLogVO> queryPage(String likeKeyword, int offset, int size);

    /** 满足条件的总行数（PageHelper 的 count 查询）。 */
    long count(String likeKeyword);

    /** 插入一行并回填自增主键（厂商 `insert` + `getLastKey` 或 `@Options(useGeneratedKeys)`）。 */
    ImageLogVO insert(ImageLogVO imageLog);

    /** 厂商 `imageLogMapper.appendLog(id, text)`：把文本**追加**到 `log` 列末尾。 */
    void appendLog(long id, String text);

    /**
     * 厂商 `updateByPrimaryKeySelective`：**只更新非 null 字段**。
     * 调用方负责把要改的字段填好、id 填好。
     */
    void updateSelective(ImageLogVO imageLog);

    /** 厂商 `getRepositories` 用：`status = 1` 且 `updated_at` 倒序的全部行。 */
    List<ImageLogVO> querySuccessOrderByUpdatedAtDesc();
}
