package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.role.model.valobj.ImportRoleRowVO;
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
 * 把战位导入 Excel 解析成 {@link ImportRoleRowVO} 列表（厂商 `RoleService.handleImportExcel:836` +
 * `importRoleExcel:852-1124` 的"读单元格"部分）。
 *
 * <p>列序：0 名称 / 1 唯一标识 / 2 关联应用(JSON) / 3 关联子角色(JSON) / 4 描述 /
 * 5 关联用户(JSON) / 6 英文名 / 7 法文名。
 *
 * <p><b>照抄</b>：读单元格一律 `ExcelUtils.formatCellType(cell, STRING).getStringCellValue()`
 * （数字单元格会被转成文本）；单元格不存在 ⇒ {@code null}（厂商在这里 NPE，被逐处 catch）。
 * `lastRowNum < 1`（只有表头/空表）⇒ 空列表（厂商只记日志、直接返回成功）。
 *
 * <p><b>一处统一化的偏离</b>：三个 JSON 列的**非法 JSON**在本环境一律按"没有列表"处理。
 * 厂商在三种列、两个分支上的行为不一致（有的 500、有的吞掉、有的走 `break`），
 * 详见 `findings/158`；前端路径上产不出非法 JSON（模板与导出都是合法 JSON 数组）。
 */
@Slf4j
public final class RoleImportExcelParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int COL_NAME = 0;
    private static final int COL_KEY = 1;
    private static final int COL_SOFTWARE = 2;
    private static final int COL_SUB_ROLE = 3;
    private static final int COL_DESC = 4;
    private static final int COL_USER = 5;
    private static final int COL_NAME_US = 6;
    private static final int COL_NAME_FR = 7;

    private RoleImportExcelParser() {
    }

    /** @return 从**第 1 行**（第 0 行是表头）开始的每一行；空表/只有表头 ⇒ 空列表 */
    public static List<ImportRoleRowVO> parse(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                log.info("战位表格无内容，或内容不匹配,直接返回成功");
                return List.of();
            }
            List<ImportRoleRowVO> rows = new ArrayList<>(lastRowNum);
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                rows.add(ImportRoleRowVO.builder()
                        .rowIndex(i)
                        .name(readString(row, COL_NAME))
                        .key(readString(row, COL_KEY))
                        .softwareKeys(readStringList(readString(row, COL_SOFTWARE)))
                        .subRoleKeys(readStringList(readString(row, COL_SUB_ROLE)))
                        .desc(readString(row, COL_DESC))
                        .usernames(readStringList(readString(row, COL_USER)))
                        .nameUs(readString(row, COL_NAME_US))
                        .nameFr(readString(row, COL_NAME_FR))
                        .build());
            }
            return rows;
        }
    }

    /** 照抄 `ExcelUtils.formatCellType(cell, STRING)`：单元格不存在/转换失败 ⇒ null。 */
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

    /** 空白 ⇒ null；非法 JSON ⇒ null（见类注释的偏离说明）。 */
    private static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            log.warn("战位导入的 JSON 列不是合法数组，按没有列表处理: {}", json);
            return null;
        }
    }
}
