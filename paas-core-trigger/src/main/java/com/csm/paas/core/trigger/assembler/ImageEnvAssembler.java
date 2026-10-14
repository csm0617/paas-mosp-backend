package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.ImageEnvInfoVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 镜像 ENV → 厂商契约键（`controller/response/image/ImageEnvInfo`：`name`、`value`）。
 */
public final class ImageEnvAssembler {

    private ImageEnvAssembler() {
    }

    public static Map<String, Object> toItem(ImageEnvInfoVO item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", item.getName());
        map.put("value", item.getValue());
        return map;
    }
}
