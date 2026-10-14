package com.csm.paas.core.domain.backup.adapter.port;

import java.io.IOException;
import java.io.InputStream;

/**
 * 「框架基础数据」导出端口（厂商 `BackService.getBaseInputStream:1093-1202`）。
 *
 * <p>厂商在这里做六件事，key → 产物：
 * <table>
 *   <tr><td>`user`</td><td>`UserService.generateExportExcel(空列表)` → HSSFWorkbook → `user.xls` 字节流</td></tr>
 *   <tr><td>`role`</td><td>`RoleService.generateExportExcel(空列表)` → `role.xls`</td></tr>
 *   <tr><td>`sub_role`</td><td>`SubRoleService.generateExportExcel(空列表)` → `sub_role.xls`</td></tr>
 *   <tr><td>`software`</td><td>`SoftwareService.exportSoftwareForms(空列表)` → Gson → `software.json`</td></tr>
 *   <tr><td>`ingress`</td><td>`SoftwareService.exportTcpAndUdpConfigMap()` → Jackson → `ingress.json`（厂商在 `/baseData` 里又写成 `listenInfo.json`）</td></tr>
 *   <tr><td>`configmap`</td><td>`SoftwareService.exportConfigMap(空列表)` → Jackson → `configmap.json`</td></tr>
 * </table>
 * 未知 key → 厂商抛 `WrongArgumentException("backup.get.data.info.identifier.error", key)`（`BackService.java:1200-1201`）。
 *
 * <p><b>类 C：本环境不可验证</b>——本次重写在 infrastructure 里实现了 `configmap`（复用
 * `IServiceQueryService.exportConfigMaps`）与 `ingress`（复用 `IServiceQueryService` 的 ingress
 * 快照）两条；`user`/`role`/`sub_role`（需要 POI，而 domain 禁用 POI、且本任务的**文件白名单不允许
 * 在 trigger 新增 Excel 写手**）与 `software`（厂商 `exportSoftwareForms` 尚未移植）如实抛厂商同名错误键，
 * 详见实现类 javadoc。
 */
public interface IBaseDataExportPort {

    /**
     * 生成某个基础数据 key 的字节流（调用方负责关闭）。
     *
     * @param dataKey user / role / sub_role / software / ingress / configmap
     * @throws IOException 与厂商相同：生成失败抛 IO 异常，由调用方包成厂商的 i18n 键
     */
    InputStream openBaseData(String dataKey) throws IOException;
}
