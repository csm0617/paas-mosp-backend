package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** `manifest.json` 的 `apps` 一项（厂商 `model/ExportManifest$AppEntry`：`name` + `contents`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExportManifestAppDTO {

    private String name;
    @Builder.Default
    private List<String> contents = new ArrayList<>();
}
