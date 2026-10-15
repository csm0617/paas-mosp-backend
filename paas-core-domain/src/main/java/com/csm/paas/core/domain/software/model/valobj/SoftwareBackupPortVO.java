package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 软件端口映射的导出/还原形态（厂商 `controller/response/ExportServicePortForm`，
 * 5 键：serviceName/port/pathName/webEntry/allow）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareBackupPortVO {

    private String serviceName;
    private Integer port;
    private String pathName;
    private Boolean webEntry;
    private Boolean allow;
}
