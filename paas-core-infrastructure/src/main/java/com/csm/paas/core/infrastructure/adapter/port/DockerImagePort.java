package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.image.adapter.port.IDockerImagePort;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Docker Engine API 端口实现（替代厂商的 docker-java `DockerClient`）。
 *
 * <p><b>类 C：本环境到不了 docker daemon，代码齐全但不做在线验证。</b>
 * docker daemon 地址来自 `gaop.docker.host`（厂商 `docker.host`）：
 * k8s profile 默认 `unix:///var/run/docker.sock`（`application-k8s.yml:28`），
 * base profile 默认 `tcp://localhost:2375`（`application.yml:77`）。
 *
 * <p>厂商出处：`recovered/gaop-be/.../service/DockerService.java` 与
 * `.../service/impl/K3sImageServiceImpl.java`。路径对照：
 * <ul>
 *   <li>`pullImageCmd("n:t")` → `POST /images/create?fromImage=n&tag=t`；</li>
 *   <li>`tagImageCmd(id, repo, tag)` → `POST /images/{id}/tag?repo=&tag=`；</li>
 *   <li>`saveImageCmd("n:t")` → `GET /images/{n:t}/get`；</li>
 *   <li>`POST /images/load`（`Content-Type: application/x-tar`）→ 响应体文本；</li>
 *   <li>`pushImageCmd(remote)` → `POST /images/{remote}/push`；</li>
 *   <li>`removeImageCmd(t)` → `DELETE /images/{t}`；</li>
 *   <li>`inspectImageCmd(t)` → `GET /images/{t}/json` 的 `Config.Env`。</li>
 * </ul>
 */
@Slf4j
@Component
public class DockerImagePort implements IDockerImagePort {

    /** 厂商 k8s profile 的默认值；本地/base profile 是 `tcp://localhost:2375`。 */
    @Value("${gaop.docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile DockerEngineHttpClient engine;

    private DockerEngineHttpClient engine() {
        DockerEngineHttpClient local = engine;
        if (local == null) {
            synchronized (this) {
                local = engine;
                if (local == null) {
                    local = new DockerEngineHttpClient(dockerHost);
                    engine = local;
                }
            }
        }
        return local;
    }

    @Override
    public void pull(String nameWithRepository, String tag) {
        String path = "/images/create?fromImage=" + encode(nameWithRepository) + "&tag=" + encode(tag);
        DockerEngineHttpClient.DockerResponse response;
        try {
            response = engine().request("POST", path, Map.of(), new byte[0]);
        } catch (RuntimeException e) {
            log.error("pull 失败：{}:{}", nameWithRepository, tag, e);
            throw new WrongArgumentException("docker.pull.image.failed");
        }
        if (response.status() >= 400 || containsError(response.body())) {
            log.error("pull 失败：{}:{} → {}", nameWithRepository, tag, text(response.body()));
            throw new WrongArgumentException("docker.pull.image.failed");
        }
    }

    @Override
    public void tag(String imageId, String imageNameWithRepository, String tag) {
        String path = "/images/" + encode(imageId) + "/tag?repo=" + encode(imageNameWithRepository)
                + "&tag=" + encode(tag);
        DockerEngineHttpClient.DockerResponse response = engine().request("POST", path, Map.of(), new byte[0]);
        if (response.status() >= 400) {
            throw new IllegalStateException("tag 镜像失败：" + imageId + " → " + imageNameWithRepository + ":" + tag
                    + "（HTTP " + response.status() + "）");
        }
    }

    @Override
    public InputStream saveImage(String name, String tag) {
        DockerEngineHttpClient.DockerStreamResponse response =
                engine().stream("GET", "/images/" + encode(name + ":" + tag) + "/get", Map.of());
        if (response.status() >= 400) {
            try {
                response.close();
            } catch (Exception e) {
                log.debug("关闭 save 响应失败", e);
            }
            throw new IllegalStateException("save 镜像失败：" + name + ":" + tag + "（HTTP " + response.status() + "）");
        }
        return response.body();
    }

    @Override
    public String loadImage(InputStream tar) {
        DockerEngineHttpClient.DockerResponse response = engine().requestStreamingBody(
                "POST", "/images/load", Map.of("Content-Type", "application/x-tar"), tar);
        if (response.status() >= 400) {
            throw new WrongArgumentException("docker.push.image.failed");
        }
        // 厂商 `StreamUtils.copyToString(body, UTF_8)`
        return text(response.body());
    }

    @Override
    public void push(String remoteImageId) {
        DockerEngineHttpClient.DockerResponse response =
                engine().request("POST", "/images/" + encode(remoteImageId) + "/push", Map.of(), new byte[0]);
        if (response.status() >= 400 || containsError(response.body())) {
            log.error("push 失败：{} → {}", remoteImageId, text(response.body()));
            throw new WrongArgumentException("docker.push.image.failed");
        }
    }

    @Override
    public void removeImage(String imageTag) {
        try {
            DockerEngineHttpClient.DockerResponse response =
                    engine().request("DELETE", "/images/" + encode(imageTag), Map.of(), new byte[0]);
            if (response.status() == 404) {
                log.error("找不到镜像:{}", imageTag);
            } else if (response.status() >= 400) {
                log.error("删除镜像失败:{} → HTTP {}", imageTag, response.status());
            }
        } catch (RuntimeException e) {
            // 厂商 `deleteRepositories` 里只捕获 NotFoundException 并记日志；这里放宽为全部记日志
            log.error("删除镜像失败:{}", imageTag, e);
        }
    }

    @Override
    public String[] inspectImageEnv(String imageTag) {
        DockerEngineHttpClient.DockerResponse response =
                engine().request("GET", "/images/" + encode(imageTag) + "/json", Map.of(), null);
        if (response.status() == 404) {
            // 厂商：docker-java 的 NotFoundException 直接冒到 500
            throw new IllegalStateException("镜像不存在：" + imageTag);
        }
        if (response.status() >= 400) {
            throw new IllegalStateException("inspect 镜像失败：" + imageTag + "（HTTP " + response.status() + "）");
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode config = root.get("Config");
            if (config == null || config.isNull()) {
                // 厂商：config == null ⇒ 返回空列表
                return new String[0];
            }
            JsonNode env = config.get("Env");
            if (env == null || !env.isArray()) {
                return new String[0];
            }
            String[] result = new String[env.size()];
            for (int i = 0; i < env.size(); i++) {
                result[i] = env.get(i).asText();
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("解析 inspect 结果失败：" + imageTag, e);
        }
    }

    /** 厂商在 pull/push 的流里靠 `onError` 判失败；Docker 的 JSON 流里失败会带 `"error"` 键。 */
    private static boolean containsError(byte[] body) {
        return body != null && text(body).contains("\"error\"");
    }

    private static String text(byte[] body) {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
