package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.user.service.impl.UserExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.csm.paas.core.trigger.i18n.MessageResolver;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * `/v1/user/export` 与 `/v1/user/backup` 的 xls 产出（厂商 `generateExportExcel:977-1026`）。
 *
 * <p>照抄：表头是**已翻译文案**（同模板端点）；8 列；行尾**只给第 0/1/2 列设宽 2560**；
 * 厂商还建了一个 `yyyy-MM-dd` 日期样式（本函数未用到日期单元格，但样式对象会被创建——
 * 为了字节层面对齐，这里也创建一次）。
 */
public final class UserExportExcelWriter {

    private static final String[] HEADER_KEYS = {
            "template.user.name", "template.user.nickname", "template.user.relate.user",
            "template.user.relate.subrole", "template.user.relate.sub.subrole",
            "template.user.desc", "template.user.status", "template.user.password"};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UserExportExcelWriter() {
    }

    public static byte[] build(MessageResolver resolver, List<UserExportService.Row> rows) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("sheet1");
            HSSFRow header = sheet.createRow(0);
            for (int i = 0; i < HEADER_KEYS.length; i++) {
                header.createCell(i).setCellValue(resolver.resolve(HEADER_KEYS[i]));
            }
            // 厂商：getDateCellStyle(workbook, "yyyy-MM-dd")——导出里其实没有日期单元格，但样式对象会被创建
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-MM-dd"));
            int rowIndex = 1;
            for (var r : rows) {
                HSSFRow row = sheet.createRow(rowIndex++);
                set(row, 0, r.username());
                set(row, 1, r.nickname());
                set(row, 2, r.roleKey());
                set(row, 3, toJson(r.roleKeys()));
                set(row, 4, toJson(r.subRoleKeys()));
                set(row, 5, r.desc());
                if (r.status() != null) {
                    row.createCell(6).setCellValue(r.status().intValue());
                }
                set(row, 7, r.password());
            }
            for (int i = 0; i < 3; i++) {
                sheet.setColumnWidth(i, 2560);   // 厂商**只**设前三列
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** 厂商用 Gson 序列化键列表；空列表**不写单元格**（值为 null）。 */
    private static String toJson(java.util.List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private static void set(HSSFRow row, int col, String value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }
}
