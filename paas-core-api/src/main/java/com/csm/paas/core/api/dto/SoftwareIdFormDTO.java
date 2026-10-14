package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `SoftwareIdForm`（`PUT /v1/software/order/{orderNum}` 的请求体）：`{softwareIds}`。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareIdFormDTO {

    private List<String> softwareIds;
}
