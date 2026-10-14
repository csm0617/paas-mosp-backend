package com.csm.paas.core.domain.dynamicloading.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 动态加载授权 jar 的记录（厂商 `com.glriverside.gaop.be.model.AuthInfo`，表 **`g_auth`**，
 * 见厂商 `AuthInfoDynamicSqlSupport.AuthInfo`：`super("g_auth")`）。
 *
 * <p>本次只用到 `POST /v1/load/update`（改默认授权 jar 的密钥）所需的字段。
 */
@Data
@Builder
public class AuthInfoEntity {

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
