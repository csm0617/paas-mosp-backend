package com.csm.paas.core.domain.node.model.valobj;

import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * `GET /v1/service/serviceInfoByNode` 列表里的一项（厂商 `ServicesItem` 在该端点被赋值的那些字段）。
 *
 * <p>厂商出处：`ServiceController.getServicesItems:1730-1810` + `getServiceItemInfo:1812-1873`。
 *
 * <p><b>为什么放在 node 域</b>：本次改动的文件白名单只允许新增 `domain/node/**` 与 `api/dto` 下的类型，
 * 不允许再往 `domain/software/model` 加东西；而这个视图本质是"某节点上的服务 + 监控补齐"，
 * 归在"节点资源"域下也说得通（厂商那边它就是 `ServiceController` 与 `ComputerService` 的交叉点）。
 *
 * <p>K8s 原样快照那部分（service/deployment/serviceAuth/imageInfo/hostIP 等）不在这个 VO 里，
 * 由 {@link #runtime} 承载（厂商的 `ServicesItem` 一整份来自 `tenxCloud.allPageService(null)`）。
 *
 * <p><b>类 C：本环境不可验证</b>——每服务的 cpu/memory 补齐依赖外部监控后端（Prometheus 的
 * 24 小时范围查询），本机不可达。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceNodeItemVO {

    /**
     * 服务本体（名字、快照、授权、镜像等）。
     * <p>厂商这里的数据源是 `tenxCloud.allPageService(null)`；本平台没有该客户端（`findings/79`、C3 更正），
     * 用**同集群同命名空间的 Deployment/Servce 直读**等价复刻（与 `GET /v1/service` 同一份来源）。
     */
    private ServiceRuntimeEntity runtime;

    /** 排序用的资源申请量：cpu 单位 **m**（`requests.cpu` 不是 `m` 结尾时 ×1024）。 */
    private Double cpuOrder;

    /** 排序用的资源申请量：memory 单位 **MiB**（`Gi` 结尾时 ×1024）。 */
    private Double memoryOrder;

    /** `limits["nvidia.com/gpu"]`（没有则保持 0.0）。 */
    private Double gpuOrder;

    /** 该服务所有 pod 里**最大**的重启次数。 */
    private Integer restartCount;

    /** 按 pod 聚合的 cpu/memory 统计；**恒为 1 项**（厂商只挂一个对象，`podName` = Deployment 名）。 */
    private List<ServiceNodePodVO> podInfoList;

    /** 该服务的 pod 落在哪些节点上（去重，顺序不保证）。 */
    private List<String> belongNodeList;
}
