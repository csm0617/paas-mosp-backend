package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `POST /v1/service/startUp` 的请求体（`InitServiceInfo`）：`{list, time}`（延时秒数）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InitServiceInfoDTO {

    private List<String> list;
    private Long time;
}
