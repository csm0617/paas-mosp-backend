package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.file.adapter.port.IFileStorePort;
import com.csm.paas.core.domain.file.model.valobj.FileObjectStatVO;
import com.csm.paas.core.domain.file.model.valobj.MinioStorageVO;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link IFileStorePort} 的实现：MinIO 走 S3/HTTP 直连（替代厂商的 {@code MinioClientUtil} +
 * {@code MinioClient}/{@code CustomMinioClient}/{@code MinioAdminClient}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/utils/MinioClientUtil.java}
 * 与 {@code service/FileMinioService.java:141-180,273-298,324-335,404-429}。
 *
 * <p><b>类 C：本环境无 MinIO 对端，代码齐全但不做在线验证。</b>
 * 离线仓库无 {@code io.minio:minio} 且不允许改 pom ⇒ 见 {@link MinioS3HttpClient} 的手写 SigV4。
 *
 * <p>配置（照抄厂商 {@code MinioConfig} 的四个键，但本仓不改 application.yml，默认值直接写进注解；
 * 厂商默认值见 {@code recovered/gaop-fdfs/src/main/resources/application.yml:35-39}）：
 * <ul>
 *   <li>{@code gaop.minio.endpoint} 默认 {@code http://paas216:9001}；</li>
 *   <li>{@code gaop.minio.access-key} 默认 {@code admin}；</li>
 *   <li>{@code gaop.minio.secret-key} 默认 {@code 12345678}；</li>
 *   <li>{@code gaop.minio.bucket-name} 默认 {@code gaop}；</li>
 *   <li>{@code gaop.minio.region} 默认 {@code us-east-1}（厂商交给 minio-java 自动探测，本仓手写签名必须显式给）。</li>
 * </ul>
 */
@Slf4j
@Component
public class MinioFileStorePort implements IFileStorePort {

    /** 厂商 {@code application.yml:36} 的 {@code GAOP_MINIO_HOST} 默认值。 */
    @Value("${gaop.minio.endpoint:http://paas216:9001}")
    private String endpoint;

    /** 厂商 {@code GAOP_MINIO_ACCCESSKEY} 默认值（注意厂商键名拼写是 ACCCESSKEY）。 */
    @Value("${gaop.minio.access-key:admin}")
    private String accessKey;

    /** 厂商 {@code GAOP_MINIO_SECRETKEY} 默认值。 */
    @Value("${gaop.minio.secret-key:12345678}")
    private String secretKey;

    /** 厂商 {@code GAOP_MINIO_BUCKET} 默认值。 */
    @Value("${gaop.minio.bucket-name:gaop}")
    private String bucketName;

    /** 手写 SigV4 需要的 region；厂商由 minio-java 自动探测。 */
    @Value("${gaop.minio.region:us-east-1}")
    private String region;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile MinioS3HttpClient client;

    private MinioS3HttpClient client() {
        MinioS3HttpClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = new MinioS3HttpClient(endpoint, accessKey, secretKey, region);
                    client = local;
                }
            }
        }
        return local;
    }

    // ================================================================ 上传

    /** 照抄厂商 {@code MinioClientUtil.uploadFile:54-79}（含最外层把一切包成 RuntimeException）。 */
    @Override
    public String putObject(byte[] content, String originalFilename) {
        try {
            // 厂商：fileExtension 含点号，且 "/" 替换成 "_"（文件名里没有 "." 时 substring 会抛，
            // 这条路径同样被外层 catch 包成"上传失败"，属照抄）
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = UUID.randomUUID() + fileExtension.replaceAll("/", "_");
            if (!client().bucketExists(bucketName)) {
                client().makeBucket(bucketName);
            }
            String contentType = Files.probeContentType(Paths.get(originalFilename));
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            client().putObject(bucketName, objectName, content, contentType);
            return objectName;
        } catch (Exception e) {
            // 厂商 MinioClientUtil 的 catch (Exception e) → RuntimeException("上传失败: " + msg)
            throw new RuntimeException("上传失败: " + e.getMessage(), e);
        }
    }

    // ================================================================ stat / 下载 / 删除

    /** 厂商 {@code FileMinioService.download:158} 的 {@code statObject}（失败即抛，见 {@code handleMinioError:261-266}）。 */
    @Override
    public FileObjectStatVO statObject(String objectName) {
        HttpResponse<Void> response;
        try {
            response = client().headObject(bucketName, objectName);
        } catch (Exception e) {
            log.error("MinIO statObject 异常：{}", objectName, e);
            throw new WrongArgumentException("MinIO 服务异常");
        }
        if (response.statusCode() == 404) {
            // 厂商 handleMinioError：错误信息里含 "not Found"/"not exist"
            throw new WrongArgumentException("下载的文件不存在");
        }
        if (response.statusCode() >= 400) {
            throw new WrongArgumentException("MinIO 服务异常");
        }
        String contentType = response.headers().firstValue("Content-Type")
                .orElse("application/octet-stream");
        long size = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        return FileObjectStatVO.builder().contentType(contentType).size(size).build();
    }

    @Override
    public InputStream getObject(String objectName) {
        return openObject(objectName, null, null);
    }

    @Override
    public InputStream getObject(String objectName, long offset, long length) {
        return openObject(objectName, offset, length);
    }

    private InputStream openObject(String objectName, Long offset, Long length) {
        HttpResponse<InputStream> response;
        try {
            response = client().getObject(bucketName, objectName, offset, length);
        } catch (Exception e) {
            log.error("MinIO GET object 异常：{}", objectName, e);
            throw new WrongArgumentException("MinIO 服务异常");
        }
        if (response.statusCode() == 404) {
            throw new WrongArgumentException("下载的文件不存在");
        }
        if (response.statusCode() >= 400) {
            throw new WrongArgumentException("MinIO 服务异常");
        }
        return response.body();
    }

    /**
     * 照抄厂商 {@code FileMinioService.deleteFile:324-335}：**先 stat 再删**，
     * 且把一切异常吞成 {@code false}——所以对象不存在时返回 false（库行不会被删）。
     */
    @Override
    public boolean deleteObject(String objectName) {
        try {
            if (client().headObject(bucketName, objectName).statusCode() >= 400) {
                return false;
            }
            return client().deleteObject(bucketName, objectName).statusCode() < 400;
        } catch (Exception e) {
            log.error("MinIO 删除对象失败：{}", objectName, e);
            return false;
        }
    }

    // ================================================================ 存储信息（Admin API）

    /**
     * 照抄厂商 {@code FileMinioService.getStorageInfo:404-429}，包括它的<b>三处怪点</b>：
     * <ol>
     *   <li>{@code freeSize} 累加的是 {@code disk.usedspace()}（不是 freespace）；</li>
     *   <li>每轮循环又把累计值除以 1048576（即磁盘数 &gt; 1 时会被反复缩小）；</li>
     *   <li>{@code ip} 取 {@code endpoint.substring(0, indexOf(":"))}，没有冒号就抛
     *       {@code StringIndexOutOfBoundsException}——会被本方法的 catch 吞掉，
     *       于是返回**已累积的部分列表**。</li>
     * </ol>
     */
    @Override
    public List<MinioStorageVO> serverStorageInfo() {
        List<MinioStorageVO> storageList = new ArrayList<>();
        try {
            String json = client().adminInfo();
            if (json == null) {
                return storageList;
            }
            JsonNode root = objectMapper.readTree(json);
            for (JsonNode server : root.path("servers")) {
                MinioStorageVO storage = MinioStorageVO.builder()
                        .totalSize(0.0).freeSize(0.0).percentMemoryLeft(0.0)
                        .status(5)
                        .build();
                String serverEndpoint = server.path("endpoint").asText("");
                storage.setIp(serverEndpoint.substring(0, serverEndpoint.indexOf(":")));
                if ("online".equals(server.path("state").asText())) {
                    storage.setStatus(7);
                }
                storage.setGroup("minio");
                for (JsonNode disk : server.path("disks")) {
                    storage.setTotalSize((storage.getTotalSize() + disk.path("totalspace").asDouble())
                            / 1048576.0);
                    storage.setFreeSize((storage.getFreeSize() + disk.path("usedspace").asDouble())
                            / 1048576.0);
                }
                storage.setPercentMemoryLeft(storage.getTotalSize() == 0.0
                        ? 0.0 : storage.getFreeSize() / storage.getTotalSize());
                storageList.add(storage);
            }
        } catch (Exception e) {
            log.error("获取minio存储信息异常", e);
        }
        return storageList;
    }
}
