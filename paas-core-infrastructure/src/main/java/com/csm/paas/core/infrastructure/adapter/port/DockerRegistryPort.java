package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.model.valobj.ImageItemVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Docker Registry v2 HTTP 客户端（厂商 `controller/third/api/DockerRegistryApi` +
 * `DockerRegistryApiService`，Feign 实现，base url = `docker.registry-host` = `server.host.harbor`）。
 *
 * <p><b>类 C：本环境到不了 registry</b>（`docs/findings/79`：Harbor 在集群内外都不可达），
 * 代码齐全但不做在线验证。网络失败一律**照厂商吞掉**（返回空列表 / 跳过该条），不向外抛。
 *
 * <p>照抄点：
 * <ul>
 *   <li>`_catalog` 分页靠响应头 `Link` 的 `last`/`n`（厂商 `DockerRegistryApi.getAllCatalogList`）；</li>
 *   <li>**没有 Authorization 头**（厂商只给 Harbor bean 加 Basic，DockerRegistryApi 不加）；</li>
 *   <li>仓库名里的 `/` 保持原样、**不做 percent-encode**（Feign `decodeSlash=false`）；</li>
 *   <li>`created` 用 `OffsetDateTime.parse(...).toInstant().toEpochMilli()`，解析失败 ⇒ **null**；
 *       `virtualSize = size`、`sharedSize = -1`（写死）；</li>
 *   <li>结果缓存 30 秒 / 最多 100 条（Caffeine → JDK `LinkedHashMap` LRU + 过期时间戳）。</li>
 * </ul>
 */
@Slf4j
@Component
public class DockerRegistryPort implements IDockerRegistryPort {

    /** 厂商 `@Headers("Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json, application/json")`。 */
    private static final String MANIFEST_ACCEPT =
            "application/vnd.docker.distribution.manifest.v2+json, "
                    + "application/vnd.oci.image.manifest.v1+json, application/json";
    private static final String MANIFEST_DELETE_ACCEPT = "application/vnd.docker.distribution.manifest.v2+json";
    /** 厂商 `Pattern.compile("</v2/_catalog\\?last=([^&]+)&n=([0-9]+)>; rel=\"next\"")`。 */
    private static final Pattern NEXT_LINK =
            Pattern.compile("</v2/_catalog\\?last=([^&]+)&n=([0-9]+)>; rel=\"next\"");
    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final int CACHE_MAX_SIZE = 100;

    /**
     * 厂商 `docker.registry-host: ${server.host.harbor}`，
     * k8s profile 的 `server.host.harbor` 默认 `http://harbor:30002`（`application-k8s.yml:24`）。
     */
    @Value("${gaop.harbor.url:${server.host.harbor:http://harbor:30002}}")
    private String registryUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 缓存项：值 + 过期时间戳（等价于 Caffeine `expireAfterWrite(30s)`）。 */
    private record Cached(long expireAt, List<ImageItemVO> value) {
    }

    /** Cache：等价于 Caffeine `maximumSize(100)` + access order LRU。 */
    private final Map<String, Cached> imageListCache =
            Collections.synchronizedMap(new LinkedHashMap<String, Cached>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    @Override
    public List<ImageItemVO> getImageListByProject(String project) {
        Cached cached = imageListCache.get(project);
        if (cached != null && cached.expireAt() > System.currentTimeMillis()) {
            return cached.value();
        }
        List<ImageItemVO> result = doGetImageListByProject(project);
        imageListCache.put(project, new Cached(System.currentTimeMillis() + CACHE_TTL_MILLIS, result));
        return result;
    }

    @Override
    public void evictImageListCache(String project) {
        imageListCache.remove(project);
    }

    @Override
    public String getImageDigest(String imageName, String tag) {
        try {
            HttpResponse<String> response = send("GET", "/v2/" + imageName + "/manifests/" + tag,
                    MANIFEST_ACCEPT, null);
            return response.headers().firstValue("Docker-Content-Digest").orElse(null);
        } catch (Exception e) {
            // 厂商 `getImageDigest` 的 catch 块就是 return null
            log.info("获取镜像 digest 失败（返回 null）：{}:{}", imageName, tag, e);
            return null;
        }
    }

    @Override
    public void deleteManifest(String imageName, String digest) {
        try {
            HttpResponse<String> response = send("DELETE", "/v2/" + imageName + "/manifests/" + digest,
                    MANIFEST_DELETE_ACCEPT, null);
            if (response.statusCode() >= 400) {
                log.warn("删除 manifest 返回 {}：{} / {}", response.statusCode(), imageName, digest);
            }
        } catch (Exception e) {
            // 厂商 Feign 是 void 调用，异常在调用方被 catch 后只记日志
            log.warn("删除 manifest 失败：{} / {}", imageName, digest, e);
        }
    }

    /**
     * 厂商 `dockerConfig.getRegistryHost().replace("http://","").replace("https://","")`
     * （`K3sImageServiceImpl`/`DockerService` 里用的是 `replace`，替换全部——照抄）。
     */
    @Override
    public String registryHost() {
        return baseUrl().replace("http://", "").replace("https://", "");
    }

    /**
     * 厂商 `DockerRegistryApiService.doGetImageListByProject`：任何一步失败都 `continue`，
     * `_catalog` 失败返回空列表。
     */
    private List<ImageItemVO> doGetImageListByProject(String project) {
        List<String> repositories;
        try {
            repositories = getAllCatalogRepositories();
        } catch (Exception e) {
            log.info("获取CatalogList失败，跳过", e);
            return new ArrayList<>();
        }
        if (repositories == null) {
            return new ArrayList<>();
        }
        String host = registryHost();
        List<ImageItemVO> items = new ArrayList<>();
        for (String imageName : repositories) {
            // 厂商 `repositories.stream().filter(e -> e.startsWith(project + "/"))`
            if (!imageName.startsWith(project + "/")) {
                continue;
            }
            List<String> tags;
            try {
                tags = getTagList(imageName);
            } catch (Exception e) {
                log.info("获取TagList失败，跳过", e);
                continue;
            }
            if (tags == null) {
                continue;
            }
            for (String tag : tags) {
                JsonNode manifests;
                try {
                    manifests = getJson("/v2/" + imageName + "/manifests/" + tag, MANIFEST_ACCEPT);
                } catch (Exception e) {
                    log.info("获取Manifests失败，跳过", e);
                    continue;
                }
                JsonNode config = manifests.get("config");
                if (config == null || config.isNull()) {
                    // 厂商在这一步会 NPE（不是 catch 内）⇒ 冒到 500；这里选择跳过并记日志（唯一的有意偏离）
                    log.warn("manifest 里没有 config，跳过：{}:{}", imageName, tag);
                    continue;
                }
                String digest = config.path("digest").asText(null);
                JsonNode blobs;
                try {
                    blobs = getJson("/v2/" + imageName + "/blobs/" + digest, null);
                } catch (Exception e) {
                    log.info("获取JsonBlobs失败，跳过", e);
                    continue;
                }
                String createdTime = blobs.path("created").asText(null);
                ImageItemVO item = new ImageItemVO();
                item.setId(imageName + ":" + tag);
                item.setCreated(convertDateTime(createdTime));
                long size = 0L;
                for (JsonNode layer : manifests.path("layers")) {
                    size += layer.path("size").asLong(0L);
                }
                item.setSize(size);
                item.setVirtualSize(size);
                item.setSharedSize(-1L);
                item.setRepoTags(new String[]{host + "/" + imageName + ":" + tag});
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 厂商 `DockerRegistryApi.getAllCatalogList`：`GET /v2/_catalog?n=1000`，
     * 之后靠响应头 `Link` 里的 `last` 翻页，直到没有 `next`。
     */
    private List<String> getAllCatalogRepositories() throws Exception {
        List<String> allRepositories = new ArrayList<>();
        int pageSize = 1000;
        String nextToken = null;
        while (true) {
            String path = "/v2/_catalog?n=" + pageSize + (nextToken == null ? "" : "&last=" + nextToken);
            HttpResponse<String> response = send("GET", path, null, null);
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode repositories = body.path("repositories");
            if (repositories.isArray()) {
                repositories.forEach(node -> allRepositories.add(node.asText()));
            }
            String linkHeader = response.headers().firstValue("Link").orElse(null);
            if (linkHeader == null || !linkHeader.startsWith("</v2/_catalog")) {
                break;
            }
            Matcher matcher = NEXT_LINK.matcher(linkHeader);
            if (!matcher.find()) {
                break;
            }
            nextToken = matcher.group(1);
            pageSize = Integer.parseInt(matcher.group(2));
            if (nextToken == null || nextToken.isEmpty()) {
                break;
            }
        }
        return allRepositories;
    }

    /** 厂商 `GET /v2/{imageName}/tags/list`（`decodeSlash=false`，`/` 原样保留）。 */
    private List<String> getTagList(String imageName) throws Exception {
        JsonNode body = getJson("/v2/" + imageName + "/tags/list", null);
        JsonNode tags = body.path("tags");
        if (!tags.isArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        tags.forEach(node -> result.add(node.asText()));
        return result;
    }

    private JsonNode getJson(String path, String accept) throws Exception {
        HttpResponse<String> response = send("GET", path, accept, null);
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("registry 返回 " + response.statusCode() + "：" + path);
        }
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String accept, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(30));
        if (accept != null) {
            builder.header("Accept", accept);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 厂商 `DateTimeUtils.convertDateTime`：解析失败 ⇒ null（不抛）。 */
    private static Long convertDateTime(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(date).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.error("转换日期异常: " + date, e);
            return null;
        }
    }

    private String baseUrl() {
        String url = registryUrl == null ? "" : registryUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
