package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商 `POST /v1/software` 的请求体（`SoftwareForm`）。
 * <p>`screenShotList` 是**截图列表**（厂商存库时按 `,` 拼成一列）；`roleListKey` 是**战位 key 列表**。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareFormDTO {

    private String key;
    private String name;
    private String nameFr;
    private String nameUs;
    private String type;
    private String routerKey;
    private String desc;
    private String location;
    private String remoteIp;
    private String remoteName;
    private String remotePassword;
    private String framework;
    private String execName;
    private String icon;
    private List<String> screenShotList;
    private List<String> roleListKey;
}
