package com.csm.paas.core.domain.image;

import com.csm.paas.core.domain.image.adapter.port.IDockerImagePort;
import com.csm.paas.core.domain.image.adapter.port.IDockerRegistryPort;
import com.csm.paas.core.domain.image.adapter.port.IImageWorkloadPort;
import com.csm.paas.core.domain.image.adapter.repository.IImageDescriptionRepository;
import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.adapter.repository.IImageLogRepository;
import com.csm.paas.core.domain.image.model.entity.ImageLogListEntity;
import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;
import com.csm.paas.core.domain.image.service.impl.ImageCatalogQueryServiceImpl;
import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 镜像上传日志（厂商 `DockerService.getImageList:172-197`）。
 *
 * <p>这是本轮新增的 /v1/image 端点里**唯一可以离线验证的读取路径**（纯库 `g_image_log`）：
 * 另外那些（repositories/tags/tagInfo/exist/imageList/download/image_env/expose）都依赖
 * registry/docker daemon/SSH ⇒ 类 C（`docs/findings/79`、`81`）。
 */
class ImageLogListTest {

    private final IDockerRegistryPort dockerRegistryPort = mock(IDockerRegistryPort.class);
    private final IDockerImagePort dockerImagePort = mock(IDockerImagePort.class);
    private final IImageWorkloadPort imageWorkloadPort = mock(IImageWorkloadPort.class);
    private final IImageLogRepository imageLogRepository = mock(IImageLogRepository.class);
    private final IImageDescriptionRepository imageDescriptionRepository = mock(IImageDescriptionRepository.class);
    private final IImageGroupRepository imageGroupRepository = mock(IImageGroupRepository.class);
    private final ISshPort sshPort = mock(ISshPort.class);

    private final ImageCatalogQueryServiceImpl service = new ImageCatalogQueryServiceImpl(
            dockerRegistryPort, dockerImagePort, imageWorkloadPort, imageLogRepository,
            imageDescriptionRepository, imageGroupRepository, sshPort);

    private static ImageLogVO log(long id, int status, long createdAt, String log) {
        return ImageLogVO.builder().id(id).fileName("a.tar.gz").status(status).createdAt(createdAt).log(log).build();
    }

    /** `status == 2` 且超过 30 分钟 ⇒ 改成 0、追加超时说明、写回（厂商的副作用）。 */
    @Test
    void flipsStaleProcessingLogToFailed() {
        long now = System.currentTimeMillis();
        ImageLogVO stale = log(7L, 2, now - 1_800_001L, "开始处理上传镜像");
        when(imageLogRepository.queryPage(any(), anyInt(), anyInt())).thenReturn(List.of(stale));
        when(imageLogRepository.count(any())).thenReturn(1L);

        ImageLogListEntity result = service.queryImageLogList(1, 20, null);

        assertEquals(1, result.getList().size());
        assertEquals(0, result.getList().get(0).getStatus().intValue(), "超时的处理中行被改成失败");
        assertEquals(stale.getCreatedAt(), result.getList().get(0).getCreatedAt(), "createdAt 不动");
        assertTrue(result.getList().get(0).getLog().startsWith("开始处理上传镜像\n"), "旧日志保留 + 换行");
        assertTrue(result.getList().get(0).getLog().contains("上传镜像已超时,请重新上传"), "追加厂商原文");
        assertEquals(1L, result.getTotal().longValue(), "total 来自 count()，不是当页条数");
        verify(imageLogRepository).updateSelective(stale);
    }

    /** 未超时的处理中行、以及已成功/已失败的行都不动。 */
    @Test
    void keepsFreshOrFinishedLogsUntouched() {
        long now = System.currentTimeMillis();
        ImageLogVO fresh = log(1L, 2, now, "处理中");
        ImageLogVO success = log(2L, 1, now - 10_000_000L, "成功");
        when(imageLogRepository.queryPage(any(), anyInt(), anyInt())).thenReturn(List.of(fresh, success));
        when(imageLogRepository.count(any())).thenReturn(2L);

        ImageLogListEntity result = service.queryImageLogList(1, 20, null);

        assertEquals(2, result.getList().size());
        assertEquals(2, result.getList().get(0).getStatus().intValue(), "没超时就不动");
        assertEquals(1, result.getList().get(1).getStatus().intValue(), "已成功的行不动");
        assertEquals("处理中", result.getList().get(0).getLog(), "日志不变");
        verify(imageLogRepository, never()).updateSelective(any());
    }

    /** 控制器/服务都会 `replaceAll(" +","")`，再包成 `%kw%`；分页默认 page=1,size=20,offset=0。 */
    @Test
    void stripsSpacesFromKeywordAndNormalizesPaging() {
        when(imageLogRepository.queryPage(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(imageLogRepository.count(any())).thenReturn(0L);

        service.queryImageLogList(null, null, "  a b ");

        verify(imageLogRepository).queryPage(eq("%ab%"), eq(0), eq(20));
    }

    /** `total` 取满足条件的**总行数**（PageHelper 的 count），与当页条数无关。 */
    @Test
    void totalIsTheCountNotThePageSize() {
        long now = System.currentTimeMillis();
        when(imageLogRepository.queryPage(any(), anyInt(), anyInt())).thenReturn(List.of(log(1L, 1, now, "ok")));
        when(imageLogRepository.count(any())).thenReturn(42L);

        ImageLogListEntity result = service.queryImageLogList(2, 5, "nginx");

        assertEquals(42L, result.getTotal().longValue());
        assertEquals(1, result.getList().size());
        verify(imageLogRepository).queryPage(eq("%nginx%"), eq(5), eq(5));
    }
}
