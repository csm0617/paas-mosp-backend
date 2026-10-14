package com.csm.paas.core.domain.image.adapter.port;

import com.csm.paas.core.domain.image.model.valobj.ImageItemVO;

import java.util.List;

/**
 * Docker Registry v2 HTTP API 端口（厂商 `controller/third/api/DockerRegistryApi` +
 * `DockerRegistryApiService`，Feign 实现，base url = `docker.registry-host` = `server.host.harbor`）。
 *
 * <p><b>类 C：本环境到不了 registry</b>（`docs/findings/79`：Harbor 在集群内外都不可达），
 * 代码齐全但不做在线验证。网络失败必须**照厂商吞掉**（返回空列表/部分结果），不能抛异常。
 *
 * <p>厂商用 Caffeine 做 30 秒、最多 100 条的 `getImageListByProject` 缓存，
 * 删除镜像后调 {@link #evictImageListCache} 失效（照抄）。
 */
public interface IDockerRegistryPort {

    /**
     * 厂商 `DockerRegistryApiService.getImageListByProject(project)`：
     * `_catalog` 全量分页 → 过滤 `startsWith(project + "/")` → 每仓 `tags/list` → 每 tag `manifests`
     * → `blobs/{config.digest}`；任何一步失败就**跳过**该条。
     * 带 30 秒缓存。
     */
    List<ImageItemVO> getImageListByProject(String project);

    /** 厂商 `DockerRegistryApiService.evictImageListCache`。 */
    void evictImageListCache(String project);

    /**
     * 厂商 `DockerRegistryApi.getImageDigest`：`GET manifests/{tag}` 读响应头
     * `Docker-Content-Digest`；异常/无头 ⇒ **null**（照抄，不抛）。
     */
    String getImageDigest(String imageName, String tag);

    /**
     * 厂商 `DockerRegistryApi.deleteManifest`：
     * `DELETE /v2/{imageName}/manifests/{digest}`，`Accept: application/vnd.docker.distribution.manifest.v2+json`。
     */
    void deleteManifest(String imageName, String digest);

    /**
     * 厂商 `dockerConfig.getRegistryHost().replace("http://","").replace("https://","")`
     * （= `server.host.harbor`，去协议头）。
     * <p>注意与 {@link #getImageListByProject} 内部拼 `repoTags` 用的 `replaceFirst` 不是一回事：
     * 厂商在 `DockerRegistryApiService` 里用 `replaceFirst`，在 `K3sImageServiceImpl`/`DockerService` 里用
     * `replace`（替换全部），两者都照抄。
     */
    String registryHost();
}
