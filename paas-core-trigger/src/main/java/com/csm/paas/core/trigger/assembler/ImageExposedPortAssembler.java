package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.ExposedPortItemVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 镜像暴露端口 → 厂商契约键（`controller/response/image/ExposedPortItem`：`port`、`protocol`，
 * 字段声明顺序即键顺序）。
 */
public final class ImageExposedPortAssembler {

    private ImageExposedPortAssembler() {
    }

    public static Map<String, Object> toItem(ExposedPortItemVO item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("port", item.getPort());
        map.put("protocol", item.getProtocol());
        return map;
    }
}
