package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.subrole.model.valobj.ImportSubRoleRowVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 把子角色导入 Excel 解析成 {@link ImportSubRoleRowVO} 列表
 * （厂商 `SubRoleServiceImpl.handleImportExcel:521` + `importSubroleExcel:583-625` 的"读单元格"部分）。
 *
 * <p>列序：0 名称 / 1 唯一标识 / 2 关联用户(JSON) / 3 关联战位(JSON) / 4 描述 / 5 英文名 / 6 法文名。
 * 读单元格一律 `formatCellType(cell, STRING)`；单元格不存在 ⇒ null；空表/只有表头 ⇒ 空列表。
 */
@Slf4j
public final class SubRoleImportExcelParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int COL_NAME = 0;
    private static final int COL_KEY = 1;
    private static final int COL_USERS = 2;
    private static final int COL_ROLES = 3;
    private static final int COL_DESC = 4;
    private static final int COL_NAME_US = 5;
    private static final int COL_NAME_FR = 6;

    private SubRoleImportExcelParser() {
    }

    public static List<ImportSubRoleRowVO> parse(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                log.info("子角色表格无内容，或内容不匹配,直接返回成功");
                return List.of();
            }
            List<ImportSubRoleRowVO> rows = new ArrayList<>(lastRowNum);
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                rows.add(ImportSubRoleRowVO.builder()
                        .rowIndex(i)
                        .name(readString(row, COL_NAME))
                        .key(readString(row, COL_KEY))
                        .usernames(readStringList(readString(row, COL_USERS)))
                        .roleKeys(readStringList(readString(row, COL_ROLES)))
                        .desc(readString(row, COL_DESC))
                        .nameUs(readString(row, COL_NAME_US))
                        .nameFr(readString(row, COL_NAME_FR))
                        .build());
            }
            return rows;
        }
    }

    private static String readString(Row row, int column) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(column);
        if (cell == null) {
            return null;
        }
        try {
            cell.setCellType(CellType.STRING);
            return cell.getStringCellValue();
        } catch (Exception e) {
            return null;
        }
    }

    /** 空白 ⇒ null；非法 JSON ⇒ null（与战位导入同一处理，见 `findings/158`）。 */
    private static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            log.warn("子角色导入的 JSON 列不是合法数组，按没有列表处理: {}", json);
            return null;
        }
    }
}
