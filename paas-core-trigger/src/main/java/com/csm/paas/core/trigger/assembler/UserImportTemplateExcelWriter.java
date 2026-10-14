package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.trigger.i18n.MessageResolver;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * `GET /v1/user/import/template` 的 xls 产出（厂商 `UserService.generateExportExcelTemplate:932-945`
 * + `ExcelUtils.initializeWorkbook:22-29`）。
 *
 * <p>逐项照抄：
 * <ul>
 *   <li>sheet 名 `sheet1`；第 0 行 = 8 个表头，写的是**已翻译的文案**（厂商传的是 `i18nUtil.getMessages(TABLE_HEADERS)`）；</li>
 *   <li>第 1 行 = 8 个示例单元格（`template.user.example.*`）；</li>
 *   <li>**不加任何样式/列宽/日期格式**（厂商的 `initializeWorkbook` 也不加）。</li>
 * </ul>
 */
public final class UserImportTemplateExcelWriter {

    /** 厂商 `UserService.TABLE_HEADERS`（顺序即列序）。 */
    private static final String[] HEADER_KEYS = {
            "template.user.name", "template.user.nickname", "template.user.relate.user",
            "template.user.relate.subrole", "template.user.relate.sub.subrole",
            "template.user.desc", "template.user.status", "template.user.password"};

    /** 厂商 `generateExportExcelTemplate` 里第 1 行的 8 个示例。 */
    private static final String[] EXAMPLE_KEYS = {
            "template.user.example.name", "template.user.example.nickname", "template.user.example.role.key",
            "template.user.example.role", "template.user.example.role.role", "template.user.example.desc",
            "template.user.example.status", "template.user.example.password"};

    private UserImportTemplateExcelWriter() {
    }

    public static byte[] build(MessageResolver resolver) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("sheet1");
            HSSFRow header = sheet.createRow(0);
            for (int i = 0; i < HEADER_KEYS.length; i++) {
                header.createCell(i).setCellValue(resolver.resolve(HEADER_KEYS[i]));
            }
            HSSFRow example = sheet.createRow(1);
            for (int i = 0; i < EXAMPLE_KEYS.length; i++) {
                example.createCell(i).setCellValue(resolver.resolve(EXAMPLE_KEYS[i]));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
