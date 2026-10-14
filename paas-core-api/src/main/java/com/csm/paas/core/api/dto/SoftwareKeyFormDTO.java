package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `POST /v1/software/softwareCode` 的请求体（`SoftwareKeyForm`）：`{softwareKeyList}`。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareKeyFormDTO {

    private List<String> softwareKeyList;
}
