package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;

/** 软件目录查询服务（K2：`GET /v1/software`）。 */
public interface ISoftwareQueryService {

    /**
     * 软件列表（厂商 `GET /v1/software?page&size&name&type&order&sort`）。
     *
     * @param host 用于拼 CS 类型软件的 `gaop://cs?...` 入口地址（取自请求头 `x-forwarded-host`）
     */
    SoftwareListEntity querySoftwareList(int page, int size, String name, String type,
                                        String order, String sort, String host);

    /**
     * 软件授权列表（厂商 `GET /v1/software/auth` → `SoftwareService.getSoftwareAuthList:3280-3323`）。
     *
     * <p>每个软件的 `status` 三态判定（照抄）：
     * <ul>
     *   <li>`active_code` 空白 → **0**（未授权），且 `authCreateTime`/`authExpireTime` 都置 **null**；</li>
     *   <li>否则：`activeExpire != null && now > activeExpire` → **2**（已过期）；
     *       `activeExpire == null || == 0` → **0**；其余 → **1**（已授权）；</li>
     * </ul>
     * 注意 `active_code` 非空时两个时间字段**原样取**（不过滤 null）。
     * <p>排序**只按 `created_at DESC`**（与列表端点的 `order_top` 恒在最前不同）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthInfoVO> queryAuthList(
            int page, int size, String name, String type);

    /** 授权列表总数（同一套 WHERE）。 */
    long countAuthList(String name, String type);

    /**
     * 软件在某服务下的端口映射（厂商 `GET /v1/software/{softwareId}/service/{serviceName}`
     * → `SoftwareService.servicePortList:2304-2306`）：`software_id` + `service_name` 等值查询，**不排序**。
     * <p>找不到就是**空数组**（不报 404/400）——照抄。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.ServicePortVO> queryServicePorts(
            String softwareId, String serviceName);

    /**
     * 设置服务端口映射路径（厂商 `PUT /v1/software/{softwareId}/service/{serviceName}`，
     * `SoftwareController:466-483` + `SoftwareService.updateSoftwareServicePort:2308-2322`）。
     *
     * <p>校验顺序照抄（**都在 controller 里先做**）：`path` 空白 → `software.path.must.not.null`；
     * `trim` 后长度 ∉ [3,45] → `software.path.length.invalid`；首字符是数字 → `software.path.can.not.start.by.num`；
     * 只允许 `[a-z0-9-]` → `software.path.must.in.char`。
     * <p>然后：同软件+服务下路径已存在 → `software.path.exist.in.service`（参数是 **serviceName**）；
     * 该 (软件,服务,端口) 不存在 → `service.port.not.found`（参数是 **port**）；
     * 否则更新 `path_name` 并返回**重新查出来的那一行**。
     * <p><b>未实现</b>：厂商随后调 `notificationGateway()`（通知网关重载），见 `findings/39` 同一条待办。
     */
    com.csm.paas.core.domain.software.model.valobj.ServicePortVO updateServicePortPath(
            String softwareId, String serviceName, int port, String path);

    /**
     * 生成软件申请码的载荷（厂商 `POST /v1/software/softwareCode` → `SoftwareService.getSoftwareCode:3166-3186`）。
     * <p>**JSON 组装与 Base64 在 trigger 层做**（domain 模块没有 Jackson；且用显式 ObjectNode 保证字段顺序）。
     *
     * <p>算法**完全确定**：`Base64( JSON({"serviceIdList":[入参],"machineCode":<软件机器码>}) )`，
     * 其中机器码用的是 `getMachineCodeBase64ByService()`（= 原始机器码 + "," + "gaop"，**无尾逗号**，
     * 见 `findings/42`）。因此这个端点的输出应当与厂商**逐字节相同**。
     * <p>`softwareKeyList` 为空/null → 400 `software.invalid.api.parameter`。
     */
    com.csm.paas.core.domain.software.model.valobj.SoftwareIdentifierVO generateSoftwareIdentifier(
            java.util.List<String> softwareKeyList);

    /**
     * 取消软件授权（厂商 `POST /v1/software/cancelAuth` → `SoftwareService.cancelAuth:3187-3211`）。
     *
     * <p>逐行照抄：逐个 id 查软件，**不存在 → 400 `software.not.exist.or.deleted`（立即中断）**；
     * `active_code` 空白 → **跳过**（不产出结果项）；否则清空三个授权字段 + 置 `updated_at`，
     * 并产出一项 `{softwareKey, result:false}`。
     * <p>最后：**结果列表为空 → 400 `auth.no.software.authorization`**（即"一个都没取消到"是错误，不是空数组）。
     */
    java.util.List<com.csm.paas.core.domain.software.model.valobj.SoftwareAuthResultVO> cancelSoftwareAuth(
            java.util.List<String> softwareIdList);
}
