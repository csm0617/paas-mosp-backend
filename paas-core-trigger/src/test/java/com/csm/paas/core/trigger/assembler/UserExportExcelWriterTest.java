package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.user.service.impl.UserExportService;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `/v1/user/export` 的 xls 结构（厂商 `generateExportExcel:977-1026`）。
 * <p>**8 列**、表头已翻译、**只给前三列设宽 2560**。
 */
class UserExportExcelWriterTest {

    private MessageResolver resolver() {
        MessageResolver r = mock(MessageResolver.class);
        when(r.resolve(anyString())).thenAnswer(i -> "T:" + i.getArgument(0));
        return r;
    }

    @Test
    void headerOnlyWhenNoRows() throws Exception {
        byte[] bytes = UserExportExcelWriter.build(resolver(), List.of());

        try (HSSFWorkbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("sheet1");
            assertEquals(1, sheet.getLastRowNum() + 1, "只有表头行（admin/root 被跳过）");
            assertEquals(8, sheet.getRow(0).getLastCellNum());
            assertEquals("T:template.user.name", sheet.getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void dataRowFollowsVendorColumnOrderAndWidths() throws Exception {
        var row = new UserExportService.Row("bob", "尼克", "admin",
                List.of("ops"), List.of("sub1"), "描述", 1, "$2a$hash");

        byte[] bytes = UserExportExcelWriter.build(resolver(), List.of(row));

        try (HSSFWorkbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("sheet1");
            var data = sheet.getRow(1);
            assertEquals("bob", data.getCell(0).getStringCellValue());
            assertEquals("尼克", data.getCell(1).getStringCellValue());
            assertEquals("admin", data.getCell(2).getStringCellValue());
            assertEquals("[\"ops\"]", data.getCell(3).getStringCellValue(), "角色关系 key 列表 JSON");
            assertEquals("[\"sub1\"]", data.getCell(4).getStringCellValue(), "子角色 key 列表 JSON");
            assertEquals("描述", data.getCell(5).getStringCellValue());
            assertEquals(1.0, data.getCell(6).getNumericCellValue(), "status 是数字单元格");
            assertEquals("$2a$hash", data.getCell(7).getStringCellValue(), "第 7 列口令哈希（B12）");
            assertEquals(2560, sheet.getColumnWidth(0));
            assertEquals(2560, sheet.getColumnWidth(2));
            assertEquals(2048, sheet.getColumnWidth(3), "**只**前三列设宽，第 4 列保持默认");
        }
    }
}
