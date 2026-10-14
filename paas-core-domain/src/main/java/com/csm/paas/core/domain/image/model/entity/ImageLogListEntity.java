package com.csm.paas.core.domain.image.model.entity;

import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 镜像上传日志列表（厂商 `controller/response/ImageLogList`：`{list,total}`，`total` 是 **Long**）。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageLogListEntity {

    private List<ImageLogVO> list;
    private Long total;
}
