package com.csm.paas.core.domain.file.service;

import com.csm.paas.core.domain.file.model.entity.FileEntity;

import java.util.List;

/**
 * 文件域写服务（上传 / 删除）。
 *
 * <p>覆盖厂商两个独立微服务的写端点：
 * <ul>
 *   <li>{@code POST /v1/pub/file/upload/{projectId}} ——{@code PublicMinioController.java:73-80} →
 *       {@code FileMinioService.uploadFile:107-139}（MinIO 分支）；</li>
 *   <li>{@code POST /v1/pub/fdfs/upload/{projectId}} ——{@code gaop-fdfs/.../PublicFdfsController.java:61-67}
 *       / {@code gaop-be/.../pub/PublicFdfsController.java:92-98} → {@code FileService.uploadFile:495-533}；</li>
 *   <li>{@code DELETE /v1/pub/file/{fileId}} ——{@code PublicMinioController.java:88-93} →
 *       {@code FileMinioService.delFile:182-194}；</li>
 *   <li>{@code DELETE /v1/pub/file/delete} ——{@code PublicMinioController.java:95-101} →
 *       {@code FileMinioService.delFileList:196-211}。</li>
 * </ul>
 *
 * <p>入参用 {@code byte[]} 而不是 {@code MultipartFile}：本仓 domain 不依赖 spring-web
 * （与 {@code ILanguageCommandService.importLanguageExcel} 拿解析结果 VO 的做法一致），
 * 由 trigger 层读 {@code MultipartFile} 的字节。
 */
public interface IFileCommandService {

    /**
     * MinIO 分支上传：写对象存储 + 插 {@code g_file}，返回插入后按 path 反查的整行。
     *
     * <p>照抄点（{@code FileMinioService.uploadFile:107-139}）：
     * <ul>
     *   <li><b>不校验 projectId 是否存在</b>（对比 fdfs 分支会校验）——怪点，必须保留；</li>
     *   <li>先算 md5 再上传；objectName 由端口按 {@code UUID + 扩展名} 生成；</li>
     *   <li>插入的行 {@code currentSize = size}、{@code deleted}/{@code updatedAt} 不设置；</li>
     *   <li>反查不到那一行时照抄 {@code Optional.get()}（抛 {@code NoSuchElementException} → 500）。</li>
     * </ul>
     */
    FileEntity uploadMinio(String projectId, byte[] content, String originalFilename);

    /**
     * FastDFS 分支上传。
     *
     * <p>照抄点（{@code gaop-fdfs/.../FileService.uploadFile:495-533}）：
     * <b>先校验业务软件存在</b>（查 {@code g_file_project} 的 {@code id=? and deleted=false}，
     * 查不到 ⇒ 400 {@code id指向的业务软件不存在}），再算 md5、再上传、再插行。
     *
     * <p><b>类 C：本环境无 FastDFS 对端，且私有协议未实现</b>——校验与 md5 这段可跑，
     * 到 {@code IFdfsFileStorePort.upload} 会抛错（见该端口的 javadoc）。
     */
    FileEntity uploadFdfs(String projectId, byte[] content, String originalFilename);

    /**
     * 删单个文件。
     *
     * <p>照抄 {@code FileMinioService.delFile:182-194}：按**主键**查（不过滤 deleted），
     * 查不到 ⇒ 400 {@code 删除失败，找不到业务软件下的该文件}；对象删除成功才删库行；
     * 对象删除失败 ⇒ 返回 {@code false} 且**保留**库行（不抛异常）。
     */
    Boolean deleteFile(String fileId);

    /**
     * 批量删文件。照抄 {@code FileMinioService.delFileList:196-211}：逐个删，
     * 任何一个 id 查不到就**立刻抛 400**（前面已删掉的不回滚），全部成功返回 true。
     */
    Boolean deleteFiles(List<String> fileIdList);
}
