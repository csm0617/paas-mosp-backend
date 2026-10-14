package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.user.model.valobj.ImportUserRowVO;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入 Excel 的**解析**（厂商 `ExcelUtils.formatCellType(cell, STRING).getStringCellValue()` 的等价行为）。
 *
 * <p>最关键的一条：**单元格不存在 ⇒ {@code null}**、**单元格存在但为空 ⇒ 空串 {@code ""}**——
 * 领域层的报错/跳过规则全靠这个区分（见 {@link ImportUserRowVO}）。
 */
class UserImportExcelParserTest {

    private static final String[] HEADERS = {"name", "nickname", "roleKey", "roles", "subRoles", "desc", "status", "password"};

    /** 造一个 xls：第 0 行是表头，之后按行写单元格（{@code null} = 该单元格不创建）。 */
    private static byte[] xls(String[][] dataRows) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    if (dataRows[r][c] != null) {
                        row.createCell(c).setCellValue(dataRows[r][c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static List<ImportUserRowVO> parse(byte[] bytes) throws IOException {
        return UserImportExcelParser.parse(new ByteArrayInputStream(bytes));
    }

    @Test
    void headerOnlySheetYieldsNoRows() throws IOException {
        assertTrue(parse(xls(new String[0][])).isEmpty(), "lastRowNum < 1 ⇒ 厂商只记日志、不做导入");
    }

    @Test
    void missingCellIsNullButEmptyCellIsEmptyString() throws IOException {
        // 第 1 行：只创建 col0；第 2 行：col0/col1/col6/col7 都创建但为空串
        List<ImportUserRowVO> rows = parse(xls(new String[][]{
                {"bob"},
                {"alice", "", "", "", "", "", "", ""}}));

        assertEquals(2, rows.size());
        assertEquals("bob", rows.get(0).getUsername());
        assertNull(rows.get(0).getNickname(), "col1 不存在 ⇒ null");
        assertNull(rows.get(0).getStatusText(), "col6 不存在 ⇒ null");
        assertNull(rows.get(0).getPassword(), "col7 不存在 ⇒ null");
        assertNull(rows.get(0).getUserToRole());
        assertNull(rows.get(0).getSubRoles());

        assertEquals("", rows.get(1).getNickname(), "col1 存在但为空 ⇒ 空串（不是 null！）");
        assertEquals("", rows.get(1).getStatusText());
        assertEquals("", rows.get(1).getPassword());
    }

    @Test
    void numericStatusCellIsConvertedToText() throws IOException {
        byte[] bytes;
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            sheet.createRow(0).createCell(0).setCellValue("name");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("bob");
            // 数字单元格：厂商 `setCellType(STRING)` 会把它转成 "1"
            row.createCell(6).setCellType(CellType.NUMERIC);
            row.createCell(6).setCellValue(1);
            workbook.write(out);
            bytes = out.toByteArray();
        }
        assertEquals("1", parse(bytes).get(0).getStatusText());
    }

    @Test
    void jsonColumnsAreParsedIntoKeyLists() throws IOException {
        List<ImportUserRowVO> rows = parse(xls(new String[][]{
                {"bob", "n", "tester", "[\"user\",\"dev\"]", "[\"ops\"]", "d", "1", "secret123"}}));

        assertEquals(List.of("user", "dev"), rows.get(0).getUserToRole());
        assertEquals(List.of("ops"), rows.get(0).getSubRoles());
    }

    @Test
    void blankJsonColumnYieldsNull() throws IOException {
        List<ImportUserRowVO> rows = parse(xls(new String[][]{
                {"bob", "n", "tester", "   ", null, "d", "1", "secret123"}}));
        assertNull(rows.get(0).getUserToRole(), "空白 ⇒ 厂商 `isNotBlank` 守卫 ⇒ 不解析");
        assertNull(rows.get(0).getSubRoles());
    }

    @Test
    void malformedJsonPropagatesAsIoException() throws IOException {
        // 厂商的 Gson 也会把 JsonSyntaxException 抛出去 ⇒ 500；这里同样是 IOException 子类
        byte[] bytes = xls(new String[][]{{"bob", "n", "tester", "not-json", null, "d", "1", "secret123"}});
        assertThrows(IOException.class, () -> parse(bytes));
    }

    @Test
    void blankRowBetweenDataIsKeptAsEmptyUsername() throws IOException {
        // 中间夹一个"完全空"的行（厂商遍历到 lastRowNum，逐行读 col0 ⇒ 空 ⇒ 跳过该行）
        List<ImportUserRowVO> rows = parse(xls(new String[][]{{"bob", "n", null, null, null, null, "1", "secret123"}, {}}));
        assertEquals(2, rows.size());
        assertNull(rows.get(1).getUsername());
    }
}
