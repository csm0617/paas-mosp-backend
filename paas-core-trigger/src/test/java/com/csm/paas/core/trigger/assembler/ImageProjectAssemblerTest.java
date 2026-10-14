package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约回放：Harbor 项目的**键名与顺序**必须与厂商实测样本一致（snake_case）。
 * <p>这是本轮最容易写错的地方：域对象是 camelCase，Jackson 默认会输出 `creationTime`，
 * 而厂商契约是 `creation_time`。样本取自 `runtime-samples/v1_image_projectList.json`。
 */
class ImageProjectAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void keysMatchRecordedVendorSample() throws Exception {
        JsonNode vendor;
        try (InputStream in = getClass().getResourceAsStream("/vendor/v1_image_projectList.json")) {
            vendor = MAPPER.readTree(in);
        }
        JsonNode vendorItem = vendor.get("data").get("list").get(0);

        var mine = ImageProjectAssembler.toItem(ProjectItemVO.builder()
                .creationTime("+58753-04-29T03:17:20+08:00").projectId(3).name("library").build());

        Set<String> vendorKeys = new LinkedHashSet<>();
        for (Iterator<String> it = vendorItem.fieldNames(); it.hasNext(); ) {
            vendorKeys.add(it.next());
        }
        assertEquals(vendorKeys, new LinkedHashSet<>(mine.keySet()), "键集合与顺序都要一致");
        assertEquals(11, mine.size());
        assertTrue(mine.containsKey("creation_time"), "必须是 snake_case");
        assertEquals("library", mine.get("name"));
    }
}
