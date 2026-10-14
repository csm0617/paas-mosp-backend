package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.role.model.valobj.RoleExportRowVO;
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
 * `GET /v1/role/import/template` 与 `POST /v1/role/backup` 的 xls 内容
 * （厂商 `RoleService.generateExportExcelTemplate:740` / `generateExportExcel:784`）。
 * <p>规格见 `docs/findings/157`。
 */
class RoleExcelWriterTest {

    private MessageResolver resolver() {
        MessageResolver resolver = mock(MessageResolver.class);
        when(resolver.resolve(anyString())).thenAnswer(i -> "T:" + i.getArgument(0));
        return resolver;
    }

    @Test
    void templateHasEightHeadersAndSixExampleCells() throws Exception {
        byte[] bytes = RoleExcelWriter.buildTemplate(resolver());

        try (HSSFWorkbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("sheet1");
            assertEquals("sheet1", sheet.getSheetName());
            assertEquals(8, sheet.getRow(0).getLastCellNum(), "表头 8 列");
            assertEquals("T:template.role.name", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("T:template.role.nameFr", sheet.getRow(0).getCell(7).getStringCellValue());
            // 示例行只有 6 列，且 cell0 取的是 example.key（导入侧却把 cell0 当 name —— 厂商不一致，照抄）
            assertEquals(6, sheet.getRow(1).getLastCellNum());
            assertEquals("T:template.role.example.key", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("T:template.role.example.name", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("T:template.role.example.user", sheet.getRow(1).getCell(5).getStringCellValue());
        }
    }

    @Test
    void exportWritesEightColumnsAndBlankCellsForEmptyLists() throws Exception {
        var row = RoleExportRowVO.builder()
                .name("维护席位").key("maintenance")
                .softwareKeys(List.of("gx-006")).subRoleKeys(List.of())
                .desc("描述").usernames(List.of("user1"))
                .nameUs("Maintenance").nameFr(null)
                .build();

        byte[] bytes = RoleExcelWriter.buildExport(resolver(), List.of(row));

        try (HSSFWorkbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("sheet1");
            var data = sheet.getRow(1);
            assertEquals("维护席位", data.getCell(0).getStringCellValue());
            assertEquals("maintenance", data.getCell(1).getStringCellValue());
            assertEquals("[\"gx-006\"]", data.getCell(2).getStringCellValue());
            // 空列表 ⇒ setCellValue(null) ⇒ 空字符串单元格（POI 行为；不是 null）
            assertEquals("", data.getCell(3).getStringCellValue(), "空列表 ⇒ 空单元格（厂商只有非空才 gson.toJson）");
            assertEquals("描述", data.getCell(4).getStringCellValue());
            assertEquals("[\"user1\"]", data.getCell(5).getStringCellValue());
            assertEquals("Maintenance", data.getCell(6).getStringCellValue());
            assertEquals("", data.getCell(7).getStringCellValue());
            // 厂商的战位导出**不设列宽**（与子角色导出不同）
            assertEquals(2048, sheet.getColumnWidth(0));
        }
    }
}
