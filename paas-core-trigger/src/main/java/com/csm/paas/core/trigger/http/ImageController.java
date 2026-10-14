package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ImageUpdateDescriptionDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.image.adapter.port.IDockerImagePort;
import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.model.entity.ImageLogListEntity;
import com.csm.paas.core.domain.image.model.entity.RepositoryListEntity;
import com.csm.paas.core.domain.image.model.entity.TagListEntity;
import com.csm.paas.core.domain.image.model.valobj.TagItemVO;
import com.csm.paas.core.domain.image.service.IImageCatalogQueryService;
import com.csm.paas.core.domain.image.service.IImageCommandService;
import com.csm.paas.core.domain.image.service.IImageQueryService;
import com.csm.paas.core.trigger.assembler.ImageEnvAssembler;
import com.csm.paas.core.trigger.assembler.ImageExposedPortAssembler;
import com.csm.paas.core.trigger.assembler.ImageInfoAssembler;
import com.csm.paas.core.trigger.assembler.ImageLogAssembler;
import com.csm.paas.core.trigger.assembler.ImageProjectAssembler;
import com.csm.paas.core.trigger.assembler.ImageRepositoryAssembler;
import com.csm.paas.core.trigger.assembler.ImageTagAssembler;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 厂商 `/v1/image/**`：镜像管理（`recovered/gaop-be/.../controller/ImageController.java`，逐行照抄）。
 *
 * <p><b>实际生效的实现是 `K3sImageServiceImpl`</b>——`NodeVersionServiceImpl.getImageServiceInstance()`
 * 无条件返回它（`docs/findings/79`）。数据源 = `g_group`/`g_image_log`/`g_setting` + Docker Registry
 * + docker daemon + SSH，**不是 Harbor**。
 *
 * <p>厂商 `AbstractImageService` 有两个实现，本类只照抄 k3s 那个（K8s/Harbor 变体在本环境不生效）。
 *
 * <p><b>类 C 标注</b>：`repositories`/`tags`/`tagInfo`/`exist`/`existTag`/`imageList`/`all_image`/`download`/
 * `image_env`/`expose`/`serviceRelationImage` 在参考环境都到不了对端（registry/docker daemon/SSH），
 * 代码齐全但不做在线验证（`docs/findings/79`、`81`）；只有 `projectList`/`project`/`imageLog`/
 * `PUT /v1/image` 是纯库路径。
 *
 * <p>厂商在这些端点上还打了 `@AuditLog(...)`；paas-core 目前没有审计落库链路（见 `docs/deviations.md`），
 * 因此这里只在方法注释里保留出处，不落审计（与既有 Controller 的处理一致）。
 *
 * <p><b>未做</b>：`POST /v1/image/upload` 与 `POST /v1/image/clean` **不属于 gaop-be**——
 * 它们在独立服务 `recovered/gaop-image-uploader/.../controller/ImageController.java:101,161`
 * （Redis + 上传进度 websocket + docker load/push，端口 18080）。
 */
@Slf4j
@RestController
public class ImageController {

    /** 厂商 `ImageController.REQUEST_TIMEOUT = Duration.ofMinutes(5).toMillis()`。 */
    private static final long REQUEST_TIMEOUT = 5 * 60 * 1000L;

    @Resource
    private IImageQueryService imageQueryService;

    @Resource
    private IImageCatalogQueryService imageCatalogQueryService;

    @Resource
    private IImageCommandService imageCommandService;

    @Resource
    private IDockerImagePort dockerImagePort;

    @Resource
    private IDockerRegistryPort dockerRegistryPort;

    // ------------------------------------------------------------------ 项目列表（纯库，已对拉一致）

    /**
     * 厂商 `GET /v1/image/projectList`：项目列表 `{list,total}`（键名 snake_case，见 `ImageProjectAssembler`）。
     * <p>**注意它在表为空时会写库**（创建 3 个内置项目），这是厂商行为。
     */
    @GetMapping("/v1/image/projectList")
    public ResponseDTO<Map<String, Object>> projectList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        var result = imageQueryService.queryProjectList(
                PageUtils.normalizePage(page), PageUtils.normalizeSize(size), keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result.getList().stream().map(ImageProjectAssembler::toItem).toList());
        data.put("total", result.getTotal());
        return ResponseDTO.ok(data);
    }

    // ------------------------------------------------------------------ 仓库 / tag

    /**
     * 厂商 `GET /v1/image/repositories?page&size&keyword&storage`（`storage` **必填**）。
     * <p>`keyword` 超过 36 ⇒ `image.invalid.image.name.length`（400）。
     * <p>缺 `storage` 时 Spring 抛 `MissingServletRequestParameterException` → 全局兜底
     * 500 + `{"msg":"系统错误","code":-1}`（与厂商实测样本 `v1_image_repositories.json` 一致）。
     * <p><b>类 C</b>：依赖 Registry。
     */
    @GetMapping("/v1/image/repositories")
    public ResponseDTO<Map<String, Object>> getRepositories(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam String storage) {
        if (keyword != null && keyword.length() > 36) {
            throw new WrongArgumentException("image.invalid.image.name.length");
        }
        RepositoryListEntity repositoryList = imageCatalogQueryService.queryRepositories(page, size, keyword, storage);
        return ResponseDTO.ok(repositoryList(repositoryList));
    }

    /**
     * 厂商 `GET /v1/image/tags?name=`：该镜像的**全部** tag（内部固定 `page=1,size=Integer.MAX_VALUE`）。
     * <p><b>类 C</b>：依赖 Registry。
     */
    @GetMapping("/v1/image/tags")
    public ResponseDTO<List<Map<String, Object>>> imageTags(@RequestParam String name) {
        TagListEntity imageTagList = imageCatalogQueryService.queryImageTagList(1, Integer.MAX_VALUE, null, name);
        return ResponseDTO.ok(imageTagList.getList().stream().map(ImageTagAssembler::toItem).toList());
    }

    /**
     * 厂商 `GET /v1/image/tagInfo?imageName&page&size&keyword`：`{list,total}`。
     * <p>`imageName` 不含 `/` ⇒ 400 `镜像名错误`（厂商拿中文当 i18n 键）。<b>类 C</b>：依赖 Registry。
     */
    @GetMapping("/v1/image/tagInfo")
    public ResponseDTO<Map<String, Object>> getImageTags(
            @RequestParam String imageName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        TagListEntity imageTagList = imageCatalogQueryService.queryImageTagList(page, size, keyword, imageName);
        return ResponseDTO.ok(tagList(imageTagList));
    }

    /**
     * 厂商 `GET /v1/image/imageList?page&size&keyword`：所有仓库组的镜像拼接（**每组各自分页**）。
     * <p><b>类 C</b>：依赖 Registry。
     */
    @GetMapping("/v1/image/imageList")
    public ResponseDTO<Map<String, Object>> getAllProjectImageList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        RepositoryListEntity imageList = imageCatalogQueryService.queryAllProjectImageList(page, size, keyword);
        return ResponseDTO.ok(repositoryList(imageList));
    }

    /**
     * 厂商 `GET /v1/image/exist?name&projectName`：仓库是否存在（不存在 ⇒ 400 `image.not.found.upload.first`）。
     * <p><b>类 C</b>：依赖 Registry。
     */
    @GetMapping("/v1/image/exist")
    public ResponseDTO<List<Map<String, Object>>> existRepositories(
            @RequestParam String name,
            @RequestParam(required = false) String projectName) {
        List<TagItemVO> tagItems = imageCatalogQueryService.existRepositories(name, projectName);
        return ResponseDTO.ok(tagItems.stream().map(ImageTagAssembler::toItem).toList());
    }

    /**
     * 厂商 `GET /v1/image/existTag?imageName&tag`。
     * <p><b>类 C</b>：依赖 Registry。
     */
    @GetMapping("/v1/image/existTag")
    public ResponseDTO<Boolean> existImageTag(@RequestParam String imageName, @RequestParam String tag) {
        return ResponseDTO.ok(imageCatalogQueryService.existImageTag(imageName, tag));
    }

    // ------------------------------------------------------------------ 镜像 ENV / 暴露端口

    /**
     * 厂商 `GET /v1/image/image_env?name&tag`：`name`/`tag` 为空 ⇒ 400 `image.get.envs.argument.is.null`
     * （厂商用 `StringUtils.isEmpty`，**只判 null 与空串**，不判纯空格）。<b>类 C</b>：依赖 docker daemon。
     */
    @GetMapping("/v1/image/image_env")
    public ResponseDTO<List<Map<String, Object>>> getImageEnv(@RequestParam String name, @RequestParam String tag) {
        if (name == null || name.isEmpty() || tag == null || tag.isEmpty()) {
            throw new WrongArgumentException("image.get.envs.argument.is.null");
        }
        return ResponseDTO.ok(imageCatalogQueryService.queryImageEnv(name, tag).stream()
                .map(ImageEnvAssembler::toItem).toList());
    }

    /**
     * 厂商 `GET /v1/image/expose?name&tag`：`{list:[{port,protocol}]}`；SSH 不可达 ⇒ **空列表**。
     * <p><b>类 C</b>：依赖 SSH（本环境没有可 SSH 的节点）。
     */
    @GetMapping("/v1/image/expose")
    public ResponseDTO<Map<String, Object>> imageExposePorts(@RequestParam String name, @RequestParam String tag) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", imageCatalogQueryService.queryExposedPorts(name, tag).stream()
                .map(ImageExposedPortAssembler::toItem).toList());
        return ResponseDTO.ok(data);
    }

    // ------------------------------------------------------------------ 服务关联镜像

    /**
     * 厂商 `GET /v1/image/serviceRelationImage`：过滤 Deployment 后取 `{group,imageName,tag}` 去重列表。
     * <p><b>类 C</b>：判定要调 Registry（`existImageTag`），Deployment 过滤本身走 K8s。
     */
    @GetMapping("/v1/image/serviceRelationImage")
    public ResponseDTO<List<Map<String, Object>>> getServiceRelationImage() {
        return ResponseDTO.ok(imageCatalogQueryService.queryServiceRelationImage().stream()
                .map(ImageInfoAssembler::toItem).toList());
    }

    // ------------------------------------------------------------------ 写路径

    /**
     * 厂商 `PUT /v1/image?repoId=`：改镜像描述（`g_setting.image.desc` 的 JSON 对象）。
     * <p>`description` 长度 > 36 ⇒ 400 `image.description.illegal`。
     * <p>厂商打了 `@AuditLog(opModule="IMAGE", opType="...modify.image.description", behavior="UPDATE")`。
     * <p>纯库路径，**可在线验证**。
     */
    @PutMapping("/v1/image")
    public ResponseDTO<Boolean> updateImageDescription(
            @RequestParam String repoId,
            @RequestBody ImageUpdateDescriptionDTO body) {
        if (body.getDescription() != null && body.getDescription().length() > 36) {
            throw new WrongArgumentException("image.description.illegal", body.getDescription());
        }
        imageCommandService.updateImageDescription(repoId, body.getDescription());
        return ResponseDTO.ok(true);
    }

    /**
     * 厂商 `POST /v1/image/project?name=`：新建仓库组（重名 ⇒ 400 `image.duplicate.repository.group`）。
     * <p>厂商打了 `@AuditLog(opModule="IMAGE", opType="...create.warehouse.group", behavior="CREATE")`。
     * <p>纯库路径，**可在线验证**。
     */
    @PostMapping("/v1/image/project")
    public ResponseDTO<Boolean> createProject(@RequestParam String name) {
        return ResponseDTO.ok(imageCommandService.createProject(name));
    }

    /**
     * 厂商 `DELETE /v1/image/project?projectId=`：删仓库组（不存在 ⇒ 400 `image.group.not.found`）。
     * <p>厂商打了 `@AuditLog(opModule="IMAGE", opType="...delete.image.warehouse.group", behavior="DELETE")`。
     * <p>纯库路径，**可在线验证**。
     */
    @DeleteMapping("/v1/image/project")
    public ResponseDTO<Boolean> deleteProject(@RequestParam Integer projectId) {
        return ResponseDTO.ok(imageCommandService.deleteProjectThrow(projectId));
    }

    /**
     * 厂商 `DELETE /v1/image?name&tag`：`tag` 用 `,` 分割批量删（`deleteImageByTag`）。
     * <p>厂商打了 `@AuditLog(opModule="IMAGE", opType="...delete.image.specified.tag", behavior="DELETE")`。
     * <p><b>类 C</b>：先删 Registry manifest，再**新线程** `docker rmi`。
     */
    @DeleteMapping("/v1/image")
    public ResponseDTO<Boolean> deleteImageByTag(@RequestParam String name, @RequestParam String tag) {
        List<String> tagList = new ArrayList<>(Arrays.asList(tag.split(",")));
        log.debug("tagList:{}", tagList);
        return ResponseDTO.ok(imageCommandService.deleteImageByTag(name, tagList));
    }

    /**
     * 厂商 `DELETE /v1/image/all_image?imageName=`：镜像名用 `,` 分割批量删。
     * <p>厂商打了 `@AuditLog(opModule="IMAGE", opType="...batch.delete.images", behavior="DELETE")`。
     * <p>厂商**无条件**返回 `true`（删不掉也不报错）。<b>类 C</b>：Registry + docker daemon。
     */
    @DeleteMapping("/v1/image/all_image")
    public ResponseDTO<Boolean> deleteAllImage(@RequestParam String imageName) {
        List<String> imageList = new ArrayList<>(Arrays.asList(imageName.split(",")));
        imageCommandService.deleteRepositories(imageList);
        return ResponseDTO.ok(true);
    }

    // ------------------------------------------------------------------ 上传日志 / 下载

    /**
     * 厂商 `GET /v1/image/imageLog?page&size&keyword`：`{list,total}`。
     * <p>`keyword` 先 `replaceAll(" +","")`；`total` 是**满足条件的总行数**。
     * <p>**副作用照抄**：`status=2` 且超过 30 分钟的行被改成失败并追加超时日志。
     * <p>纯库路径，**可在线验证**。
     */
    @GetMapping("/v1/image/imageLog")
    public ResponseDTO<Map<String, Object>> getImageLogList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        if (keyword != null) {
            keyword = keyword.replaceAll(" +", "");
        }
        ImageLogListEntity imageList = imageCatalogQueryService.queryImageLogList(normalizedPage, normalizedSize, keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", imageList.getList().stream().map(ImageLogAssembler::toItem).toList());
        data.put("total", imageList.getTotal());
        return ResponseDTO.ok(data);
    }

    /**
     * 厂商 `GET /v1/image/download?name&tag`：先 pull → tag → `docker save`，边 gzip 边写响应
     * （`Content-Disposition: attachment; filename*=UTF-8''<name>-<tag>.tar.gz`）。
     *
     * <p>厂商用 `DeferredResult<Object>`（5 分钟超时）在回调线程里写响应流；这里把 pull/tag 收敛成
     * **阻塞**调用（见 `IDockerImagePort`），保持同样的次序与返回形态。
     * <p>拉取失败 ⇒ `docker.pull.image.failed`；**下载过程中的 IOException 只记日志**（照抄）。
     * <p>压缩用 JDK 的 `GZIPOutputStream` 替代厂商的 commons-compress（paas-core 无该依赖，
     * 两者都是标准 gzip 容器格式）。
     * <p><b>类 C</b>：依赖 docker daemon。
     */
    @GetMapping("/v1/image/download")
    public DeferredResult<Object> exportImage(final HttpServletResponse response,
                                              @RequestParam String name, @RequestParam String tag) {
        final long start = System.currentTimeMillis();
        final DeferredResult<Object> output = new DeferredResult<>(REQUEST_TIMEOUT);
        // 厂商 `DockerService.nameWithRepository(name)` = registryHost + "/" + name
        final String nameWithRepository = dockerRegistryPort.registryHost() + "/" + name;
        log.info("nameWithRepository:{}", nameWithRepository);
        try {
            dockerImagePort.pull(nameWithRepository, tag);
            dockerImagePort.tag(nameWithRepository + ":" + tag, name, tag);
        } catch (RuntimeException e) {
            log.info("pull 失败：{}:{}", nameWithRepository, tag, e);
            output.setResult(null);
            throw new WrongArgumentException("docker.pull.image.failed");
        }
        try (ServletOutputStream outputStream = response.getOutputStream();
             InputStream inputStream = dockerImagePort.saveImage(name, tag);
             GZIPOutputStream gzipCompressorOutputStream = new GZIPOutputStream(outputStream)) {
            response.setContentType("application/x-tar");
            String filename = String.join("-", name, tag) + ".tar.gz";
            response.setHeader("Content-Disposition", attachmentHeaderValue(filename));
            int bufferSize = 40960;
            byte[] buffer = new byte[bufferSize];
            int n;
            while (-1 != (n = inputStream.read(buffer))) {
                gzipCompressorOutputStream.write(buffer, 0, n);
            }
            outputStream.flush();
            log.info("下载文件花费时间:{}毫秒", System.currentTimeMillis() - start);
        } catch (IOException e) {
            log.info("下载镜像失败", e);
        } catch (RuntimeException e) {
            // 厂商 `downloadAsTar` 抛的是 IOException（被上面捕获）；我们的 docker 端口连不上时抛运行时异常，
            // 这里按同样语义记日志（响应体可能已部分写出）
            log.info("下载镜像失败", e);
        }
        log.info("调用接口花费时间:{}毫秒", System.currentTimeMillis() - start);
        output.setResult(null);
        return output;
    }

    /** 厂商 `ResponseUtils.getAttachmentHeaderValue`：`attachment; filename*=UTF-8''<urlencoded>`。 */
    private static String attachmentHeaderValue(String filename) {
        return "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 组装

    private static Map<String, Object> repositoryList(RepositoryListEntity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", entity.getList().stream().map(ImageRepositoryAssembler::toItem).toList());
        data.put("total", entity.getTotal());
        return data;
    }

    private static Map<String, Object> tagList(TagListEntity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", entity.getList().stream().map(ImageTagAssembler::toItem).toList());
        data.put("total", entity.getTotal());
        return data;
    }
}
