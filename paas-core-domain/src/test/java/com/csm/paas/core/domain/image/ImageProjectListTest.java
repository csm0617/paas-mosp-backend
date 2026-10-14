package com.csm.paas.core.domain.image;

import com.csm.paas.core.domain.image.adapter.port.IHarborPort;
import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;
import com.csm.paas.core.domain.image.service.impl.ImageQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Harbor 项目列表的三步规则（厂商 `K8sImageServiceImpl.projectList:107-119`）。
 * <p>样本取自**厂商实测响应**（`runtime-samples/v1_image_projectList.json`：`library`/`gaop` 两个项目）。
 */
class ImageProjectListTest {

    private final IHarborPort harborPort = mock(IHarborPort.class);

    private static ProjectItemVO project(String name, String creationTime) {
        return ProjectItemVO.builder().name(name).creationTime(creationTime).projectId(1).build();
    }

    @Test
    void filtersBuiltInProjectsAndSortsByCreationTimeDescending() {
        when(harborPort.listAllProjects(any())).thenReturn(List.of(
                project("system_containers", "+58753-04-29T03:18:00+08:00"),
                project("library", "+58753-04-29T03:17:20+08:00"),
                project("gaop", "+58753-04-29T03:17:00+08:00"),
                project("system_store", "+58753-04-29T03:19:00+08:00")));

        var list = new ImageQueryServiceImpl(harborPort).queryProjectList(1, 20, null);

        assertEquals(List.of("library", "gaop"), list.stream().map(ProjectItemVO::getName).toList(),
                "内置项目被过滤；其余按 creation_time 倒序");
    }

    @Test
    void totalIsCountedBeforePaging() {
        when(harborPort.listAllProjects(any())).thenReturn(List.of(
                project("a", "2024-01-03"), project("b", "2024-01-02"), project("c", "2024-01-01")));

        var service = new ImageQueryServiceImpl(harborPort);

        assertEquals(3, service.countProjectList(null), "total 是分页前的总数");
        assertEquals(2, service.queryProjectList(1, 2, null).size());
        assertEquals(List.of("c"), service.queryProjectList(2, 2, null).stream().map(ProjectItemVO::getName).toList());
    }

    @Test
    void keywordIsPassedThroughToHarbor() {
        when(harborPort.listAllProjects("gaop")).thenReturn(List.of(project("gaop", "2024-01-01")));

        assertTrue(new ImageQueryServiceImpl(harborPort).queryProjectList(1, 20, "gaop").size() == 1);
    }
}
