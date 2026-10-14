package com.csm.paas.core.domain.image.service.impl;

import com.csm.paas.core.domain.image.adapter.port.IHarborPort;
import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;
import com.csm.paas.core.domain.image.service.IImageQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Harbor 项目列表（规则见 {@link IImageQueryService}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageQueryServiceImpl implements IImageQueryService {

    /** 厂商 `projectList` 里被过滤掉的两个内置项目。 */
    private static final List<String> BUILT_IN_PROJECTS = List.of("system_containers", "system_store");

    private final IHarborPort harborPort;

    @Override
    public List<ProjectItemVO> queryProjectList(int page, int size, String keyword) {
        List<ProjectItemVO> filtered = filtered(keyword);
        // 厂商：Comparator.comparing(ProjectItem::getCreationTime).reversed() —— **字符串比较**，照抄
        List<ProjectItemVO> sorted = filtered.stream()
                .sorted(Comparator.comparing(ProjectItemVO::getCreationTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        long skip = (long) (page - 1) * size;
        return sorted.stream().skip(Math.max(0, skip)).limit(size).toList();
    }

    @Override
    public long countProjectList(String keyword) {
        return filtered(keyword).size();
    }

    private List<ProjectItemVO> filtered(String keyword) {
        List<ProjectItemVO> all = harborPort.listAllProjects(keyword);
        List<ProjectItemVO> result = all.stream()
                .filter(p -> !BUILT_IN_PROJECTS.contains(p.getName()))
                .toList();
        log.info("Harbor 项目：拉取 {} 个，过滤内置项目后 {} 个", all.size(), result.size());
        return result;
    }
}
