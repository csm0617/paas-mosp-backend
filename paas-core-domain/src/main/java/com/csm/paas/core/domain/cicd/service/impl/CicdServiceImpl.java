package com.csm.paas.core.domain.cicd.service.impl;

import com.csm.paas.core.domain.cicd.adapter.port.ICicdBuildPort;
import com.csm.paas.core.domain.cicd.adapter.repository.ICicdRepository;
import com.csm.paas.core.domain.cicd.model.entity.CicdEntity;
import com.csm.paas.core.domain.cicd.model.valobj.CicdBuildJobVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobPodVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobStatusVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdTaskItemVO;
import com.csm.paas.core.domain.cicd.service.ICicdService;
import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.model.entity.TagListEntity;
import com.csm.paas.core.domain.image.model.valobj.ImageItemVO;
import com.csm.paas.core.domain.image.model.valobj.TagItemVO;
import com.csm.paas.core.domain.image.service.IImageCatalogQueryService;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自动构建领域服务（厂商 `service/CicdService.java:125-533`）。
 *
 * <p>证据等级：〔代码事实〕逐行读厂商 `CicdService`；〔类 C〕K8s Job / Docker Registry /
 * 宿主机目录三条外部依赖本环境不可验证。
 *
 * <p>照抄点（每一条都会让"重写"与厂商产生可见差异）：
 * <ol>
 *   <li>`updateDockerFile` 在 dockerfile 为 null 时**只改了内存对象就返回 false**——
 *       desc / updateAt 都不落库（`CicdService:148-154`）；</li>
 *   <li>`cicdList` 先拷贝再算新 jobStatus ⇒ **响应里的状态是旧值**，而库里已更新
 *       （`CicdService:217-251`）；</li>
 *   <li>`cicdList` 的 succeeded 分支里，**镜像 tag 列表为空时不写 jobStatus**
 *       （只有 tags 非 null 且非空才落到 1/0，`CicdService:238-245`）；</li>
 *   <li>`continuousIntegrationsV2` 把 Dockerfile 第一行**整体替换**成
 *       `FROM <harbor>/system_containers/<第一行去掉 FROM 与空格>`（`CicdService:447-453`）；</li>
 *   <li>创建 Job 后读状态那段：状态里四支**都不命中时只记日志、不写 jobStatus**，但仍写 updateAt
 *       （`CicdService:494-512`）；若返回的 `status` 本身为 null，厂商会 NPE 并被外层 catch(Exception)
 *       吞掉 ⇒ **连 updateAt 都不写**（照抄）；</li>
 *   <li>`getJobLog` 找不到 pod 时照样把 null 传给 `readNamespacedPodLog`，异常被吞 ⇒ 返回 null
 *       （`CicdService:166-181`）。</li>
 * </ol>
 *
 * <p>与厂商的一处**主动偏离**：厂商用 `org.apache.commons.io.FileUtils.cleanDirectory(dir)`，
 * 而 `paas-core-domain` 没有 commons-io 依赖（见 `paas-core-domain/pom.xml`），
 * 故用 `java.nio.file` 手写"只清空子项、保留目录本身"的等价实现。
 */
@Slf4j
@Service
public class CicdServiceImpl implements ICicdService {

    /** 厂商 `K8s.NAMESPACE`（`constant/K8s.java`），paas-core 用 `gaop.k8s.namespace`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    /** 厂商配置项 `cicd.image_version`（vendor `application.yml:153` 默认 `1.2`）。 */
    @Value("${cicd.image_version:1.2}")
    private String imageVersion;

    /** 厂商配置项 `file.cicd`（vendor `application.yml:110` 默认 `/opt/gaop/upload/cicd`）。 */
    @Value("${file.cicd:/opt/gaop/upload/cicd}")
    private String cicdDir;

    /** 厂商配置项 `mount.path`（vendor `application.yml:206` 默认 `/data/paas/`）。 */
    @Value("${mount.path:/data/paas/}")
    private String mountPath;

    /** 厂商配置项 `imageupload`（vendor `application.yml:187` 默认 `gaop-fdfs:9527`）。 */
    @Value("${imageupload:gaop-fdfs:9527}")
    private String imageUploadInfo;

    /** 厂商 `HostConfig.getHarbor()`（`server.host.harbor`；本仓既有默认值取自 `DockerRegistryPort`）。 */
    @Value("${gaop.harbor.url:${server.host.harbor:http://harbor:30002}}")
    private String harbor;

    /** 厂商 `DockerConfig.getUsername()`（`docker.username`，vendor 默认 `admin`）。 */
    @Value("${docker.username:admin}")
    private String dockerUsername;

    /** 厂商 `DockerConfig.getPassword()`（`docker.password`，vendor 默认 `Harbor12345`）。 */
    @Value("${docker.password:Harbor12345}")
    private String dockerPassword;

    @Resource
    private ICicdRepository cicdRepository;

    @Resource
    private ICicdBuildPort cicdBuildPort;

    /** 厂商 `DockerRegistryApiService.getImageListByProject`。 */
    @Resource
    private IDockerRegistryPort dockerRegistryPort;

    /** 厂商 `K3sImageServiceImpl.getImageTagListByImageList`。 */
    @Resource
    private IImageCatalogQueryService imageCatalogQueryService;

    // ------------------------------------------------------------------ createTask

    /** 厂商 `CicdService.createTask:281-305`。 */
    @Override
    public CicdEntity createTask(String userId, String imageStorage, String imageName, String desc,
                                 String dockerfile) {
        // 照抄：imageName 为 null 时厂商直接 NPE（500），本实现同样不兜
        if (!imageName.matches("^[a-zA-Z0-9-]+$")) {
            throw new WrongArgumentException("image-auto-build.illegal.image.name");
        }
        long currentTimeMillis = System.currentTimeMillis();
        String name = imageName.replaceAll(" +", "");
        List<CicdEntity> select = cicdRepository.queryByImageNameAndUserId(name, userId);
        if (!select.isEmpty()) {
            throw new WrongArgumentException("image-auto-build.duplicate.image.name");
        }
        CicdEntity info = CicdEntity.builder()
                .userId(userId)
                .imageName(name)
                .description(desc)
                .createAt(currentTimeMillis)
                .dockerfileContent(dockerfile)
                .imageStorage(imageStorage)
                .build();
        int i = cicdRepository.insertSelective(info);
        if (i < 1) {
            throw new WrongArgumentException("image-auto-build.create.failed");
        }
        List<CicdEntity> cicds = cicdRepository.queryByImageNameAndUserId(name, userId);
        return cicds.get(0);
    }

    // ------------------------------------------------------------------ deleteTask

    /** 厂商 `CicdService.deleteTask:125-135`。 */
    @Override
    public boolean deleteTask(Integer id) {
        Optional<CicdEntity> cicd = cicdRepository.queryById(id);
        if (cicd.isEmpty()) {
            throw new WrongArgumentException("image-auto-build.delete.failed.not.exist");
        }
        int i = cicdRepository.deleteById(id);
        if (i < 1) {
            throw new WrongArgumentException("image-auto-build.delete.failed");
        }
        return true;
    }

    // ------------------------------------------------------------------ updateDockerFile

    /** 厂商 `CicdService.updateDockerFile:137-155`。 */
    @Override
    public boolean updateDockerFile(Integer id, String desc, String dockerfile) {
        long currentTimeMillis = System.currentTimeMillis();
        Optional<CicdEntity> cicd = cicdRepository.queryById(id);
        if (cicd.isEmpty()) {
            throw new WrongArgumentException("image-auto-build.cannot.get.build.info");
        }
        CicdEntity c = cicd.get();
        if (desc != null && !desc.isBlank()) {
            c.setDescription(desc);
        }
        c.setUpdateAt(currentTimeMillis);
        if (dockerfile != null) {
            c.setDockerfileContent(dockerfile);
            cicdRepository.updateByPrimaryKey(c);
            return true;
        }
        // 照抄：dockerfile == null 时上面改的 description/updateAt **不落库**，直接 false
        return false;
    }

    // ------------------------------------------------------------------ getJobLog

    /** 厂商 `CicdService.getJobLog:157-182`。 */
    @Override
    public String getJobLog(String jobName) {
        return cicdBuildPort.readJobLog(namespace, jobName);
    }

    // ------------------------------------------------------------------ cicdList

    /** 厂商 `CicdService.cicdList:184-279`。 */
    @Override
    public CicdPage list(String userId, Integer page, Integer size, String keyword) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        String formKeyword = keyword;
        if (formKeyword != null) {
            formKeyword = formKeyword.replaceAll(" +", "");
        }
        String likeKeyword = formKeyword == null || formKeyword.isEmpty() ? null : "%" + formKeyword + "%";
        log.debug("模糊查询的keyword的值为:{}", likeKeyword);

        // 厂商 `listPodForAllNamespaces` + 按 `job-name` 建索引（读失败 ⇒ 空表，只记日志）
        Map<String, CicdJobPodVO> jobnameMap = new HashMap<>();
        for (CicdJobPodVO pod : cicdBuildPort.listBuildPods()) {
            jobnameMap.put(pod.getJobName(), pod);
        }

        List<CicdEntity> cicdList = cicdRepository.queryPage(userId, likeKeyword,
                normalizedSize, PageUtils.offset(normalizedPage, normalizedSize));
        long total = cicdRepository.countByUserAndKeyword(userId, likeKeyword);

        List<CicdTaskItemVO> list = new ArrayList<>();
        if (!cicdList.isEmpty()) {
            // 厂商 `imageListMap`：同一次请求内按 imageStorage 复用 registry 结果
            Map<String, List<ImageItemVO>> imageListMap = new HashMap<>();
            for (CicdEntity c : cicdList) {
                // 照抄：先把**旧值**拷进响应行，后面的 jobStatus 更新只落在库与 `c` 上
                CicdTaskItemVO item = CicdTaskItemVO.builder().cicd(copyOf(c)).build();
                if (c.getJobName() != null) {
                    String jobName = c.getJobName();
                    CicdJobStatusVO status = cicdBuildPort.readJobStatus(namespace, jobName);
                    if (status != null) {
                        if (status.getActive() != null) {
                            c.setJobStatus(2);
                        } else if (status.getSucceeded() != null) {
                            String imageStorage = c.getImageStorage();
                            String imageName = c.getImageName();
                            List<ImageItemVO> imageList = imageListMap.get(imageStorage);
                            if (imageList == null) {
                                imageList = dockerRegistryPort.getImageListByProject(imageStorage);
                                imageListMap.put(imageStorage, imageList);
                            }
                            String name = imageStorage + "/" + imageName;
                            TagListEntity imageTagList = imageCatalogQueryService
                                    .queryImageTagListByImageList(1, Integer.MAX_VALUE, null, name, imageList);
                            List<TagItemVO> tags = imageTagList.getList();
                            // 照抄：tags 为 null/空时**什么也不写**（jobStatus 保持旧值）
                            if (tags != null && !tags.isEmpty()) {
                                List<TagItemVO> collect = tags.stream()
                                        .filter(t -> t.getName().equals(c.getImageTag()))
                                        .toList();
                                if (!collect.isEmpty()) {
                                    c.setJobStatus(1);
                                } else {
                                    c.setJobStatus(0);
                                }
                            }
                        } else if (status.getFailed() != null) {
                            c.setJobStatus(0);
                        } else {
                            c.setJobStatus(0);
                        }
                        cicdRepository.updateByPrimaryKeySelective(c);
                    } else {
                        // 厂商 `catch (ApiException) { log.error("获取job信息失败", e2) }`
                        log.error("获取job信息失败: {}", jobName);
                    }
                    // 厂商 PodContainerInfo 的 container 字段声明即初始化 ⇒ 取不到 pod 时是 {"container":[]}
                    CicdJobPodVO pod = new CicdJobPodVO();
                    CicdJobPodVO found = jobnameMap.get(jobName);
                    if (found != null) {
                        pod.setPodName(found.getPodName());
                        pod.setNamespace(found.getNamespace());
                        pod.setContainer(new ArrayList<>(found.getContainer()));
                    }
                    item.setPod(pod);
                }
                list.add(item);
            }
        }
        return new CicdPage(list, total);
    }

    // ------------------------------------------------------------------ continuousIntegrationsV2

    /** 厂商 `CicdService.continuousIntegrationsV2:428-521`。 */
    @Override
    public Map<String, Object> continuousIntegrationsV2(String userId, String name, String tag,
                                                       String buildName, byte[] fileBytes) throws IOException {
        long currentTime = System.currentTimeMillis();
        List<CicdEntity> select = cicdRepository.queryByImageNameAndUserId(name, userId);
        if (select.isEmpty()) {
            throw new WrongArgumentException("image-auto-build.build.task.not.found");
        }
        CicdEntity cicd = select.get(0);
        String content = cicd.getDockerfileContent();
        if (content == null) {
            throw new WrongArgumentException("image-auto-build.empty.dockerfile.data");
        }
        String storage = cicd.getImageStorage();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        String format = simpleDateFormat.format(new Date(currentTime));
        String tags = format + "-" + getRandomNumber();
        String useTag = tag == null || tag.trim().isEmpty() ? tags : tag;
        cicd.setImageTag(useTag);

        // 照抄：第一行整体替换成 `FROM <harbor>/system_containers/<第一行去掉 FROM 与空格>`
        String[] split = content.split("\n");
        String fromImage = split[0];
        String dependImage = fromImage.replaceAll(" +", "").replaceAll("FROM", "");
        String harborUrl = harbor.replaceAll("http://", "");
        String useImage = harborUrl + "/system_containers/" + dependImage;
        String harborImage = "FROM " + useImage;
        Arrays.fill(split, 0, 1, harborImage);
        String dockerfileContent = String.join("\n", split);
        log.info("获取的dockerfile文件为" + dockerfileContent);

        Path dockerfilePath = Paths.get(String.join(File.separator, cicdDir, cicd.getId().toString(), "Dockerfile"));
        Path dir = dockerfilePath.getParent();
        String nodeDir = String.join("/", mountPath, "gaop-data/upload/cicd", cicd.getId().toString());
        try {
            if (!Files.exists(dir)) {
                boolean mkdirs = dir.toFile().mkdirs();
                if (!mkdirs) {
                    throw new SystemException("image-auto-build.create.dockerfile.dir.failed", dir);
                }
            } else {
                // 厂商 `FileUtils.cleanDirectory`：只清空子项，保留目录本身
                cleanDirectory(dir);
            }
            StreamUtils.copy(dockerfileContent, StandardCharsets.UTF_8, Files.newOutputStream(dockerfilePath));
        } catch (Exception e) {
            log.error("创建Dockerfile失败", e);
            throw new SystemException("image-auto-build.create.dockerfile.failed", e.getMessage());
        }
        // 厂商 `form.getFile().transferTo(filePath)`：目标名就是上传文件的**原始名**
        Path filePath = Paths.get(String.join(File.separator, cicdDir, cicd.getId().toString(), buildName));
        try {
            Files.write(filePath, fileBytes);
        } catch (IOException e) {
            log.error("复制上传文件异常", e);
            throw new SystemException("global.copy.upload.file.failed", e.getMessage());
        }
        String jobName = name + "-" + getRandomNumber();
        cicd.setJobName(jobName);
        String image = harborUrl + "/system_containers/ci-job-image:" + imageVersion;
        cicd.setJobStatus(2);
        cicdRepository.updateByPrimaryKey(cicd);

        Map<String, Object> job = new HashMap<>();
        try {
            job = cicdBuildPort.createBuildJob(namespace, CicdBuildJobVO.builder()
                    .name(jobName)
                    .image(image)
                    .nodeDir(nodeDir)
                    .buildName(buildName)
                    .formName(name)
                    .tag(useTag)
                    .storage(storage)
                    .harbor(harborUrl)
                    .username(dockerUsername)
                    .password(dockerPassword)
                    .imageInfo(imageUploadInfo)
                    .build());
            // 照抄：直接读**创建返回对象**的 status（不是再读一次 Job）
            Map<String, Object> statusMap = asMap(job.get("status"));
            CicdJobStatusVO status = CicdJobStatusVO.builder()
                    .active((Integer) statusMap.get("active"))
                    .succeeded((Integer) statusMap.get("succeeded"))
                    .failed((Integer) statusMap.get("failed"))
                    .ready((Integer) statusMap.get("ready"))
                    .build();
            if (status.getActive() != null) {
                cicd.setJobStatus(2);
                log.info("镜像构建job执行中");
            } else if (status.getSucceeded() != null) {
                cicd.setJobStatus(1);
                log.info("镜像构建job执行完成");
            } else if (status.getFailed() != null) {
                cicd.setJobStatus(0);
                log.info("镜像构建job执行失败");
            } else if (status.getReady() != null) {
                cicd.setJobStatus(2);
                log.info("镜像构建job准备就绪");
            } else {
                // 照抄：这一支**不写 jobStatus**，只记日志
                log.info("镜像构建job执行未知");
                log.info("status:{}", status);
            }
            cicd.setUpdateAt(currentTime);
            cicdRepository.updateByPrimaryKey(cicd);
        } catch (Exception e) {
            // 厂商分 ApiException / Exception 两支，都只记日志、不抛（`CicdService:514-519`）
            log.error("错误Job异常", e);
        }
        return job;
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 厂商 `CicdService.getRandomNumber:523-533`：从 `0123456789abcdefghijklmnopqrstuvwxyz`
     * 里取 6 个字符。厂商那个 `symbols` 局部变量声明后从未使用（死代码），照抄其**结果**。
     */
    public String getRandomNumber() {
        SecureRandom random = new SecureRandom();
        char[] chars = new char[6];
        for (int i = 0; i < chars.length; ++i) {
            chars[i] = "0123456789abcdefghijklmnopqrstuvwxyz".charAt(
                    random.nextInt("0123456789abcdefghijklmnopqrstuvwxyz".length()));
        }
        String randomNumber = new String(chars);
        log.info("随机生成6位字符串为:" + randomNumber);
        return randomNumber;
    }

    /** `BeanUtils.copyProperties(c, cicdWithPodInfo)` 的等价拷贝。 */
    private static CicdEntity copyOf(CicdEntity c) {
        return CicdEntity.builder()
                .id(c.getId()).userId(c.getUserId()).imageName(c.getImageName())
                .imageTag(c.getImageTag()).dockerfileId(c.getDockerfileId())
                .dockerfileContent(c.getDockerfileContent()).buildfileId(c.getBuildfileId())
                .jobName(c.getJobName()).jobStatus(c.getJobStatus()).description(c.getDescription())
                .createAt(c.getCreateAt()).updateAt(c.getUpdateAt()).imageStorage(c.getImageStorage())
                .build();
    }

    /**
     * "只清空子项、保留目录本身"。厂商用 `FileUtils.cleanDirectory`；
     * domain 无 commons-io（见类 javadoc 的偏离说明）。
     */
    private static void cleanDirectory(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!p.equals(dir)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }
}
