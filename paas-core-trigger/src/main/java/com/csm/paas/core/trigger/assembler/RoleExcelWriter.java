package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.role.model.valobj.RoleExportRowVO;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 战位导入模板与导出（备份）的 xls 产出：厂商 `RoleService.generateExportExcelTemplate:740-752` /
 * `generateExportExcel:784-830` + `ExcelUtils.initializeWorkbook:22-29`。
 *
 * <p>逐项照抄：
 * <ul>
 *   <li>sheet 名 `sheet1`；第 0 行 = **8 个**表头（写的是已翻译的文案）；</li>
 *   <li>**模板**第 1 行只有 **6** 个示例单元格，取自 `template.role.example.*`；
 *       注意 cell0 取的是 `example.key`、cell1 取的是 `example.name`
 *       ——而导入侧把 **cell1 当 key、cell0 当 name**（厂商模板与导入器不一致，照抄）；</li>
 *   <li>**导出**每行 8 列：名称/唯一标识/关联应用(JSON)/关联子角色(JSON)/描述/关联用户(JSON)/英文名/法文名；</li>
 *   <li>导出**不设列宽**（与子角色导出不同——那边设了前 5 列 2560）。</li>
 * </ul>
 */
public final class RoleExcelWriter {

    /** 厂商 `RoleService.TABLE_HEADERS`（顺序即列序）。 */
    private static final String[] HEADER_KEYS = {
            "template.role.name", "template.role.key", "template.role.relate.software",
            "template.role.relate.subRole", "template.role.desc", "template.role.relate.user",
            "template.role.nameUs", "template.role.nameFr"};

    /** 模板示例行（6 个键，顺序即 cell0..cell5）。 */
    private static final String[] EXAMPLE_KEYS = {
            "template.role.example.key", "template.role.example.name", "template.role.example.app",
            "template.role.example.role", "template.role.example.desc", "template.role.example.user"};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RoleExcelWriter() {
    }

    /** 模板：8 列表头 + 6 个示例单元格。 */
    public static byte[] buildTemplate(MessageResolver resolver) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("sheet1");
            writeHeader(sheet, resolver);
            HSSFRow example = sheet.createRow(1);
            for (int i = 0; i < EXAMPLE_KEYS.length; i++) {
                example.createCell(i).setCellValue(resolver.resolve(EXAMPLE_KEYS[i]));
            }
            return toBytes(workbook);
        }
    }

    /** 导出：8 列表头 + 每行 8 列（**不设列宽**）。 */
    public static byte[] buildExport(MessageResolver resolver, List<RoleExportRowVO> rows) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("sheet1");
            writeHeader(sheet, resolver);
            int rowIndex = 1;
            for (RoleExportRowVO row : rows) {
                HSSFRow dataRow = sheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(row.getName());
                dataRow.createCell(1).setCellValue(row.getKey());
                // 厂商：列表非空才 gson.toJson(...)，空则 null（setCellValue(null) ⇒ 空单元格）
                dataRow.createCell(2).setCellValue(toJson(row.getSoftwareKeys()));
                dataRow.createCell(3).setCellValue(toJson(row.getSubRoleKeys()));
                dataRow.createCell(4).setCellValue(row.getDesc());
                dataRow.createCell(5).setCellValue(toJson(row.getUsernames()));
                dataRow.createCell(6).setCellValue(row.getNameUs());
                dataRow.createCell(7).setCellValue(row.getNameFr());
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
