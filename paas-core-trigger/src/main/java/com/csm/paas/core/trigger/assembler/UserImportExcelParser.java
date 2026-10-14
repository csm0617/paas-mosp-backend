package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.user.model.valobj.ImportUserRowVO;
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
 * 把用户导入 Excel 解析成 {@link ImportUserRowVO} 列表（厂商 `UserService.handleImportExcel` /
 * `importExcel` 的"读单元格"部分，`UserService.java:1029-1177`）。
 *
 * <p><b>为什么这一段在 trigger</b>：POI 与 JSON 都是 domain 层没有的依赖（硬约束），
 * 而"单元格不存在"这件事**带语义**（厂商会因此抛 400 或跳过整行），所以由这里把它翻译成 {@code null}，
 * 领域层只负责按厂商的判定顺序处理（见 {@link ImportUserRowVO} 的字段文档）。
 *
 * <p><b>照抄的两点</b>：
 * <ol>
 *   <li>厂商读单元格一律是 `ExcelUtils.formatCellType(cell, STRING).getStringCellValue()`
 *       ——`formatCellType` 里是 `cell.setCellType(STRING)`，所以**数字单元格会被转成文本**
 *       （状态列写 1 而不是 "1" 也能导入）；单元格不存在时 NPE，被调用方 catch 成"读不到"；</li>
 *   <li>`lastRowNum < 1`（只有表头或空表）⇒ 厂商**只记日志、直接返回**（不报错）⇒ 这里返回空列表，
 *       由领域层 `importUsers` 直接 return（HTTP 200 + `data:null`，库无变化）。</li>
 * </ol>
 *
 * <p>`WorkbookFactory.create` 同时支持 xls/xlsx——与厂商一致（MIME 守卫也放行 xlsx）。
 */
@Slf4j
public final class UserImportExcelParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** col0..col7 的语义见 {@link ImportUserRowVO}（顺序即模板列序）。 */
    private static final int COL_USERNAME = 0;
    private static final int COL_NICKNAME = 1;
    private static final int COL_ROLE_KEY = 2;
    private static final int COL_USER_TO_ROLE = 3;
    private static final int COL_SUB_ROLES = 4;
    private static final int COL_DESC = 5;
    private static final int COL_STATUS = 6;
    private static final int COL_PASSWORD = 7;

    private UserImportExcelParser() {
    }

    /**
     * @return 从**第 1 行**（第 0 行是表头）开始的每一行；空表/只有表头 ⇒ 空列表
     * @throws IOException 文件不是合法 Excel、或 col3/col4 的 JSON 不合法（厂商同样会抛出去 ⇒ 500）
     */
    public static List<ImportUserRowVO> parse(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                // 厂商：`backupLogService.sendRecoveryLog("user", "用户表格无内容，或内容不匹配,不做导入操作", …)`
                log.info("用户表格无内容，或内容不匹配,不做导入操作");
                return List.of();
            }
            List<ImportUserRowVO> rows = new ArrayList<>(lastRowNum);
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                rows.add(ImportUserRowVO.builder()
                        .username(readString(row, COL_USERNAME))
                        .nickname(readString(row, COL_NICKNAME))
                        .roleKey(readString(row, COL_ROLE_KEY))
                        .userToRole(readStringList(readString(row, COL_USER_TO_ROLE)))
                        .subRoles(readStringList(readString(row, COL_SUB_ROLES)))
                        .desc(readString(row, COL_DESC))
                        .statusText(readString(row, COL_STATUS))
                        .password(readString(row, COL_PASSWORD))
                        .build());
            }
            return rows;
        }
    }

    /**
     * 照抄 `ExcelUtils.formatCellType(cell, STRING).getStringCellValue()`：
     * 单元格不存在（或转换失败）⇒ {@code null}（调用方按"读不到"处理）。
     */
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

    /**
     * col3/col4 的 JSON 数组（厂商 `new Gson().fromJson(s, new TypeToken<List<String>>(){}.getType())`）。
     * <p>只在**非空白**时解析（厂商 `StringUtils.isNotBlank` 守卫）；字面量 `null` ⇒ `null`。
     * <p>格式不合法时 Jackson 抛 `JsonProcessingException`（是 IOException 的子类）⇒ 与厂商一样冒到 500。
     */
    private static List<String> readStringList(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.readValue(json, new TypeReference<List<String>>() {
        });
    }
}
