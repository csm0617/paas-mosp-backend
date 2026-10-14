package com.csm.paas.core.domain.image.service.impl;

import com.csm.paas.core.domain.image.adapter.port.IDockerImagePort;
import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.adapter.port.IImageWorkloadPort;
import com.csm.paas.core.domain.image.adapter.repository.IImageDescriptionRepository;
import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.adapter.repository.IImageLogRepository;
import com.csm.paas.core.domain.image.model.entity.ImageLogListEntity;
import com.csm.paas.core.domain.image.model.entity.RepositoryListEntity;
import com.csm.paas.core.domain.image.model.entity.TagListEntity;
import com.csm.paas.core.domain.image.model.valobj.ExposedPortItemVO;
import com.csm.paas.core.domain.image.model.valobj.ImageEnvInfoVO;
import com.csm.paas.core.domain.image.model.valobj.ImageGroupVO;
import com.csm.paas.core.domain.image.model.valobj.ImageInfoVO;
import com.csm.paas.core.domain.image.model.valobj.ImageItemVO;
import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;
import com.csm.paas.core.domain.image.model.valobj.ImageWorkloadVO;
import com.csm.paas.core.domain.image.model.valobj.RelateSoftwareInfoVO;
import com.csm.paas.core.domain.image.model.valobj.RepositoryItemVO;
import com.csm.paas.core.domain.image.model.valobj.TagItemVO;
import com.csm.paas.core.domain.image.service.IImageCatalogQueryService;
import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 镜像域查询实现：逐行照抄厂商 `recovered/gaop-be/.../service/impl/K3sImageServiceImpl.java`
 * 与 `.../service/DockerService.java:172-197`、`.../service/ImageService.java:644-740`。
 *
 * <p><b>类 C</b>：除 {@link #queryImageLogList}（纯库 `g_image_log`）外，
 * 这些端点都依赖 Docker Registry / docker daemon / SSH，本环境不可达，代码齐全但不做在线验证
 * （`docs/findings/79`、`81`）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCatalogQueryServiceImpl implements IImageCatalogQueryService {

    /** 厂商 `K3sImageServiceImpl.REGISTRY_IMAGE_PATTERN`：只认 "IP:PORT/组/名:tag" 形态。 */
    private static final Pattern REGISTRY_IMAGE_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+/(.*)");
    /**
     * 厂商 `getImageTagList`/`deleteImageByTag` 里那个**写错的正则**：
     * `String regx = "(\\\\d+)/([a-zA-Z]+)"` ⇒ 运行时是 `(\\d+)/([a-zA-Z]+)`，匹配的是
     * "字面反斜杠 + d" 而不是数字，所以这个分支**永远不会命中**，真正的解析走下面的逗号/斜杠分支。
     * <p>照抄（不"顺手修好"）。
     */
    private static final Pattern EXPOSE_PORT_PATTERN = Pattern.compile("(\\\\d+)/([a-zA-Z]+)");
    private static final String FILTERED_GROUP = "system_containers";
    private static final String DEFAULT_PROJECT_NAME = "gaop";
    /** 厂商 `ImageInfo`/`getLoad` 用的注解键。 */
    private static final String SERVICE_LABEL_KEY = "gaop/serviceLabel";
    private static final String APP_TYPE_LABEL = "system/appType";
    private static final String APP_NAME_LABEL = "system/appName";
    private static final String APP_TYPE_BUSINESS = "business";
    private static final String INGRESS_LB_LABEL = "ingress-lb";
    private static final String DISPLAY_NAME_ANNOTATION = "displayName";
    /** 厂商时间格式：`yyyy-MM-dd'T'HH:mm:ssXXX`。 */
    private static final DateTimeFormatter TAG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    /** 厂商 `DockerService.DATE_TIME_FORMATTER`：带毫秒 + **系统时区**。 */
    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());
    /** 厂商写死的超时：1800000ms = 30 分钟。 */
    private static final long UPLOAD_TIMEOUT_MILLIS = 1_800_000L;

    private final IDockerRegistryPort dockerRegistryPort;
    private final IDockerImagePort dockerImagePort;
    private final IImageWorkloadPort imageWorkloadPort;
    private final IImageLogRepository imageLogRepository;
    private final IImageDescriptionRepository imageDescriptionRepository;
    private final IImageGroupRepository imageGroupRepository;
    private final ISshPort sshPort;

    // ------------------------------------------------------------------ 仓库列表

    @Override
    public RepositoryListEntity queryRepositories(Integer page, Integer size, String keyword, String storage) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);

        Map<String, Integer> imageTagNum = new HashMap<>();
        Map<String, Long> imageNameList = new HashMap<>();
        Map<String, String> imageLastVersion = new HashMap<>();
        Map<String, List<String>> imageTagList = new HashMap<>();

        List<ImageItemVO> imageList = dockerRegistryPort.getImageListByProject(storage);
        for (ImageItemVO image : imageList) {
            Long created = image.getCreated();
            for (String repoTag : image.getRepoTags()) {
                if (!repoTag.contains(storage)) {
                    continue;
                }
                Matcher matcher = REGISTRY_IMAGE_PATTERN.matcher(repoTag);
                if (!matcher.matches()) {
                    log.info("未匹配前缀是原harbor仓库地址ip开头的,该镜像为加载时获取到的镜像");
                    log.info("此时镜像名称为:" + repoTag);
                    continue;
                }
                String group = matcher.group(1);
                log.info("拿到的镜像名称为:" + group);
                String[] storageInfo = group.split("/");
                String storageName = storageInfo[0];
                log.info("当前镜像的仓库组为:" + storageName);
                String[] split = group.split(":");
                if (!storageName.equals(storage) || split.length <= 1) {
                    continue;
                }
                String imageName = split[0];
                String imageTag = split[1];
                if (keyword != null && !keyword.isBlank() && !imageName.contains(keyword)) {
                    continue;
                }
                if (imageNameList.containsKey(imageName)) {
                    if (imageTagList.containsKey(imageName)) {
                        List<String> existTags = imageTagList.get(imageName);
                        if (!existTags.contains(imageTag)) {
                            existTags.add(imageTag);
                            imageTagList.put(imageName, existTags);
                            if (imageTagNum.containsKey(imageName)) {
                                Integer num = imageTagNum.get(imageName);
                                imageTagNum.put(imageName, num + 1);
                            }
                        }
                    }
                    Long lastTime = imageNameList.get(imageName);
                    // 厂商：`lastTime >= created`（created 为 null 会拆箱 NPE，照抄）
                    if (lastTime == null || lastTime >= created) {
                        continue;
                    }
                    imageLastVersion.put(imageName, imageTag);
                    imageNameList.put(imageName, created);
                    continue;
                }
                List<String> tagList = new ArrayList<>();
                tagList.add(imageTag);
                imageTagList.put(imageName, tagList);
                imageNameList.put(imageName, created);
                imageTagNum.put(imageName, 1);
                imageLastVersion.put(imageName, imageTag);
            }
        }

        // 厂商：`settingMapper.select(key = 'image.desc')`，结果为空则保持 null
        Map<String, String> imageDescMap = imageDescriptionRepository.queryMap().orElse(null);

        List<ImageWorkloadVO> deployments = imageWorkloadPort.listDeployments();
        Map<String, ImageWorkloadVO> deploymentMap = new HashMap<>();
        for (ImageWorkloadVO deployment : deployments) {
            if (deployment.getName() == null) {
                continue;
            }
            deploymentMap.put(deployment.getName(), deployment);
        }

        List<ImageLogVO> allSuccessLogs = imageLogRepository.querySuccessOrderByUpdatedAtDesc();
        Map<String, String> imageLogMap = new HashMap<>();
        for (ImageLogVO logItem : allSuccessLogs) {
            String key = logItem.getName() + "|" + logItem.getVersion();
            imageLogMap.putIfAbsent(key, logItem.getUsername());
        }

        List<RepositoryItemVO> repositoryItemList = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, Integer> entry : imageTagNum.entrySet()) {
            String imageName = entry.getKey();
            RepositoryItemVO repositoryItem = new RepositoryItemVO();
            repositoryItem.setId(index);
            repositoryItem.setName(imageName);
            repositoryItem.setTagsCount(imageTagNum.get(imageName));
            String lastVersion = imageLastVersion.get(imageName);
            repositoryItem.setLastVersion(lastVersion);
            Long updateTime = imageNameList.get(imageName);
            // 厂商：`Instant.ofEpochMilli(...).atOffset(ZoneOffset.ofHours(8))`（写死 +08:00，不是系统时区）
            repositoryItem.setUpdateTime(OffsetDateTime.ofInstant(Instant.ofEpochMilli(updateTime), ZoneOffset.ofHours(8))
                    .format(TAG_TIME_FORMATTER));

            List<String> tagList = imageTagList.get(imageName);
            WorkloadLoads load = getLoad(tagList, imageName, deployments);
            List<RelateSoftwareInfoVO> relateSoftwareInfoList = new ArrayList<>();
            for (LoadInfo loadInfo : load.loads()) {
                String serviceName = loadInfo.name();
                log.info("镜像名:" + imageName + "被服务:" + serviceName + "在使用中");
                ImageWorkloadVO deployment = deploymentMap.get(serviceName);
                if (deployment == null) {
                    continue;
                }
                RelateSoftwareInfoVO relateSoftwareInfo = new RelateSoftwareInfoVO();
                Map<String, String> annotations = deployment.getTemplateAnnotations();
                if (annotations != null && annotations.containsKey(SERVICE_LABEL_KEY)) {
                    relateSoftwareInfo.setServiceName(annotations.get(SERVICE_LABEL_KEY));
                }
                relateSoftwareInfo.setServiceKey(serviceName);
                relateSoftwareInfoList.add(relateSoftwareInfo);
            }
            if (!relateSoftwareInfoList.isEmpty()) {
                relateSoftwareInfoList = relateSoftwareInfoList.stream()
                        .sorted(Comparator.comparing(RelateSoftwareInfoVO::getServiceKey))
                        .collect(Collectors.toList());
            }
            repositoryItem.setLastUserName(imageLogMap.get(imageName + "|" + lastVersion));
            if (imageDescMap != null) {
                String desc = imageDescMap.get(imageName);
                if (desc != null && !desc.isBlank()) {
                    repositoryItem.setDescription(desc);
                }
            }
            repositoryItem.setRelateSoftwareList(relateSoftwareInfoList);
            repositoryItemList.add(repositoryItem);
            ++index;
        }

        // 厂商：按 `creationTime` 排序 —— 但 creationTime **从不赋值**（永远 null），
        // `nullsLast` 让这次排序变成稳定排序（等于保持 HashMap 迭代序）。照抄。
        List<RepositoryItemVO> sorted = repositoryItemList.stream()
                .sorted(Comparator.comparing(RepositoryItemVO::getCreationTime,
                        Comparator.<String>nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        int skipCount = (normalizedPage - 1) * normalizedSize;
        List<RepositoryItemVO> items = sorted.stream()
                .skip(skipCount)
                .limit(normalizedSize)
                .collect(Collectors.toList());
        return RepositoryListEntity.builder().list(items).total(sorted.size()).build();
    }

    // ------------------------------------------------------------------ tag 列表

    @Override
    public TagListEntity queryImageTagList(Integer page, Integer size, String keyword, String imageName) {
        String project = "";
        if (imageName != null && imageName.contains("/")) {
            int slashIndex = imageName.indexOf('/');
            if (slashIndex != -1) {
                project = imageName.substring(0, slashIndex);
            }
        } else {
            // 厂商拿中文字符串当 i18n 键；MessageResolver 解析不到会**原样返回**该键
            throw new WrongArgumentException("镜像名错误");
        }
        List<ImageItemVO> imageList = dockerRegistryPort.getImageListByProject(project);
        return buildImageTagList(page, size, keyword, imageName, imageList);
    }

    @Override
    public TagListEntity queryImageTagListByImageList(Integer page, Integer size, String keyword, String imageName,
                                                      List<ImageItemVO> imageList) {
        return buildImageTagList(page, size, keyword, imageName, imageList);
    }

    /**
     * 厂商 `getImageTagListByImageList:391-458`。
     * <p>两处刻意照抄：`pushTime` 为空时 `Instant.ofEpochMilli(null)` 会 NPE；
     * 厂商把 tags 计数经 Gson 往返成 `Map<String,Double>` 再 `intValue()`，这里直接用 `Map<String,Integer>`
     * （数值语义等价）。
     */
    private TagListEntity buildImageTagList(Integer page, Integer size, String keyword, String imageName,
                                            List<ImageItemVO> imageList) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        Map<String, Long> tagCreate = new HashMap<>();
        List<String> tagList = new ArrayList<>();
        for (ImageItemVO image : imageList) {
            Long created = image.getCreated();
            for (String repoTag : image.getRepoTags()) {
                if (!repoTag.contains(imageName)) {
                    continue;
                }
                Matcher matcher = REGISTRY_IMAGE_PATTERN.matcher(repoTag);
                if (!matcher.matches()) {
                    log.info("未匹配前缀是原harbor仓库地址ip开头的,该镜像为加载时获取到的镜像");
                    log.info("此时镜像名称为:" + repoTag);
                    continue;
                }
                String group = matcher.group(1);
                log.info("拿到的镜像名称为:" + group);
                String[] split = group.split(":");
                if (split.length <= 1) {
                    continue;
                }
                String name = split[0];
                String imageTag = split[1];
                if (!name.equals(imageName)
                        || (keyword != null && !keyword.isBlank() && !imageTag.contains(keyword))
                        || tagList.contains(imageTag)) {
                    continue;
                }
                tagList.add(imageTag);
                tagCreate.put(imageTag, created);
            }
        }

        String storage = imageName.split("/")[0];
        WorkloadLoads load = getLoad(tagList, imageName, imageWorkloadPort.listDeployments());
        Map<String, Integer> workLoadTags = load.tags();
        int index = 1;
        List<TagItemVO> list = new ArrayList<>();
        for (String tag : tagList) {
            TagItemVO tagItem = new TagItemVO();
            int loads = workLoadTags.containsKey(tag) ? workLoadTags.get(tag) : 0;
            tagItem.setWorkLoad(loads);
            tagItem.setName(tag);
            Long pushTime = tagCreate.get(tag);
            Instant instant = Instant.ofEpochMilli(pushTime);
            log.debug("tag push time: {}", instant);
            tagItem.setPushTime(Date.from(instant));
            tagItem.setId(index);
            tagItem.setRepositoryId(index);
            tagItem.setImmutable(false);
            tagItem.setSigned(false);
            list.add(tagItem);
            ++index;
        }
        log.debug("tags 对应的仓库组为:{}", storage);
        List<TagItemVO> sorted = list.stream()
                .sorted(Comparator.comparing(TagItemVO::getPushTime,
                        Comparator.<Date>nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        int skipCount = (normalizedPage - 1) * normalizedSize;
        List<TagItemVO> items = sorted.stream()
                .skip(skipCount)
                .limit(normalizedSize)
                .collect(Collectors.toList());
        return TagListEntity.builder().list(items).total(sorted.size()).build();
    }

    // ------------------------------------------------------------------ 所有仓库组的镜像

    @Override
    public RepositoryListEntity queryAllProjectImageList(Integer page, Integer size, String keyword) {
        List<ImageGroupVO> imageGroups = imageGroupRepository.queryAll();
        List<RepositoryItemVO> allProjectList = new ArrayList<>();
        for (ImageGroupVO group : imageGroups) {
            if (FILTERED_GROUP.equals(group.getName())) {
                continue;
            }
            String groupName = group.getName();
            RepositoryListEntity repositories = queryRepositories(page, size, keyword, groupName);
            allProjectList.addAll(repositories.getList());
        }
        return RepositoryListEntity.builder().list(allProjectList).total(allProjectList.size()).build();
    }

    // ------------------------------------------------------------------ 上传日志

    @Override
    public ImageLogListEntity queryImageLogList(Integer page, Integer size, String keyword) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        if (keyword != null) {
            keyword = keyword.replaceAll(" +", "");
        }
        String likeKeyword = keyword == null || keyword.isEmpty() ? null : "%" + keyword + "%";
        List<ImageLogVO> rows = imageLogRepository.queryPage(likeKeyword,
                PageUtils.offset(normalizedPage, normalizedSize), normalizedSize);
        long total = imageLogRepository.count(likeKeyword);

        List<ImageLogVO> list = new ArrayList<>();
        for (ImageLogVO logItem : rows) {
            long currentTimeMillis = System.currentTimeMillis();
            long time = currentTimeMillis - logItem.getCreatedAt();
            if (logItem.getStatus() == 2 && time > UPLOAD_TIMEOUT_MILLIS) {
                logItem.setStatus(0);
                String info = logItem.getLog();
                String timeoutInfo = LOG_TIME_FORMATTER.format(ZonedDateTime.now()) + "  上传镜像已超时,请重新上传\n";
                logItem.setLog(info + "\n" + timeoutInfo);
                logItem.setUpdatedAt(currentTimeMillis);
                imageLogRepository.updateSelective(logItem);
            }
            list.add(logItem);
        }
        return ImageLogListEntity.builder().list(list).total(total).build();
    }

    // ------------------------------------------------------------------ 暴露端口（SSH）

    @Override
    public List<ExposedPortItemVO> queryExposedPorts(String name, String tag) {
        List<ExposedPortItemVO> itemList = new ArrayList<>();
        String imageName = dockerRegistryPort.registryHost() + "/" + name;
        String imageExposePort;
        try {
            imageExposePort = sshPort.exec(exposePortCommand(imageName, tag));
        } catch (Exception e) {
            // 厂商：连不上 SSH ⇒ 记日志并返回**空列表**（不是报错）
            log.error("连接nodeIp失败,失败异常信息为:", e);
            log.info("ssh连接服务器失败，获取镜像暴露的内部端口失败");
            return itemList;
        }
        try {
            if (imageExposePort != null && !imageExposePort.isBlank()) {
                Matcher matcher = EXPOSE_PORT_PATTERN.matcher(imageExposePort);
                boolean match = false;
                while (matcher.find()) {
                    ExposedPortItemVO portItem = new ExposedPortItemVO();
                    match = true;
                    String port = matcher.group(1);
                    String protocol = matcher.group(2);
                    portItem.setPort(Integer.parseInt(port));
                    if (protocol != null && !protocol.isBlank()) {
                        portItem.setProtocol(protocol);
                    } else {
                        portItem.setProtocol("tcp");
                    }
                    itemList.add(portItem);
                }
                if (!match) {
                    // 真正生效的分支：输出形如 "80/tcp,443/tcp,"（SSH 命令末尾 tr '\n' ','）
                    String[] split = imageExposePort.split(",");
                    Set<String> unique = new HashSet<>();
                    for (String portInfo : split) {
                        if (unique.contains(portInfo)) {
                            continue;
                        }
                        ExposedPortItemVO portItem = new ExposedPortItemVO();
                        String[] ports = portInfo.split("/");
                        if (ports.length > 1) {
                            portItem.setPort(Integer.parseInt(ports[0]));
                            portItem.setProtocol(ports[1]);
                        } else {
                            portItem.setPort(Integer.parseInt(ports[0]));
                            portItem.setProtocol("tcp");
                        }
                        itemList.add(portItem);
                        unique.add(portInfo);
                    }
                }
            }
        } catch (Exception e) {
            log.error("请求ssh获取镜像内部暴露端口失败,异常为:", e);
        }
        return itemList;
    }

    /** 厂商 `SshUtil.getImageExposePort` 的原样命令。 */
    private static String exposePortCommand(String imageName, String tag) {
        return String.format("docker history %s:%s | awk '/EXPOSE/ {gsub(/.*EXPOSE[[:space:]]+|[[:space:]]+0B.*/, \"\");"
                + " gsub(/[[:space:]]+/, \"/\"); print}' | tr '\\n' ','", imageName, tag);
    }

    // ------------------------------------------------------------------ ENV（docker inspect）

    @Override
    public List<ImageEnvInfoVO> queryImageEnv(String name, String tag) {
        String imageTag = dockerRegistryPort.registryHost() + "/" + name + ":" + tag;
        String[] env = dockerImagePort.inspectImageEnv(imageTag);
        if (env == null) {
            return List.of();
        }
        List<ImageEnvInfoVO> result = new ArrayList<>(env.length);
        for (String item : env) {
            // 厂商：`env.split("=")` 后直接取 [1] —— 没有 '=' 的 env 会抛 ArrayIndexOutOfBounds，照抄
            String[] envArray = item.split("=");
            result.add(new ImageEnvInfoVO(envArray[0], envArray[1]));
        }
        return result;
    }

    // ------------------------------------------------------------------ 服务关联镜像

    @Override
    public List<ImageInfoVO> queryServiceRelationImage() {
        List<ImageWorkloadVO> deployments;
        try {
            deployments = imageWorkloadPort.listDeployments();
        } catch (RuntimeException e) {
            log.error("获取服务关联的镜像列表出错，异常为：", e);
            throw new WrongArgumentException("service.get.service.image.list.error");
        }
        List<ImageWorkloadVO> withoutGaopDeployment = new ArrayList<>();
        for (ImageWorkloadVO deployment : deployments) {
            if (deployment == null || deployment.getLabels() == null) {
                continue;
            }
            Map<String, String> labels = deployment.getLabels();
            if (labels.get(INGRESS_LB_LABEL) != null) {
                continue;
            }
            String appType = labels.get(APP_TYPE_LABEL);
            if (appType != null) {
                if (APP_TYPE_BUSINESS.equals(appType)) {
                    withoutGaopDeployment.add(deployment);
                }
                continue;
            }
            if (deployment.getAnnotations() == null) {
                withoutGaopDeployment.add(deployment);
                continue;
            }
            String displayName = deployment.getAnnotations().get(DISPLAY_NAME_ANNOTATION);
            if (displayName != null && DEFAULT_PROJECT_NAME.equals(displayName)) {
                continue;
            }
            String appName = labels.get(APP_NAME_LABEL);
            boolean isGaopApp = appName != null && DEFAULT_PROJECT_NAME.equals(appName);
            if (!isGaopApp) {
                withoutGaopDeployment.add(deployment);
            }
        }

        LinkedHashSet<ImageInfoVO> imageInfoSet = new LinkedHashSet<>();
        for (ImageWorkloadVO deployment : withoutGaopDeployment) {
            String name = deployment.getName();
            log.info("获取服务名称:" + name);
            List<String> containerImages = deployment.getContainerImages();
            if (containerImages == null || containerImages.isEmpty()) {
                continue;
            }
            String image = containerImages.get(0);
            log.info("镜像信息为:" + image);
            boolean existTag;
            String group;
            String imageName;
            String tag;
            String[] split = image.split("/");
            if (split.length <= 2) {
                String first = split[0];
                String[] imageByOne = first.split(":");
                imageName = imageByOne.length >= 1 ? imageByOne[0] : null;
                tag = imageByOne.length >= 2 ? imageByOne[1] : null;
                group = DEFAULT_PROJECT_NAME;
                existTag = existImageTag(group + "/" + imageName, tag);
            } else {
                group = split[1];
                log.info("获取到服务关联的镜像仓库组为:" + group);
                String imageNameAndTag = split[2];
                log.info("获取到服务关联的镜像为:" + imageNameAndTag);
                String[] nameAndTag = imageNameAndTag.split(":");
                imageName = nameAndTag[0];
                tag = nameAndTag[1];
                existTag = existImageTag(group + "/" + imageName, tag);
            }
            if (!existTag) {
                continue;
            }
            imageInfoSet.add(ImageInfoVO.builder().group(group).imageName(imageName).tag(tag).build());
        }
        return new ArrayList<>(imageInfoSet);
    }

    // ------------------------------------------------------------------ exist / existTag

    @Override
    public List<TagItemVO> existRepositories(String name, String projectName) {
        String target = name;
        boolean startsWith = target.startsWith(projectName + "/");
        if (!startsWith) {
            target = projectName + "/" + target;
        }
        List<TagItemVO> list = queryImageTagList(1, Integer.MAX_VALUE, null, target).getList();
        if (list == null || list.isEmpty()) {
            throw new WrongArgumentException("image.not.found.upload.first");
        }
        log.debug("image:{}的标签信息为:{}", target, list);
        return list;
    }

    @Override
    public boolean existImageTag(String imageName, String tag) {
        TagListEntity imageTagList = queryImageTagList(1, Integer.MAX_VALUE, tag, imageName);
        return imageTagList.getList() != null && !imageTagList.getList().isEmpty();
    }

    // ------------------------------------------------------------------ getLoad（厂商 K3sImageServiceImpl:759-804）

    /**
     * 厂商 `getLoad`：对每个 tag，找出"容器镜像里包含 `<imageName>:<tag>`"的 Deployment，
     * 再在其中逐容器比对**镜像名末尾的 tag**；命中就记一条 load，并给该 tag 计数 +1。
     * <p>`loads` 是**跨 tag 累积**的同一个列表（厂商在 lambda 里反复 `setLoads(loads)`）。
     */
    private WorkloadLoads getLoad(List<String> tagNameList, String image, List<ImageWorkloadVO> deployments) {
        List<LoadInfo> loads = new ArrayList<>();
        Map<String, Integer> tags = new HashMap<>();
        for (String tag : tagNameList) {
            List<ImageWorkloadVO> deploymentFilterList = new ArrayList<>();
            for (ImageWorkloadVO candidate : deployments) {
                boolean flag = false;
                List<String> containers = candidate.getContainerImages();
                if (containers != null) {
                    for (String containerImage : containers) {
                        if (containerImage == null || !containerImage.contains(image + ":" + tag)) {
                            continue;
                        }
                        flag = true;
                        break;
                    }
                }
                if (flag) {
                    deploymentFilterList.add(candidate);
                }
            }
            for (ImageWorkloadVO deployment : deploymentFilterList) {
                List<String> containers = deployment.getContainerImages();
                if (containers == null) {
                    continue;
                }
                for (String containerImage : containers) {
                    if (containerImage == null) {
                        continue;
                    }
                    String tagName = containerImage.substring(containerImage.lastIndexOf(":") + 1);
                    if (!tagName.equals(tag)) {
                        continue;
                    }
                    LoadInfo loadInfo = new LoadInfo(deployment.getName(), deployment.getNamespace(), tagName,
                            "deploy", "Default-K8s", deployment.getCreationTimestamp());
                    loads.add(loadInfo);
                    tags.merge(tag, 1, Integer::sum);
                    break;
                }
            }
        }
        return new WorkloadLoads(loads, tags);
    }

    /** 厂商 `LoadInfo` 的裁剪版（只用到这些字段）。 */
    private record LoadInfo(String name, String projectName, String tag, String type, String clusterName,
                            Long createTime) {
    }

    /** 厂商 `WorkLoad` 的裁剪版：`loads` 与 `tags`。 */
    private record WorkloadLoads(List<LoadInfo> loads, Map<String, Integer> tags) {
    }
}
