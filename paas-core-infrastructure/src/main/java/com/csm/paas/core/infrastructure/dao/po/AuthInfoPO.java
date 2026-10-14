package com.csm.paas.core.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * `g_auth` 的投影（厂商 `com.glriverside.gaop.be.model.AuthInfo`；
 * 表名见厂商 `AuthInfoDynamicSqlSupport.AuthInfo`：`super("g_auth")`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthInfoPO {

    private Integer id;
    private String jarName;
    private String storeJarName;
    private String className;
    private Long createAt;
    private Long updateAt;
    private Integer used;
    private String publicKey;
    private String privateKey;
}
