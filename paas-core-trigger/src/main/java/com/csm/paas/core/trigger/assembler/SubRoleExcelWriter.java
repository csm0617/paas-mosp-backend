package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.subrole.model.valobj.SubRoleExportRowVO;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 子角色导入模板与导出的 xls 产出（厂商 `SubRoleServiceImpl.generateExportExcelTemplate:459-467` /
 * `generateExportExcel:470-520` + `ExcelUtils.initializeWorkbook:22-29`）。
 *
 * <p>逐项照抄：
 * <ul>
 *   <li>sheet 名 `sheet1`；第 0 行 = 7 个表头（写的是**已翻译的文案**）；</li>
 *   <li>**模板**第 1 行 = 4 个示例单元格：前两个是 `subRole` 这个 i18n 键（**该键不存在 ⇒ 原样返回 "subRole"**），
 *       后两个是字面量 `["user1","user2"]` 与 `["role_01","role_02"]`（厂商同样拿它们当 i18n 键 ⇒ 原样返回）；</li>
 *   <li>**导出**每行 7 列：名称/唯一标识/用户 JSON 数组/战位 JSON 数组/描述/英文名/法文名；</li>
 *   <li>导出后把**前 5 列**列宽设为 `2560`（厂商 `for (int i=0;i<5;i++) setColumnWidth(i,2560)`）；
 *       模板**不设**列宽。</li>
 * </ul>
 */
public final class SubRoleExcelWriter {

    /** 厂商 `SubRoleServiceImpl.TABLE_HEADERS`（顺序即列序）。 */
    private static final String[] HEADER_KEYS = {
            "template.subrole.name", "template.subrole.key", "template.subrole.relate.user",
            "template.subrole.relate.role", "template.subrole.desc",
            "template.subrole.nameUs", "template.subrole.nameFr"};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SubRoleExcelWriter() {
    }

    /** 模板：表头 + 4 个示例单元格（**没有**列宽设置）。 */
    public static byte[] buildTemplate(MessageResolver resolver) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("sheet1");
            writeHeader(sheet, resolver);
            HSSFRow example = sheet.createRow(1);
            // 厂商：前两个示例拿 "subRole" 当 i18n 键（不存在 ⇒ 原样返回）；后两个是字面量
            example.createCell(0).setCellValue(resolver.resolve("subRole"));
            example.createCell(1).setCellValue(resolver.resolve("subRole"));
            example.createCell(2).setCellValue(resolver.resolve("[\"user1\",\"user2\"]"));
            example.createCell(3).setCellValue(resolver.resolve("[\"role_01\",\"role_02\"]"));
            return toBytes(workbook);
        }
    }

    /** 导出：表头 + 每行 7 列，并把前 5 列列宽设为 2560。 */
    public static byte[] buildExport(MessageResolver resolver, List<SubRoleExportRowVO> rows) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("sheet1");
            writeHeader(sheet, resolver);
            int rowIndex = 1;
            for (SubRoleExportRowVO row : rows) {
                HSSFRow dataRow = sheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(row.getName());
                dataRow.createCell(1).setCellValue(row.getKey());
                // 厂商用 Gson 序列化列表 ⇒ 空列表时为 null（单元格留空）
                dataRow.createCell(2).setCellValue(toJson(row.getUsernames()));
                dataRow.createCell(3).setCellValue(toJson(row.getRoleKeys()));
                dataRow.createCell(4).setCellValue(row.getDesc());
                dataRow.createCell(5).setCellValue(row.getNameUs());
                dataRow.createCell(6).setCellValue(row.getNameFr());
            }
            for (int i = 0; i < 5; i++) {
                sheet.setColumnWidth(i, 2560);
            }
            return toBytes(workbook);
        }
    }

    private static void writeHeader(HSSFSheet sheet, MessageResolver resolver) {
        HSSFRow header = sheet.createRow(0);
        for (int i = 0; i < HEADER_KEYS.length; i++) {
            header.createCell(i).setCellValue(resolver.resolve(HEADER_KEYS[i]));
        }
    }

    /** 厂商：列表非空才 `gson.toJson(...)`，空则 null（`setCellValue(null)` ⇒ 空单元格）。 */
    private static String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] toBytes(HSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
