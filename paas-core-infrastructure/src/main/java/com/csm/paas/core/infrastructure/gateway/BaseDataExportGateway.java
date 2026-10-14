package com.csm.paas.core.infrastructure.gateway;

import com.csm.paas.core.domain.backup.adapter.port.IBaseDataExportPort;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 「框架基础数据」导出端口实现（厂商 {@code BackService.getBaseInputStream:1093-1202}）。
 *
 * <h3>已实现</h3>
 * <ul>
 *   <li>`configmap`：复用 `IServiceQueryService.exportConfigMaps(ns, 空列表)`（= 厂商
 *       `SoftwareService.exportConfigMap(空列表)`，列出**不带 `ingress-lb` 标签**的全部 ConfigMap），
 *       再用 Jackson 序列化（厂商用 `ObjectMapper` + `JavaTimeModule` 写临时文件；本仓 infra 无
 *       `jackson-datatype-jsr310`，而 ConfigMap 快照里没有 Java 时间类型 ⇒ 影响面相同）。
 *       **响应形状等价**，但**不是同一份实现**（厂商是 K8s `V1ConfigMap` 反序列化后的对象图）。</li>
 *   <li>`ingress`：复用 `IServiceQueryService.exportListenConfigs(ns)`（= 厂商
 *       `SoftwareService.exportTcpAndUdpConfigMap()`）。</li>
 * </ul>
 *
 * <h3><b>未做</b>（本任务的文件白名单内做不了，见回报）</h3>
 * <ul>
 *   <li>`user` / `role` / `sub_role`：厂商产物是 **HSSFWorkbook(.xls)**，需要 POI。
 *       domain 禁用 POI；而本任务只允许新建 `BackupController` / `DataBaseController` 两个 trigger 文件，
 *       不允许新增 Excel 写手（本仓既有的 `UserExportExcelWriter.build` / `RoleExcelWriter.buildExport` /
 *       `SubRoleExcelWriter.buildExport` 在 trigger 层，domain 与 infrastructure 都依赖不到）。
 *       ⇒ 这里**如实抛厂商同名错误键**并发 ERROR 日志说明缺口。</li>
 *   <li>`software`：厂商 `SoftwareService.exportSoftwareForms(空列表)`（软件 + 战位/服务关联的导出表单）
 *       **尚未移植**（属 K2 余账）⇒ 同样抛厂商同名错误键。</li>
 *   <li>未知 key：抛 `backup.get.data.info.identifier.error`（厂商 `:1200-1201`）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaseDataExportGateway implements IBaseDataExportPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IServiceQueryService serviceQueryService;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @Override
    public InputStream openBaseData(String dataKey) throws IOException {
        switch (dataKey) {
            case "configmap" -> {
                List<java.util.Map<String, Object>> configMaps =
                        this.serviceQueryService.exportConfigMaps(this.namespace, List.of());
                return new ByteArrayInputStream(MAPPER.writeValueAsBytes(configMaps));
            }
            case "ingress" -> {
                var listenBackInfo = this.serviceQueryService.exportListenConfigs(this.namespace);
                return new ByteArrayInputStream(MAPPER.writeValueAsBytes(listenBackInfo));
            }
            case "user", "role", "sub_role" -> {
                log.error("「{}」基础数据导出未实现：厂商产物是需要 POI 的 .xls（UserService/RoleService/"
                        + "SubRoleService.generateExportExcel），本切片无法在 trigger 层新增 Excel 写手", dataKey);
                throw new WrongArgumentException(switch (dataKey) {
                    case "user" -> "sync.get.user.data.exception";
                    default -> "sync.get.storage.data.exception";
                });
            }
            case "software" -> {
                log.error("「software」基础数据导出未实现：厂商 SoftwareService.exportSoftwareForms 尚未移植");
                throw new WrongArgumentException("sync.get.software.data.exception");
            }
            default -> throw new WrongArgumentException("backup.get.data.info.identifier.error", dataKey);
        }
    }
}
