package com.csm.paas.core.domain.node.service;

import com.csm.paas.core.domain.cluster.model.valobj.PubResourceSpecVO;

/**
 * 公共 API - 更新服务资源配置（厂商 `controller/pub/PublicFdfsController.patchServiceResource:196-202`
 * → `service/FileService.patchServiceResource:1138-1233`）。
 *
 * <p>放在 `domain/node` 下同 {@code IPubServiceQueryService} 的理由（文件白名单）。
 */
public interface IPubResourcePatchService {

    /**
     * 厂商 `FileService.patchServiceResource:1138-1183`：
     * <ol>
     *   <li>先读 **Deployment**（`K8s.NAMESPACE` 下按 `name`）；**读不到就整段回退 StatefulSet**；</li>
     *   <li>Deployment 分支：给**第一个容器**换 resources（limits 写 `cpu`/`memory`，`gpu` 非空时额外写
     *       `nvidia.com/gpu`；requests 只写 `cpu`/`memory`），再把滚动策略设成
     *       `RollingUpdate{maxSurge:0,maxUnavailable:1}`，最后整份 merge-patch；</li>
     *   <li>StatefulSet 分支：同样换 resources；策略改成
     *       `type=RollingUpdate` + `rollingUpdate.maxUnavailable=1`
     *       （**`maxSurge=0` 在厂商那里是死代码，不写**，`FileService:1218`）。</li>
     * </ol>
     *
     * <p>失败契约（照抄）：
     * <ul>
     *   <li>Deployment **或** StatefulSet 都读不到 ⇒ 400 `service.get.service.info.update.resource.failed`；</li>
     *   <li>任一次 patch 失败 ⇒ 400 `k8s.call.api.failed.to.change.deploy`。</li>
     * </ul>
     *
     * @return 恒 true（失败走异常）；厂商的返回类型是 `Boolean`，但从未返回 false
     */
    boolean patchServiceResource(PubResourceSpecVO spec);
}
