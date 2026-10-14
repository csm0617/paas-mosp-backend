package com.csm.paas.core.domain.image.service;

import com.csm.paas.core.domain.image.model.entity.ImageLogListEntity;
import com.csm.paas.core.domain.image.model.entity.RepositoryListEntity;
import com.csm.paas.core.domain.image.model.entity.TagListEntity;
import com.csm.paas.core.domain.image.model.valobj.ExposedPortItemVO;
import com.csm.paas.core.domain.image.model.valobj.ImageEnvInfoVO;
import com.csm.paas.core.domain.image.model.valobj.ImageInfoVO;
import com.csm.paas.core.domain.image.model.valobj.ImageItemVO;
import com.csm.paas.core.domain.image.model.valobj.TagItemVO;

import java.util.List;

/**
 * 镜像管理的其余查询（厂商 `K3sImageServiceImpl` + `DockerService`）。
 *
 * <p>与 {@link IImageQueryService}（`projectList`，纯库读取）分开：这些端点依赖
 * **Docker Registry / docker daemon / SSH / Deployment**，都是本环境不可达的外部设施 ⇒ **类 C**：
 * 代码照抄齐全但不做在线验证（`docs/findings/79`、`81`）。
 *
 * <p>所有方法的分页参数保持厂商控制器透传的**原始 Integer**（`null`/`0`/负数都会被
 * `PageUtils` 按厂商 `SqlUtils` 语义归一化），所以签名收 `Integer`。
 */
public interface IImageCatalogQueryService {

    /**
     * 仓库列表（厂商 `K3sImageServiceImpl.getRepositories:242-374`）。
     *
     * <p>数据来自 Registry（`DockerRegistryApiService.getImageListByProject`），再叠加
     * `g_setting.image.desc` 的描述、`g_image_log` 里最近一次成功上传的用户名、以及 Deployment 的关联关系
     * （pod 模板注解 `gaop/serviceLabel`）。
     * <p>`total` = 去重后的仓库数；`list` 先按 `creationTime`（**永远为 null**）排序
     * （`nullsLast(reverseOrder())` ⇒ 稳定排序，等于保持 HashMap 迭代序）再 `skip/limit`。
     */
    RepositoryListEntity queryRepositories(Integer page, Integer size, String keyword, String storage);

    /**
     * 镜像的 tag 列表（厂商 `K3sImageServiceImpl.getImageTagList:376-389` +
     * `getImageTagListByImageList:391-458`）。
     * <p>`imageName` 不含 `/` ⇒ 抛 `WrongArgumentException("镜像名错误")`
     * （厂商拿中文字符串当 i18n 键，解析不到就原样返回，见 `MessageResolver`）。
     * <p>`total` = 过滤后的条数，`list` 按 `pushTime` **倒序**。
     */
    TagListEntity queryImageTagList(Integer page, Integer size, String keyword, String imageName);

    /**
     * 厂商 `AbstractImageService.getImageTagListByImageList` 是**公开**方法，
     * `deleteRepositories`（`DELETE /v1/image/all_image`）会先按 project 取一次镜像列表，
     * 再对每个镜像复用同一个列表来算 tag（避免重复请求 Registry）。
     * <p>规则与 {@link #queryImageTagList} 的后半段完全相同。
     */
    TagListEntity queryImageTagListByImageList(Integer page, Integer size, String keyword, String imageName,
                                               List<ImageItemVO> imageList);

    /**
     * 所有仓库组下的镜像（厂商 `K3sImageServiceImpl.getAllProjectImageList:487-502`）：
     * 遍历 `g_group`（过滤 `system_containers`），逐个调 {@link #queryRepositories}，
     * 把各自**分页后**的 list 拼接，`total` = 拼接后的条数（所以 `total` 与真实总数不同）。
     */
    RepositoryListEntity queryAllProjectImageList(Integer page, Integer size, String keyword);

    /**
     * 镜像上传日志（厂商 `DockerService.getImageList:172-197`）。
     * <p>`keyword` 先去空格；分页，`total` = 满足条件的**总行数**。
     * <p><b>副作用</b>：`status == 2` 且 `created_at` 距今超过 1800000ms（30 分钟）的行会被改成
     * `status = 0`、`log` 追加一行超时说明并写库。
     */
    ImageLogListEntity queryImageLogList(Integer page, Integer size, String keyword);

    /**
     * 镜像声明的端口（厂商 `K3sImageServiceImpl.exposedPortList:609-696`）：
     * SSH 到节点执行 `docker history <registryHost>/<name>:<tag> | awk … | tr '\n' ','`，
     * 再按逗号/斜杠解析。**连不上 SSH 或命令失败 ⇒ 返回空列表**（厂商不报错）。
     */
    List<ExposedPortItemVO> queryExposedPorts(String name, String tag);

    /**
     * 镜像的 ENV（厂商 `K3sImageServiceImpl.getImageTagEnvList:744-757`）：
     * `docker inspect` 的 `Config.Env`，按 `=` 切成 `{name, value}`。
     * <p>厂商对每个 env 直接取 `split("=")[1]`——**没有 `=` 的 env 会抛 ArrayIndexOutOfBounds**，照抄。
     */
    List<ImageEnvInfoVO> queryImageEnv(String name, String tag);

    /**
     * 服务关联的镜像（厂商 `ImageService.getServiceRelationImage:644-740`）：
     * 过滤 Deployment（排除带 `ingress-lb` 标签、只留 `system/appType=business`、排除注解
     * `displayName=gaop`、排除标签 `system/appName=gaop`），取第一个容器镜像解析出
     * `group/imageName:tag`，再用 {@link #existImageTag} 过滤，最后放进 `LinkedHashSet` 去重。
     */
    List<ImageInfoVO> queryServiceRelationImage();

    /**
     * 判断仓库是否存在 tag（厂商 `K3sImageServiceImpl.existRepositories:698-711`）：
     * `name` 不以 `<projectName>/` 开头时补前缀，再查 tag 列表；**为空就抛**
     * `WrongArgumentException("image.not.found.upload.first")`。
     */
    List<TagItemVO> existRepositories(String name, String projectName);

    /**
     * 判断镜像是否有该 tag（厂商 `K3sImageServiceImpl.existImageTag:735-742`）：
     * 用 `keyword = tag` 调 tag 列表，非空即 true。
     */
    boolean existImageTag(String imageName, String tag);
}
