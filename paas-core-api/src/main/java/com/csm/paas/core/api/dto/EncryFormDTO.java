package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `com.glriverside.gaop.be.controller.form.EncryForm`（`EncryForm.java`）：
 * `POST /v1/load/update` 的请求体，只有 `publicKey` 一个字段（JSON 键名照抄厂商）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EncryFormDTO {

    /** 厂商 `EncryForm.publicKey`：AES 加密过的授权公钥载荷（Base64）。 */
    private String publicKey;
}
