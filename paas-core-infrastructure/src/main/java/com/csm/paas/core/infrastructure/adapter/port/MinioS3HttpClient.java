package com.csm.paas.core.infrastructure.adapter.port;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 最小 S3 客户端（替代厂商的 {@code io.minio.MinioClient}），只实现本批端点用到的 6 个操作。
 *
 * <p><b>类 C：本环境无 MinIO 对端，代码齐全但不做在线验证。</b>
 * 离线 Maven 仓库里没有 {@code io.minio:minio}，且不允许改 pom，所以改用 JDK 的
 * {@link HttpClient} + 手写 <b>AWS Signature Version 4</b> 直连 MinIO 的 S3 接口。
 *
 * <p>为什么手写签名：{@code io.minio.MinioClient} 的便利方法（{@code putObject}/
 * {@code statObject}/{@code getObject}）背后就是标准 S3 REST + SigV4，
 * 厂商 {@code MinioClientUtil} 也只用到了"PUT 对象 / HEAD 对象 / GET 对象（可带 Range）/ DELETE 对象 /
 * 桶是否存在 / 建桶"这几件直连 HTTP 就能表达的事，没有用到 multipart 之外的定制行为。
 *
 * <p>签名要点（与 AWS SigV4 规范一致，vendor 侧由 minio-java 完成，无从对照）：
 * <ul>
 *   <li>路径式寻址（path-style）：{@code /{bucket}/{key}}，key 逐段 URI 编码、{@code /} 不编码；
 *       桶级操作路径补尾斜杠（{@code /{bucket}/}）；</li>
 *   <li>只签 {@code host} / {@code x-amz-content-sha256} / {@code x-amz-date} 三个头，
 *       {@code Range} 与 {@code Content-Type} 不参与签名（规范允许）；</li>
 *   <li>service 固定 {@code s3}，region 由 {@code gaop.minio.region} 给（默认 {@code us-east-1}）。</li>
 * </ul>
 */
@Slf4j
final class MinioS3HttpClient {

    /** 空 body 的 SHA-256（GET/HEAD/DELETE 用）。 */
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String scheme;
    private final String authority;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final HttpClient httpClient;

    MinioS3HttpClient(String endpoint, String accessKey, String secretKey, String region) {
        String normalized = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        URI uri = URI.create(normalized);
        this.scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        this.authority = uri.getRawAuthority();
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region == null || region.isBlank() ? "us-east-1" : region;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ================================================================ 桶

    /** {@code HEAD /{bucket}/}：2xx ⇒ true，404 ⇒ false，其余抛错。 */
    boolean bucketExists(String bucket) {
        int code = execute("HEAD", bucketPath(bucket), null, null).statusCode();
        if (code == 404) {
            return false;
        }
        if (code >= 400) {
            throw new IllegalStateException("HEAD bucket 失败, HTTP " + code);
        }
        return true;
    }

    /** {@code PUT /{bucket}/}。 */
    void makeBucket(String bucket) {
        int code = execute("PUT", bucketPath(bucket), null, null).statusCode();
        if (code >= 400) {
            throw new IllegalStateException("建桶失败, HTTP " + code);
        }
    }

    // ================================================================ 对象

    /** {@code PUT /{bucket}/{key}}。 */
    void putObject(String bucket, String objectName, byte[] content, String contentType) {
        int code = execute("PUT", objectPath(bucket, objectName), contentType, content).statusCode();
        if (code >= 400) {
            throw new IllegalStateException("PUT object 失败, HTTP " + code);
        }
    }

    /** {@code HEAD /{bucket}/{key}}：由调用方看状态码（404 ⇒ 对象不存在）。 */
    HttpResponse<Void> headObject(String bucket, String objectName) {
        return execute("HEAD", objectPath(bucket, objectName), null, null);
    }

    /**
     * {@code GET /{bucket}/{key}}，{@code offset}/{@code length} 非空时带
     * {@code Range: bytes=<offset>-<offset+length-1>}（照抄厂商 {@code GetObjectArgs.offset/length}）。
     *
     * <p>返回原始响应，由调用方判状态码（404 要走"下载的文件不存在"分支）。
     */
    HttpResponse<java.io.InputStream> getObject(String bucket, String objectName,
                                               Long offset, Long length) {
        String range = (offset == null || length == null)
                ? null : "bytes=" + offset + "-" + (offset + length - 1);
        return executeStream("GET", objectPath(bucket, objectName), range);
    }

    /** {@code DELETE /{bucket}/{key}}。 */
    HttpResponse<Void> deleteObject(String bucket, String objectName) {
        return execute("DELETE", objectPath(bucket, objectName), null, null);
    }

    // ================================================================ MinIO Admin API

    /**
     * {@code GET /minio/admin/v3/info}（厂商 {@code MinioAdminClient.getServerInfo()} 背后的接口，
     * **不是 S3**）：成功返回 JSON 原文，任何失败（含响应被 MinIO 加密导致解析不了）返回 null。
     */
    String adminInfo() {
        try {
            HttpResponse<String> response = executeString("GET", "/minio/admin/v3/info");
            if (response.statusCode() >= 400) {
                log.warn("获取 MinIO admin info 失败, HTTP {}", response.statusCode());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            log.warn("获取 MinIO admin info 异常", e);
            return null;
        }
    }

    // ================================================================ 请求构造与签名

    private String bucketPath(String bucket) {
        return "/" + uriEncode(bucket, false) + "/";
    }

    private String objectPath(String bucket, String objectName) {
        return "/" + uriEncode(bucket, false) + "/" + uriEncode(objectName, true);
    }

    private HttpResponse<Void> execute(String method, String path, String contentType, byte[] body) {
        try {
            return httpClient.send(buildRequest(method, path, contentType, body),
                    HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("S3 请求被中断", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("S3 请求 IO 失败: " + e.getMessage(), e);
        }
    }

    private HttpResponse<java.io.InputStream> executeStream(String method, String path, String range) {
        try {
            return httpClient.send(buildRequest(method, path, null, null, range),
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("S3 请求被中断", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("S3 请求 IO 失败: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> executeString(String method, String path) {
        try {
            return httpClient.send(buildRequest(method, path, null, null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("S3 请求被中断", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("S3 请求 IO 失败: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildRequest(String method, String path, String contentType, byte[] body) {
        return buildRequest(method, path, contentType, body, null);
    }

    private HttpRequest buildRequest(String method, String path, String contentType, byte[] body,
                                     String range) {
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = sha256Hex(payload);
        String amzDate = AMZ_DATE.format(Instant.now());
        String dateStamp = amzDate.substring(0, 8);

        String canonicalHeaders = "host:" + authority + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        // CanonicalQueryString 恒为空：本批操作用请求头表达 Range，不用 query 参数
        String canonicalRequest = method + "\n" + path + "\n" + "\n"
                + canonicalHeaders + signedHeaders + "\n" + payloadHash;

        String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = hex(hmacSha256(signingKey(dateStamp), stringToSign));
        String authorization = ALGORITHM + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(scheme + "://" + authority + path))
                .timeout(Duration.ofMinutes(5))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if (range != null) {
            builder.header("Range", range);
        }
        switch (method) {
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload));
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofByteArray(payload));
            case "DELETE" -> builder.DELETE();
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            default -> builder.method("GET", HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private byte[] signingKey(String dateStamp) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        return hmacSha256(kService, "aws4_request");
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        if (bytes.length == 0) {
            return EMPTY_SHA256;
        }
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** SigV4 的 URI 编码：非保留字符原样，其余 {@code %XX}；{@code /} 视 {@code encodeSlash} 决定。 */
    private static String uriEncode(String input, boolean encodeSlash) {
        StringBuilder result = new StringBuilder();
        for (byte bb : input.getBytes(StandardCharsets.UTF_8)) {
            int b = bb & 0xFF;
            char c = (char) b;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                result.append(c);
            } else if (c == '/' && !encodeSlash) {
                result.append('/');
            } else {
                result.append('%').append(String.format(Locale.ROOT, "%02X", b));
            }
        }
        return result.toString();
    }
}
