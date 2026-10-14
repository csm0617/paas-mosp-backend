package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `POST /v1/service/cancelStartUp` 的请求体（`StartUpServiceList`）：`{list}`（服务名列表）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StartUpServiceListDTO {

    private List<String> list;
}
