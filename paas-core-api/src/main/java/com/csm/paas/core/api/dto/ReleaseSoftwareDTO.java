package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `POST /v1/software/release` 的请求体（`ReleaseSoftwareForm`）：`{type, list}`。
 * <p>`type=1` 放行（写白名单）、`type=0` 禁止（删白名单）；`list` 是**软件 id** 列表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReleaseSoftwareDTO {

    private Integer type;
    private List<String> list;
}
