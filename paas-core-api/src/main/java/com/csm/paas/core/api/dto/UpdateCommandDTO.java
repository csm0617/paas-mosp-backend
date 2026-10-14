package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `PUT /v1/service/patchCommand/{serviceName}` 的请求体（`UpdateCommand`）：`{command, args}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCommandDTO {

    private List<String> command;
    private List<String> args;
}
