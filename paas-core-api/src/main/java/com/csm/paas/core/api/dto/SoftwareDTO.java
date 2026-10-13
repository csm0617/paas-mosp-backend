package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商 `Software` 的 **23 键**形状（`POST /v1/software`、`PUT /v1/software/{id}`、`GET /v1/software/{id}` 的 data）。
 * <p>与列表项 {@link SoftwareItemDTO} 的区别：列表项是 `SoftwareMessage`（多 `releaseStatus`/`roleList`/`web`/`url` 四键）。
 * <p><b>实测提醒</b>：`POST /v1/software` 的响应就是这 23 键——我一开始用列表项 DTO 返回了 27 键（4 个附加键为 null），
 * 对拉时键数不符才发现。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareDTO {

    private String id;
    private String key;
    private String name;
    private String appName;
    private String routerKey;
    private String type;
    private String version;
    private Long createdAt;
    private Long updatedAt;
    private String framework;
    private String execName;
    private String location;
    private String remoteIp;
    private String remoteName;
    private String remotePassword;
    private Long activeCreate;
    private Long activeExpire;
    private Integer orderTop;
    private Long orderTime;
    private String desc;
    private String activeCode;
    private String screenshot;
    private String icon;
}
