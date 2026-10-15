package com.csm.paas.core.domain.file.service;

import com.csm.paas.core.domain.file.model.entity.FileDownloadEntity;
import com.csm.paas.core.domain.file.model.entity.FileListEntity;
import com.csm.paas.core.domain.file.model.valobj.FdfsStorageNodeVO;
import com.csm.paas.core.domain.file.model.valobj.MinioStorageVO;

import java.util.List;

/**
 * 文件域查询服务。
 *
 * <p>覆盖厂商两个独立微服务里"只读"的文件端点：
 * <ul>
 *   <li>{@code GET /v1/pub/file/list} ——{@code PublicMinioController.java:59-71} →
 *       {@code FileMinioService.list:87-105}（MinIO 分支）；</li>
 *   <li>{@code GET /v1/pub/file/download/{fileId}} ——{@code PublicMinioController.java:82-86} →
 *       {@code FileMinioService.download:141-180}；</li>
 *   <li>{@code GET /v1/pub/file/storage} ——{@code PublicMinioController.java:124-128} →
 *       {@code FileMinioService.getStorageInfo:404-429}；</li>
 *   <li>{@code GET /v1/pub/fdfs/status} ——{@code gaop-be/.../controller/pub/PublicFdfsController.java:53-58}
 *       →{@code gaop-be/.../service/FileService.getFdfsStatusInfos:323-366}。</li>
 * </ul>
 */
public interface IFileQueryService {

    /**
     * 一页文件。
     *
     * <p>对应厂商 {@code GET /v1/pub/file/list}。入参 {@code start}/{@code end} 已由调用方
     * 做完"end 加一天"的换算（厂商在 controller 里做，见 {@code PublicMinioController.java:64-68}）。
     *
     * <p>规则（照抄 {@code FileMinioService.list}）：
     * <ul>
     *   <li>{@code offset = (page-1)*size}，关键字 {@code trim} 后拼 {@code %kw%}，空白视作 null；</li>
     *   <li>{@code total} 只按 {@code projectId} 计数（忽略时间/关键字）；</li>
     *   <li>每行的 {@code projectName} 按 {@code file_project_id} 反查（**不过滤 deleted**），
     *       查不到保持 null。</li>
     * </ul>
     */
    FileListEntity queryFileList(Long start, Long end, String keyword, String projectId, int page, int size);

    /**
     * 组装下载描述（不写响应）。
     *
     * <p>对应厂商 {@code GET /v1/pub/file/download/{fileId}} 的前半段
     * （{@code FileMinioService.download:141-175}）：查行（{@code id=? and deleted=false}，
     * 查不到 ⇒ 400 {@code 下载的文件不存在}）→ 解析 Range → stat object 取 Content-Type。
     * 后半段的响应头与字节流由 trigger 层照抄 {@code handleRangeRequest}/{@code handleFullDownload}。
     *
     * @param range 请求头 {@code Range} 的原值（形如 {@code 0-100}，**不带 bytes=**），可为 null
     */
    FileDownloadEntity prepareDownload(String fileId, String range);

    /** 对应厂商 {@code GET /v1/pub/file/storage}（MinIO Admin API，类 C，拿不到返回空列表）。 */
    List<MinioStorageVO> queryMinioStorage();

    /**
     * 对应厂商 {@code GET /v1/pub/fdfs/status}。
     *
     * <p>照抄点：拿不到存储信息时**不报错**，而是给每个配置的 tracker 造一条
     * {@code status=99(NONE)}、大小 0、{@code group="group1"} 的占位记录；
     * 重试循环只判 {@code != null}（空列表也算成功，直接跳出）。
     */
    List<FdfsStorageNodeVO> queryFdfsStatus();
}
