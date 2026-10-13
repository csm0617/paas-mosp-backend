package com.csm.paas.core.infrastructure.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 那条 JSON patch 的**报文必须与厂商逐字相同**（`ServiceController:1536`）：
 * `[{"op":"replace","path":"/spec/template/metadata/annotations/gaop~1serviceLabel","value":"<name>"}]`
 * <p>三个不能想当然的点：`op` 是 **replace**（注解不存在时会失败，从而触发 StatefulSet 回退）；
 * 路径里的 `~1` 是 JSON-pointer 对 `/` 的转义（注解键是 `gaop/serviceLabel`）；
 * 键顺序是 op → path → value。
 */
class ServiceLabelPatchBodyTest {

    @Test
    void patchBodyIsByteIdenticalToVendorFormat() {
        assertEquals("[{\"op\":\"replace\",\"path\":\"/spec/template/metadata/annotations/gaop~1serviceLabel\","
                        + "\"value\":\"新名字\"}]",
                ServiceRuntimeGateway.serviceLabelPatchBody("新名字"));
    }
}
