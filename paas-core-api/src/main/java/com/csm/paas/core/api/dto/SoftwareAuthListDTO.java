package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `GET /v1/software/auth` 的响应体（`SoftwareAuthList`）：`{list,total}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareAuthListDTO {

    private List<SoftwareAuthInfoDTO> list;
    private long total;
}
