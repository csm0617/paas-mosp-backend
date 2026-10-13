package com.csm.paas.core.cases.software;

import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;

/** 新增软件的用例（`POST /v1/software`）。 */
public interface ISoftwareWriteCase {

    /**
     * 新增软件：校验与派生在领域层（`ISoftwareCommandService.prepareNewSoftware`），
     * **插行 + 战位关联写入在一个事务里**。
     *
     * <p>厂商 `addSoftWare`（`SoftwareService.java:607-647`）没有 `@Transactional`
     * （controller 上有，所以整体仍是事务）；关系部分：`roleListKey` 是**战位 key 列表**，
     * 逐个反查 id、找不到跳过，然后 `softwareRelateRole`（**已存在的关联跳过**，`order` = 该战位已有软件数 + 1）。
     * <p>`roleListKey` 为 null 时厂商会 NPE（缺陷，它只判了 `.size()`）；我们按空列表处理。
     */
    SoftwareEntity addSoftware(SoftwareFormVO form);

    /**
     * 删除软件（`DELETE /v1/software/{id}`），**整个用例一个事务**。
     * <p>厂商的删除顺序（`SoftwareController.removeSoftWare:388-395` + `SoftwareService.removeSoftware:1520-1533`）：
     * `g_role_software`（按 app_id）→ `g_software_app` → `g_service_port` → `g_software` 主行；
     * 其中删 `g_service_port` 有行时厂商会 `notificationGateway()`（起线程通知网关，未实现，见 findings/39）。
     * <p>软件不存在 → `software.not.exist.by.id`（HTTP 400）。
     * <p><b>我们与厂商的一处收紧</b>：厂商这条链路**没有事务**，中途失败会留下半删状态；我们把整个用例包进一个事务。
     * <p>厂商 `removeSoftware` 还会删 i18n 自动名（`deleteAutoNameMessage`），本环境是 no-op，待切片 5 回补。
     */
    void removeSoftware(String softwareId);

    /**
     * 修改软件（`PUT /v1/software/{id}`）：校验与"表单没带的字段会被置 null"在领域层，
     * **整行更新在一个事务里**。
     * <p>厂商返回的是**重新查出来的 `SoftwareInfo`（23 键）**，不是 Software；
     * 且响应的 `nameUs`/`nameFr` 取自**表单**（非空则用表单值，空则回退 name），不是详情的默认回退。
     * <p><b>未实现</b>：CS 类型的 WebSocket 通知、BS 类型改 key 后的服务重关联。
     */
    com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity editSoftware(
            String softwareId, SoftwareFormVO form);
}
