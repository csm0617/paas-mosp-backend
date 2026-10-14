package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.trigger.i18n.MessageResolver;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/user/import/template` 的 xls 内容（厂商 `generateExportExcelTemplate:932-945`）。
 * <p>**已实测**：两侧文件大小相同（4608B）、响应头相同、内容一致；**唯一字节差异**是 xls 文档属性里的
 * **操作系统用户名**（厂商机上是 `root`、本机是 `csm`）——环境产物，不是契约差异（`findings/101`）。
 */
class UserImportTemplateExcelWriterTest {

    @Test
    void writesSheetNameHeaderRowAndExampleRow() throws Exception {
        MessageResolver resolver = mock(MessageResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> "T:" + i.getArgument(0));

        byte[] bytes = UserImportTemplateExcelWriter.build(resolver);

        try (HSSFWorkbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheet("sheet1");
            assertEquals("sheet1", sheet.getSheetName());
            assertEquals(8, sheet.getRow(0).getLastCellNum(), "表头 8 列");
            assertEquals("T:template.user.name", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("T:template.user.password", sheet.getRow(0).getCell(7).getStringCellValue());
            assertEquals(8, sheet.getRow(1).getLastCellNum(), "示例行 8 列");
            assertEquals("T:template.user.example.name", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("T:template.user.example.password", sheet.getRow(1).getCell(7).getStringCellValue());
            assertEquals(0, wb.getNumberOfSheets() - 1, "只有一个 sheet");
        }
    }
}
