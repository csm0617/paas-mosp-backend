package com.csm.paas.core.domain.software.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 软件列表分页结果（厂商 `SoftwareList`：`{softwareList,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareListEntity {

    private List<SoftwareMessageEntity> softwareList;
    private long total;
}
