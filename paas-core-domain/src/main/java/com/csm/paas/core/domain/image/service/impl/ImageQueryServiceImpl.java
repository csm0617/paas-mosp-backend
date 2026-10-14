package com.csm.paas.core.domain.image.service.impl;

import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.model.entity.ProjectListEntity;
import com.csm.paas.core.domain.image.model.valobj.ImageGroupVO;
import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;
import com.csm.paas.core.domain.image.service.IImageQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 项目列表（规则见 {@link IImageQueryService}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageQueryServiceImpl implements IImageQueryService {

    /** 表为空时厂商会取/建这三个内置项目（顺序也照抄）。 */
    private static final List<String> BUILT_IN_GROUPS = List.of("system_containers", "gaop", "library");
    /** 厂商**只**过滤掉这一个（没有 system_store）。 */
    private static final String FILTERED_GROUP = "system_containers";
    /** 厂商的时间格式：`yyyy-MM-dd'T'HH:mm:ssXXX`，时区写死 `Asia/Shanghai`。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final IImageGroupRepository imageGroupRepository;

    @Override
    public ProjectListEntity queryProjectList(int page, int size, String keyword) {
        String likeName = (keyword == null || keyword.isEmpty()) ? null : "%" + keyword + "%";
        List<ImageGroupVO> rows = imageGroupRepository.queryPage(
                likeName, com.csm.paas.core.types.util.PageUtils.offset(page, size), size);

        List<ImageGroupVO> groups = new ArrayList<>();
        if (rows.isEmpty()) {
            // 厂商：查询为空才取/建内置项目（注意这会写库）
            for (String name : BUILT_IN_GROUPS) {
                createIfAbsent(name).ifPresent(groups::add);
            }
        }
        groups.addAll(rows);

        List<ProjectItemVO> list = groups.stream()
                .filter(g -> !FILTERED_GROUP.equals(g.getName()))
                .map(ImageQueryServiceImpl::toItem)
                .toList();
        log.info("镜像项目：库内 {} 条，内置 {} 条，过滤后 {} 条", rows.size(), groups.size() - rows.size(), list.size());
        return ProjectListEntity.builder().list(list).total(list.size()).build();
    }

    /** 厂商 `createProjectNoException`：查不到就插入（`createDate = now` 毫秒）。 */
    private java.util.Optional<ImageGroupVO> createIfAbsent(String name) {
        List<ImageGroupVO> found = imageGroupRepository.queryByName(name);
        if (!found.isEmpty()) {
            return java.util.Optional.of(found.get(0));
        }
        ImageGroupVO created = imageGroupRepository.insert(name, System.currentTimeMillis());
        return java.util.Optional.ofNullable(created);
    }

    /**
     * 厂商映射：**只设 3 个字段**（其余保持 null）；`createDate` 按**秒**解释
     * （`Instant.ofEpochSecond`）——毫秒时间戳会被解释成公元 58753 年，**这是厂商缺陷，照抄**。
     */
    private static ProjectItemVO toItem(ImageGroupVO g) {
        String creationTime = OffsetDateTime
                .ofInstant(Instant.ofEpochSecond(g.getCreateDate() == null ? 0L : g.getCreateDate()), ZONE)
                .format(FORMATTER);
        return ProjectItemVO.builder()
                .projectId(g.getId())
                .name(g.getName())
                .creationTime(creationTime)
                .build();
    }
}
