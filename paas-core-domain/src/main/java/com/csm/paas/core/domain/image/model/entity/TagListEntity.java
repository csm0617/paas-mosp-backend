package com.csm.paas.core.domain.image.model.entity;

import com.csm.paas.core.domain.image.model.valobj.TagItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** tag 列表（厂商 `controller/response/TagList`：`{list,total}`，`total` 是 **Integer**）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TagListEntity {

    private List<TagItemVO> list;
    private Integer total;
}
