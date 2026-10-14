package com.csm.paas.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 数据库记录列表（厂商 `DatabaseRecordList`：`{list,total}`）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseRecordListDTO {

    private List<DatabaseRecordDTO> list;
    private long total;
}
