package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.ImageInfoVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务关联的镜像 → 厂商契约键（`controller/response/ImageInfo`：`group` → `imageName` → `tag`，
 * 字段声明顺序即键顺序）。
 */
public final class ImageInfoAssembler {

    private ImageInfoAssembler() {
    }

    public static Map<String, Object> toItem(ImageInfoVO item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group", item.getGroup());
        map.put("imageName", item.getImageName());
        map.put("tag", item.getTag());
        return map;
    }
}
