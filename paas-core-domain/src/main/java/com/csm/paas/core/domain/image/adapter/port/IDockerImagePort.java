package com.csm.paas.core.domain.image.adapter.port;

import java.io.InputStream;

/**
 * Docker Engine API 端口（厂商用 docker-java 的 `DockerClient`，`docker.host` 默认
 * k8s profile 的 `unix:///var/run/docker.sock`，base profile 是 `tcp://localhost:2375`）。
 *
 * <p><b>类 C：本环境到不了 docker daemon</b>，代码齐全但不做在线验证。
 *
 * <p>厂商的 `pull`/`push` 是**回调式异步**（`ResultCallback`），
 * `download` 依赖 "pull 的 onComplete 里才 tag+save" 的次序；本端口把 pull/push 收敛成**阻塞**调用，
 * 从而在 trigger 里保持与厂商相同的执行次序（先 pull 成功，再 tag，再 save）。
 */
public interface IDockerImagePort {

    /**
     * 厂商 `DockerService.pull(name, tag, cb)`：`pullImageCmd(nameWithRepository + ":" + tag)`，
     * 失败时厂商在 onError 里抛 `docker.pull.image.failed`。
     */
    void pull(String nameWithRepository, String tag);

    /** 厂商 `DockerService.tag(imageId, imageNameWithRepository, tag)`。 */
    void tag(String imageId, String imageNameWithRepository, String tag);

    /** 厂商 `DockerService.downloadAsTar(name, tag)`：`saveImageCmd("name:tag").exec()` 的原始 tar 流。 */
    InputStream saveImage(String name, String tag);

    /**
     * 厂商 `DockerService.uploadImage` 的 `/images/load`：把 tar 流 POST 上去，
     * **返回响应体文本**（厂商 `StreamUtils.copyToString(body, UTF_8)`，用于正则解析 `Loaded image: x:y`）。
     */
    String loadImage(InputStream tar);

    /** 厂商 `dockerClient.pushImageCmd(remoteImageId).exec(callback)`：阻塞到推完。 */
    void push(String remoteImageId);

    /** 厂商 `dockerClient.removeImageCmd(imageTag).exec()`。 */
    void removeImage(String imageTag);

    /**
     * 厂商 `K3sImageServiceImpl.getImageTagEnvList`：`inspectImageCmd(imageTag).exec().getConfig().getEnv()`。
     * <p>配置不存在 ⇒ 厂商返回**空列表**；镜像不存在 ⇒ 厂商的 `NotFoundException` 直接冒到 500。
     */
    String[] inspectImageEnv(String imageTag);
}
