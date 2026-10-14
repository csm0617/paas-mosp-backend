package com.csm.paas.core.domain.image.service.impl;

import com.csm.paas.core.domain.image.adapter.port.IDockerImagePort;
import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.adapter.repository.IImageDescriptionRepository;
import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.model.entity.TagListEntity;
import com.csm.paas.core.domain.image.model.valobj.ImageGroupVO;
import com.csm.paas.core.domain.image.model.valobj.ImageItemVO;
import com.csm.paas.core.domain.image.model.valobj.TagItemVO;
import com.csm.paas.core.domain.image.service.IImageCatalogQueryService;
import com.csm.paas.core.domain.image.service.IImageCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 镜像域写路径：逐行照抄厂商 `recovered/gaop-be/.../service/impl/K3sImageServiceImpl.java`。
 *
 * <p>`createProject` / `deleteProjectThrow` / `updateImageDescription` 是**纯库写入**（可在线验证）；
 * `deleteRepositories` / `deleteImageByTag` 依赖 Registry + docker daemon ⇒ **类 C**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCommandServiceImpl implements IImageCommandService {

    private final IImageGroupRepository imageGroupRepository;
    private final IImageDescriptionRepository imageDescriptionRepository;
    private final IDockerRegistryPort dockerRegistryPort;
    private final IDockerImagePort dockerImagePort;
    private final IImageCatalogQueryService imageCatalogQueryService;

    @Override
    public boolean createProject(String name) {
        // 厂商 `isLikeWhenPresent(name)`：不带通配符时就是等值；名字里带 %/_ 时仍是通配符（照抄）
        List<ImageGroupVO> groupList = imageGroupRepository.queryByName(name);
        if (!groupList.isEmpty()) {
            throw new WrongArgumentException("image.duplicate.repository.group", name);
        }
        ImageGroupVO created = imageGroupRepository.insert(name, System.currentTimeMillis());
        return created != null;
    }

    @Override
    public boolean deleteProjectThrow(Integer projectId) {
        ImageGroupVO imageGroup = imageGroupRepository.queryById(projectId);
        if (imageGroup == null) {
            log.info("仓库组id:{}不存在", projectId);
            throw new WrongArgumentException("image.group.not.found");
        }
        imageGroupRepository.deleteById(projectId);
        return true;
    }

    @Override
    public boolean updateImageDescription(String repoId, String description) {
        // 厂商：`select` 结果为空 ⇒ `new HashMap<>()` 只放这一条；否则解析出来 put 后再整段写回
        Map<String, String> descInfo = imageDescriptionRepository.queryMap().orElseGet(HashMap::new);
        descInfo.put(repoId, description);
        imageDescriptionRepository.saveMap(descInfo, System.currentTimeMillis());
        return true;
    }

    @Override
    public void deleteRepositories(List<String> imageNames) {
        Map<String, List<ImageItemVO>> imageListMap = new HashMap<>();
        for (String image : imageNames) {
            String project = "";
            if (image != null && image.contains("/")) {
                int slashIndex = image.indexOf('/');
                if (slashIndex != -1) {
                    project = image.substring(0, slashIndex);
                }
            } else {
                throw new WrongArgumentException("镜像名错误");
            }
            List<ImageItemVO> imageList = imageListMap.get(project);
            if (imageList == null) {
                imageList = dockerRegistryPort.getImageListByProject(project);
                imageListMap.put(project, imageList);
            }
            TagListEntity imageTagList =
                    imageCatalogQueryService.queryImageTagListByImageList(1, Integer.MAX_VALUE, null, image, imageList);
            List<TagItemVO> list = imageTagList.getList();
            String host = dockerRegistryPort.registryHost();
            String preImage = host + "/" + image;
            for (TagItemVO tagItem : list) {
                String imageTag = preImage + ":" + tagItem.getName();
                log.info("开始删除镜像:" + imageTag);
                try {
                    // 厂商顺序：先 `docker rmi`，再取 digest、删 manifest
                    dockerImagePort.removeImage(imageTag);
                    String imageDigest = dockerRegistryPort.getImageDigest(image, tagItem.getName());
                    if (imageDigest != null) {
                        dockerRegistryPort.deleteManifest(image, imageDigest);
                    }
                    log.info("删除完成");
                } catch (RuntimeException e) {
                    // 厂商只捕获 NotFoundException；这里放宽到 RuntimeException（端口内部也会吞）
                    log.error("找不到镜像:{}", imageTag, e);
                }
            }
        }
        for (String project : imageListMap.keySet()) {
            dockerRegistryPort.evictImageListCache(project);
        }
    }

    @Override
    public boolean deleteImageByTag(String name, List<String> tagList) {
        String host = dockerRegistryPort.registryHost();
        TagListEntity imageTagList = imageCatalogQueryService.queryImageTagList(1, Integer.MAX_VALUE, null, name);
        List<TagItemVO> list = imageTagList.getList();
        List<String> existTagList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            existTagList = list.stream().map(TagItemVO::getName).collect(Collectors.toList());
        }
        List<String> existTags = existTagList.stream().distinct().filter(tagList::contains).collect(Collectors.toList());
        if (!existTags.isEmpty()) {
            for (String tag : existTags) {
                String imageTag = host + "/" + name + ":" + tag;
                log.info("开始删除镜像:{}", imageTag);
                try {
                    String imageDigest = dockerRegistryPort.getImageDigest(name, tag);
                    if (imageDigest != null) {
                        dockerRegistryPort.deleteManifest(name, imageDigest);
                    }
                    // 厂商：`new Thread(() -> dockerClient.removeImageCmd(imageTag).exec()).start()`
                    String tagToRemove = imageTag;
                    new Thread(() -> dockerImagePort.removeImage(tagToRemove)).start();
                    log.info("{}删除完成", imageTag);
                } catch (Exception e) {
                    log.error("找不到镜像:{}", imageTag, e);
                }
            }
            String project = name.contains("/") ? name.substring(0, name.indexOf("/")) : name;
            dockerRegistryPort.evictImageListCache(project);
        }
        return true;
    }
}
