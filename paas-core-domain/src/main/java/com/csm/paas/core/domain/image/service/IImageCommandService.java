package com.csm.paas.core.domain.image.service;

import java.util.List;

/**
 * 镜像管理写入（厂商 `K3sImageServiceImpl` 的写路径）。
 *
 * <p>规则与出处：
 * <ul>
 *   <li>{@link #createProject} ← `K3sImageServiceImpl.createProject:174-185`（纯库 `g_group`）；</li>
 *   <li>{@link #deleteProjectThrow} ← `deleteProjectThrow:724-733`（纯库）；</li>
 *   <li>{@link #updateImageDescription} ← `updateImageDesc:460-485`（纯库 `g_setting.image.desc`）；</li>
 *   <li>{@link #deleteRepositories} / {@link #deleteImageByTag} ← `deleteRepositories:533-574` /
 *       `deleteImageByTag:576-607`（**类 C**：依赖 Registry 与 docker daemon）。</li>
 * </ul>
 */
public interface IImageCommandService {

    /**
     * 新建仓库组（厂商 `createProject`）：`name` **LIKE** 查重（厂商传的是 `isLikeWhenPresent(name)`，
     * 不带通配符 ⇒ 等价于等值，但名字里若含 `%`/`_` 会被当通配符——照抄）；重复 ⇒
     * 抛 `WrongArgumentException("image.duplicate.repository.group", name)`。
     */
    boolean createProject(String name);

    /**
     * 删除仓库组（厂商 `deleteProjectThrow`）：主键不存在 ⇒
     * 抛 `WrongArgumentException("image.group.not.found")`。
     */
    boolean deleteProjectThrow(Integer projectId);

    /**
     * 改镜像描述（厂商 `updateImageDesc`）：把 `repoId -> description` 存进 `g_setting.image.desc`
     * 的 JSON 对象里；行不存在则插入，已存在则更新（**写的是 `created_at`**，厂商怪点）。
     * <p>长度校验（>36 ⇒ `image.description.illegal`）在 trigger 层，与厂商控制器一致。
     */
    boolean updateImageDescription(String repoId, String description);

    /**
     * 批量删除镜像（厂商 `deleteRepositories`，`DELETE /v1/image/all_image`）：
     * 对每个 `image` 先取 tag 列表，再逐个 `docker rmi` + `DELETE manifest`，最后清 Registry 缓存。
     * <p>镜像名不含 `/` ⇒ 抛 `WrongArgumentException("镜像名错误")`。
     */
    void deleteRepositories(List<String> imageNames);

    /**
     * 按 tag 删镜像（厂商 `deleteImageByTag`，`DELETE /v1/image`）：
     * 先 `DELETE manifest`，再**新起一个线程**执行 `docker rmi`（厂商就是 `new Thread(...).start()`，
     * fire-and-forget）；最后清 Registry 缓存。始终返回 true。
     */
    boolean deleteImageByTag(String name, List<String> tagList);
}
