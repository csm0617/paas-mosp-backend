package com.csm.paas.core.domain.software.model.entity;

import com.csm.paas.core.domain.software.model.valobj.RelationRoleVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 厂商 `SoftwareMessage`：软件行 + 4 个附加字段（发布态 / 关联战位 / web 入口状态 / 入口地址）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareMessageEntity {

    private SoftwareEntity software;
    /** 白名单里有 `/<key>/**` → 1，否则 0（厂商 `softWareReleaseStatus`）。 */
    private Integer releaseStatus;
    private List<RelationRoleVO> roleList;
    /** 0=无 web 入口；1=健康；2=找不到 Deployment 或状态缺失；3=异常态。 */
    private Integer web;
    private String url;
}
