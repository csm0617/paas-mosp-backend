package com.csm.paas.core.domain.file.adapter.port;

import com.csm.paas.core.domain.file.model.valobj.FileObjectStatVO;
import com.csm.paas.core.domain.file.model.valobj.MinioStorageVO;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储（MinIO）端口。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/utils/MinioClientUtil.java}
 * 与 {@code service/FileMinioService.java:141-180,273-298,404-429}。
 *
 * <p><b>类 C：本环境无 MinIO 对端，代码齐全但不做在线验证。</b>
 * 离线 Maven 仓库里没有 {@code io.minio:minio}（也不允许改 pom），因此实现层用
 * {@link java.net.http.HttpClient} + 手写 AWS Signature V4 直连 S3 API，见
 * {@code infrastructure/.../adapter/port/MinioS3HttpClient.java}。
 *
 * <p>异常约定（照抄厂商的 {@code handleMinioError}）：
 * <ul>
 *   <li>对象/桶不存在（HEAD/GET 404）⇒ 由实现层抛
 *       {@code WrongArgumentException("下载的文件不存在")}；</li>
 *   <li>其余传输/签名/HTTP 错误 ⇒ {@code WrongArgumentException("MinIO 服务异常")}；</li>
 *   <li>{@link #deleteObject} 例外：厂商 {@code deleteFile} 把异常吞成 {@code false}，
 *       所以这里也**不抛**，失败一律返回 false。</li>
 * </ul>
 */
public interface IFileStorePort {

    /**
     * 上传字节，返回 objectName。
     *
     * <p>厂商 {@code MinioClientUtil.uploadFile:54-79}：objectName = {@code UUID + 扩展名}
     * （扩展名含点，且 {@code "/"} 被替换成 {@code "_"}）；桶不存在时先建桶；
     * Content-Type 用 {@code Files.probeContentType}，探测不到回落 {@code application/octet-stream}；
     * 全程异常统一包成 {@code RuntimeException("上传失败: " + msg)}（⇒ HTTP 500 系统错误）。
     */
    String putObject(byte[] content, String originalFilename);

    /**
     * HEAD object，只取 Content-Type/长度。
     *
     * <p>厂商 {@code FileMinioService.download:158} 的 {@code statObject}，**失败即抛**
     * （对象被外部删掉时下载直接 400）。
     */
    FileObjectStatVO statObject(String objectName);

    /** GET object 整对象（厂商 {@code handleFullDownload:81-86}，响应 200）。 */
    InputStream getObject(String objectName);

    /** GET object 指定区间（厂商 {@code handleRangeRequest:88-127}，响应 206，offset+length）。 */
    InputStream getObject(String objectName, long offset, long length);

    /**
     * DELETE object。先 stat 再删；任何失败返回 false（照抄 {@code FileMinioService.deleteFile:324-335}
     * 把 {@code MinioException | IllegalArgumentException | InvalidKeyException | NoSuchAlgorithmException}
     * 全吞成 false）。
     */
    boolean deleteObject(String objectName);

    /**
     * MinIO 服务端存储信息（厂商走 MinIO **Admin API** 的 {@code getServerInfo()}，
     * 不是 S3；见 {@code FileMinioService.getStorageInfo:404-429}）。
     *
     * <p>厂商整段 catch 住异常并返回**已累积的部分列表**，所以拿不到时实现层返回空列表即可。
     * 本环境类 C：不做在线验证。
     */
    List<MinioStorageVO> serverStorageInfo();
}
