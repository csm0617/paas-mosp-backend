package com.csm.paas.core.domain.image;

import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.model.valobj.ImageGroupVO;
import com.csm.paas.core.domain.image.service.impl.ImageQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目列表（厂商 `K3sImageServiceImpl.projectList:200-250`）。**数据源是库 `g_group`，不是 Harbor**。
 * <p>三种情形已**实测对拉一致**（`findings/80`）：库里有 3 行内置项目时返回 2 条（过滤 `system_containers`）、
 * 带 keyword、以及 `create_date` 按**秒**解释产生的畸形年份 `+58753-…`。
 */
class ImageProjectListTest {

    private final IImageGroupRepository repository = mock(IImageGroupRepository.class);

    private static ImageGroupVO group(int id, String name, long createDate) {
        return ImageGroupVO.builder().id(id).name(name).createDate(createDate).build();
    }

    @Test
    void filtersOnlySystemContainersAndKeepsDatabaseOrder() {
        when(repository.queryPage(any(), anyInt(), anyInt())).thenReturn(List.of(
                group(3, "library", 1791908594240L),
                group(2, "gaop", 1791908594220L),
                group(1, "system_containers", 1791908594120L)));

        var result = new ImageQueryServiceImpl(repository).queryProjectList(1, 20, null);

        assertEquals(List.of("library", "gaop"), result.getList().stream().map(v -> v.getName()).toList(),
                "只过滤 system_containers（没有 system_store）；顺序沿用库里的 create_date 倒序");
        assertEquals(2, result.getTotal(), "total = 过滤后的条数");
        verify(repository, never()).insert(anyString(), anyLong());
    }

    /** `create_date` 是**毫秒**却被按**秒**解释 ⇒ 年份爆到 58753（厂商缺陷，照抄）。 */
    @Test
    void creationTimeInterpretsMillisAsSeconds() {
        when(repository.queryPage(any(), anyInt(), anyInt()))
                .thenReturn(List.of(group(2, "gaop", 1791908594220L)));

        var item = new ImageQueryServiceImpl(repository).queryProjectList(1, 20, null).getList().get(0);

        assertTrue(item.getCreationTime().startsWith("+58753-"), "实测厂商就是 +58753-04-29…：" + item.getCreationTime());
        assertEquals(2, item.getProjectId());
        assertEquals("gaop", item.getName());
        assertEquals(null, item.getOwnerName(), "厂商只设 3 个字段，其余保持 null");
    }

    /** 库为空 ⇒ 取/建三个内置项目（**会写库**），再过滤掉 system_containers。 */
    @Test
    void emptyTableCreatesBuiltInGroups() {
        when(repository.queryPage(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.queryByName("system_containers")).thenReturn(List.of());
        when(repository.queryByName("gaop")).thenReturn(List.of());
        when(repository.queryByName("library")).thenReturn(List.of());
        when(repository.insert(anyString(), anyLong()))
                .thenAnswer(i -> group(9, i.getArgument(0), i.getArgument(1)));

        var result = new ImageQueryServiceImpl(repository).queryProjectList(1, 20, null);

        assertEquals(3, result.getList().size() + 1, "建了 3 个，过滤掉 system_containers 后剩 2");
        assertEquals(List.of("gaop", "library"), result.getList().stream().map(v -> v.getName()).toList());
        verify(repository, org.mockito.Mockito.times(3)).insert(anyString(), anyLong());
    }
}
