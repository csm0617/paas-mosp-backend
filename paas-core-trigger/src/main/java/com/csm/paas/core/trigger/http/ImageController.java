package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ImageUpdateDescriptionDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.image.adapter.port.IDockerImagePort;
import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.adapter.port.IImageUploadProgressPort;
import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.adapter.repository.IImageLogRepository;
import com.csm.paas.core.domain.image.model.entity.ImageLogListEntity;
import com.csm.paas.core.domain.image.model.entity.RepositoryListEntity;
import com.csm.paas.core.domain.image.model.entity.TagListEntity;
import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p><b>`POST /v1/image/upload` 与 `POST /v1/image/clean`</b> 本来**不属于 gaop-be**——
 * 它们在独立服务 `recovered/gaop-image-uploader/.../controller/ImageController.java:101,161`
 * （Redis 上传进度 + raw websocket + docker load/push，端口 18080）。本重写把控制器的判断与
 * `g_group`/`g_image_log`/docker 调用**照抄搬进来**（见类末尾"上传 / 清理"一节），
 * 上传进度改存 Redis（端口 {@code IImageUploadProgressPort}）、进度推送改走既有 STOMP broker。
 * <p><b>类 C</b>：upload 依赖 docker daemon + registry；clean 依赖 Redis；两者都不可在线验证。
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

    // ------------------------------------------------------------------ 上传进度（uploader 独立服务的那套 Redis 键）

    /** uploader 的 Redis 进度存储（端口在 infrastructure）。 */
    @Resource
    private IImageUploadProgressPort imageUploadProgressPort;

    @Resource
    private IImageLogRepository imageLogRepository;

    @Resource
    private IImageGroupRepository imageGroupRepository;

    /** 进度广播（厂商是 uploader 进程里的 raw websocket；本重写改用既有 STOMP broker，见类 javadoc）。 */
    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /** 厂商 uploader `application.yml:35`：`timeout: ${REDIS_TIMEOUT:30}`（分钟）。 */
    @Value("${timeout:30}")
    private long timeout;

    /** 厂商 `ImageController` 的三个 Redis 键常量（照抄键名）。 */
    static final String IMAGEUPLOADINFO = "imageUploadInfo";
    static final String IMAGE_CURRENT_COUNT = "image_current_count";
    static final String IMAGE_CURRENT_TOTAL = "image_current_total";
    static final String IMAGE_ERROR_COUNT = "image_error_count";

    /** 厂商 `ImageController.DATE_TIME_FORMATTER`。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

    /** 厂商 `ImageUploadInfo` 的状态常量：0 初始 / 1 进行中 / 2 成功 / 3 失败。 */
    private static final int UPLOAD_INIT = 0;
    private static final int UPLOAD_RUNNING = 1;
    private static final int UPLOAD_SUCCESS = 2;

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    // ------------------------------------------------------------------ 上传 / 清理（厂商 gaop-image-uploader）

    /**
     * 厂商 `POST /v1/image/clean`（`recovered/gaop-image-uploader/.../controller/ImageController.java:101-140`）。
     *
     * <p>照抄点：`total == 0` 是"取消上传"分支——存在进度键则删掉进度与当前计数、推一次
     * **初始进度**；不存在进度键也推一次初始进度。`total != 0` 是"开始上传"分支——写一条
     * `status=1(RUNNING)`、`total=<total>` 的进度 + 计数归零，TTL 用 `${timeout:30}` 分钟。
     *
     * <p><b>类 C：本环境不可验证</b>（依赖 Redis）。进度推送偏离：厂商推的是 uploader 进程内
     * raw websocket（前端连 `ws://&lt;host&gt;:19528/ws/uploadImage/&lt;username&gt;`），本重写改发到既有
     * STOMP broker 的 `/topic/image/upload`——前端那条 ws 是别的进程的端口，无法在本进程补。
     */
    @PostMapping("/v1/image/clean")
    public ResponseDTO<Boolean> uploadImageList(@RequestParam Integer total) {
        log.info("清空redis存储进度信息，数据进行初始化");
        if (total == 0) {
            String info = imageUploadProgressPort.getValue(IMAGEUPLOADINFO);
            if (StringUtils.hasText(info)) {
                log.info("取消上传，移除redis中的上传进度信息");
                imageUploadProgressPort.delete(IMAGEUPLOADINFO);
                if (imageUploadProgressPort.getInt(IMAGE_CURRENT_COUNT) != null) {
                    imageUploadProgressPort.delete(IMAGE_CURRENT_COUNT);
                }
                pushUploadProgress(initUploadProgressJson());
                log.info("最后一次推送完成");
                return ResponseDTO.ok(true);
            }
            log.info("取消上传,redis中未存在上传进度信息");
            pushUploadProgress(initUploadProgressJson());
            log.info("最后一次推送完成");
            return ResponseDTO.ok(true);
        }
        log.info("开始正常初始化上传信息");
        String progress = runningUploadProgressJson(total);
        pushUploadProgress(progress);
        imageUploadProgressPort.setValue(IMAGEUPLOADINFO, progress, timeout);
        imageUploadProgressPort.setInt(IMAGE_CURRENT_COUNT, 0, timeout);
        log.info("初始化完成");
        return ResponseDTO.ok(true);
    }

    /**
     * 厂商 `POST /v1/image/upload`（`.../ImageController.java:161-360`）。
     *
     * <p>query 参数 `storage`/`username`/`type`（type 缺省 1；2 = 批量上传，要维护进度计数），
     * multipart `file` 是 tar 或 tar.gz。照抄点：
     * <ol>
     *   <li>先按名找仓库组，找不到就建一个；建也失败 ⇒ `storage` 回落成 `gaop`（只记日志，不报错）；</li>
     *   <li>插一条 `status=2` 的上传日志（`g_image_log`），后续日志都往这行追加；</li>
     *   <li>Tika 探测结果必须是 `application/x-tar` 或 `application/gzip`，否则追加日志并
     *       400 `只支持tar和tar.gz文件的上传`（厂商拿中文当消息，不是 i18n 键）；</li>
     *   <li>`type=2` 且进度键存在 ⇒ 把进度里的 `name` 更新成当前文件名；</li>
     *   <li>docker `/images/load` → 从响应文本解析 `Loaded image: &lt;name&gt;:&lt;tag&gt;`；
     *       解析失败 ⇒ 日志置 0 并 400 `上传出现异常`；</li>
     *   <li>成功 ⇒ 写 `name`/`version` 到日志行，tag 成 `&lt;registryHost&gt;/&lt;storage&gt;/&lt;basename&gt;` 并 push，
     *       最后日志置 1 + 追加"上传镜像成功"；`type=2` 时推进当前计数，
     *       当"当前 + 错误数 == 总数"时把进度置 `status=2(SUCCESS)`。</li>
     * </ol>
     *
     * <p><b>两处主动偏离</b>（都写进回报）：
     * ① 厂商 `DockerService.uploadImage` 是 `@Async`（接口立即返回、上传在后台跑）；本重写的
     * {@code IDockerImagePort} 是阻塞式，因此这里会等 load+push 完成才返回（HTTP 更快超时风险由前端控制）。
     * ② 厂商开头那次 `dockerService.getVersion()` 会去请求 `/v1/pub/version`；本重写只打日志、不发该请求。
     * <p><b>类 C：本环境不可验证</b>（依赖 docker daemon + registry + Redis）。
     */
    @PostMapping(value = "/v1/image/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Boolean> uploadImage(@RequestParam String storage,
                                            @RequestParam String username,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) Integer type) throws IOException {
        if (type == null) {
            type = 1;
        }
        long startTime = System.currentTimeMillis();
        log.info("当前集群版本为:{}", "unknown");
        Boolean project = false;
        var groupOptional = imageGroupRepository.queryByName(storage).stream().findFirst();
        if (groupOptional.isPresent()) {
            project = true;
        } else {
            var imageGroup = imageGroupRepository.insert(storage, System.currentTimeMillis());
            if (imageGroup != null) {
                project = true;
            }
        }
        if (project) {
            log.info("已存在或已成功创建仓库组:{}", storage);
        } else {
            log.info("不存在且创建仓库组:{}失败，默认采用gaop仓库组", storage);
            storage = "gaop";
        }
        ImageLogVO imageLog = ImageLogVO.builder()
                .createdAt(System.currentTimeMillis())
                .fileName(file.getOriginalFilename())
                .status(2)
                .username(username)
                .log(DATE_TIME_FORMATTER.format(ZonedDateTime.now()) + "  开始处理上传镜像："
                        + file.getOriginalFilename() + "\n")
                .build();
        ImageLogVO savedLog = imageLogRepository.insert(imageLog);
        long id = savedLog.getId();
        final Integer finalType = type;
        String contentType = new Tika().detect(file.getInputStream());
        log.info("上传镜像文件类型为:{}", contentType);
        if (!"application/x-tar".equals(contentType) && !"application/gzip".equals(contentType)) {
            imageLogRepository.appendLog(id, "失败：只支持tar和tar.gz文件的上传");
            throw new WrongArgumentException("只支持tar和tar.gz文件的上传");
        }
        if (finalType == 2) {
            String imageJson = imageUploadProgressPort.getValue(IMAGEUPLOADINFO);
            if (StringUtils.hasText(imageJson)) {
                ObjectNode info = readProgressNode(imageJson);
                info.put("name", file.getOriginalFilename());
                imageUploadProgressPort.setValue(IMAGEUPLOADINFO, info.toString(), timeout);
            }
        }
        String loadResponse;
        try {
            loadResponse = dockerImagePort.loadImage(file.getInputStream());
        } catch (RuntimeException e) {
            log.info("上传镜像出现异常:", e);
            throw new WrongArgumentException("上传镜像出现异常");
        }
        imageLogRepository.appendLog(id, "load镜像:" + loadResponse);
        ImageMeta imageMeta = parseLoadImage(loadResponse);
        if (imageMeta == null) {
            String parseFailed = "解析镜像文件信息失败," + loadResponse;
            log.error(parseFailed);
            imageLogRepository.appendLog(id, parseFailed);
            ImageLogVO failed = new ImageLogVO();
            failed.setId(id);
            failed.setStatus(0);
            imageLogRepository.updateSelective(failed);
            throw new WrongArgumentException("上传出现异常");
        }
        String imageMetaName = imageMeta.name();
        Matcher matcher = Pattern.compile("([^/]+)(/.*)?").matcher(imageMeta.name());
        if (matcher.matches() && StringUtils.hasText(matcher.group(2))) {
            imageMetaName = storage + matcher.group(2);
        }
        ImageLogVO nameUpdate = new ImageLogVO();
        nameUpdate.setId(id);
        nameUpdate.setName(imageMetaName);
        nameUpdate.setVersion(imageMeta.tag());
        imageLogRepository.updateSelective(nameUpdate);
        imageLogRepository.appendLog(id, "获取镜像元信息 " + imageMeta.name() + ":" + imageMeta.tag());
        // 厂商 `DockerService.push`：tag 成 <registryHost>/<storage>/<basename> 再 push
        String host = dockerRegistryPort.registryHost().replace("http://", "").replace("https://", "");
        String extractName = extractImageName(imageMeta.name());
        String imageNameWithRepository = host + "/" + storage + "/" + extractName;
        String imageId = imageMeta.name() + ":" + imageMeta.tag();
        dockerImagePort.tag(imageId, imageNameWithRepository, imageMeta.tag());
        String remoteImageId = imageNameWithRepository + ":" + imageMeta.tag();
        dockerImagePort.push(remoteImageId);
        ImageLogVO success = new ImageLogVO();
        success.setId(id);
        success.setStatus(1);
        success.setUpdatedAt(System.currentTimeMillis());
        imageLogRepository.updateSelective(success);
        imageLogRepository.appendLog(id, "上传镜像成功");
        if (finalType == 2) {
            updateUploadProgressOnComplete();
        }
        log.info("上传镜像接口总共花费的时间为:{}毫秒", System.currentTimeMillis() - startTime);
        return ResponseDTO.ok(true);
    }

    /**
     * 厂商 `ImageController` 里 `onComplete`/`close` 推到 Redis 的那段（成功分支）：
     * 当前计数 +1，读进度把 `current` 同步过去；`current + error == total` ⇒ 置 `status=SUCCESS`
     * 并清掉错误计数。
     */
    private void updateUploadProgressOnComplete() {
        imageUploadProgressPort.incrementInt(IMAGE_CURRENT_COUNT);
        String toJson = imageUploadProgressPort.getValue(IMAGEUPLOADINFO);
        if (!StringUtils.hasText(toJson)) {
            return;
        }
        ObjectNode info = readProgressNode(toJson);
        Integer current = imageUploadProgressPort.getInt(IMAGE_CURRENT_COUNT);
        info.put("current", current == null ? 0 : current);
        int error = 0;
        Integer errorNum = imageUploadProgressPort.getInt(IMAGE_ERROR_COUNT);
        if (errorNum != null) {
            error = errorNum;
        }
        if (info.path("current").asInt() + error == info.path("total").asInt()) {
            info.put("status", UPLOAD_SUCCESS);
        }
        imageUploadProgressPort.setValue(IMAGEUPLOADINFO, info.toString(), timeout);
        if (info.path("status").asInt() == UPLOAD_SUCCESS && error != 0) {
            imageUploadProgressPort.delete(IMAGE_ERROR_COUNT);
        }
    }

    /** `ImageUploadInfo.init()` 的 JSON：`{status:0,name:null,current:0,total:0}`。 */
    private static String initUploadProgressJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", UPLOAD_INIT);
        node.putNull("name");
        node.put("current", 0);
        node.put("total", 0);
        return node.toString();
    }

    /** `ImageUploadInfo.init()` 后 `status=RUNNING`、`total=<total>` 的 JSON（`clean` 的开始分支）。 */
    private static String runningUploadProgressJson(int total) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", UPLOAD_RUNNING);
        node.putNull("name");
        node.put("current", 0);
        node.put("total", total);
        return node.toString();
    }

    private static ObjectNode readProgressNode(String json) {
        try {
            var node = MAPPER.readTree(json);
            return node != null && node.isObject() ? (ObjectNode) node : MAPPER.createObjectNode();
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }

    /** 进度推送（厂商是 raw websocket，这里走 STOMP broker；推失败只记日志）。 */
    private void pushUploadProgress(String json) {
        try {
            messagingTemplate.convertAndSend("/topic/image/upload", json);
        } catch (RuntimeException e) {
            log.info("推送上传进度失败:{}", e.getMessage());
        }
    }

    /**
     * 厂商 `DockerService.parseLoadImage:287-303`：正则
     * `\{"stream":"Loaded image: (.*):(.*?)\\n"\}`，取 `name`/`tag`；不匹配返回 null。
     */
    private static ImageMeta parseLoadImage(String response) {
        if (response == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\{\"stream\":\"Loaded image: (.*):(.*?)\\\\n\"\\}")
                .matcher(response);
        if (matcher.find()) {
            return new ImageMeta(matcher.group(1), matcher.group(2));
        }
        return null;
    }

    /** 厂商 `DockerService.extractName`：最后一个 `/` 之后的部分。 */
    private static String extractImageName(String name) {
        int lastSlash = name.lastIndexOf("/");
        return lastSlash < 0 ? name : name.substring(lastSlash + 1);
    }

    /** 厂商 `ImageMeta`（`name` + `tag`）的最小等价物。 */
    private record ImageMeta(String name, String tag) {
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
