package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.language.model.valobj.LanguageImportEntryVO;
import com.csm.paas.core.domain.language.model.valobj.LanguageImportParseResultVO;
import com.csm.paas.core.types.exception.WrongArgumentException;
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
 * 语言包导入 Excel 解析（厂商 `LanguageServiceImpl.importLanguageExcel:90-246` 的"读单元格"部分）。
 *
 * <p><b>为什么这一段在 trigger</b>：POI 是 domain 层没有的依赖（硬约束），
 * 而"单元格读不到"在厂商那里**带语义**（module/textKey/content 读不到 ⇒ 400，且带行号），
 * 所以在这里翻译好再交给领域层。
 *
 * <p>照抄点：
 * <ol>
 *   <li>只看 sheet0；`lastRowNum < 1` ⇒ 返回**空 rows**（领域层据此返回 false，不写库）；</li>
 *   <li>生效语言默认 `zh_CN`，**只在第 1 行**（Excel 第 2 行）读第 5 列：厂商直接
 *       `row.getCell(5).getStringCellValue()`——单元格是数字/不存在都会抛，**被 catch 吞掉**后
 *       保持默认语言；成功则 `replaceAll("-","_")`（`BaseLanguageEnum.lang2Underline`）；</li>
 *   <li>col0 module / col1 textKey / col3 content：`STRING` 取字符串、`NUMERIC` 取
 *       `String.valueOf((long)value)`、其它类型为 null；读单元格的异常被 catch 吞掉后仍为 null
 *       ⇒ 随后用 **`isEmpty`（不是 isBlank）** 判定并抛 400（**带行号 `i+1`**）；</li>
 *   <li>col2 description / col4 abbr 可空，且厂商这两处的 catch 是**空的**（不记日志）。</li>
 * </ol>
 */
@Slf4j
public final class LanguageImportExcelParser {

    /** 第 1 行第 5 列是语言标识（厂商 `row.getCell(5)`）。 */
    private static final int COL_LANG = 5;

    private LanguageImportExcelParser() {
    }

    /**
     * @return 生效语言 + 词条行（词条为空等价于厂商 `lastRowNum < 1`）
     * @throws IOException 不是合法 Excel（厂商在服务里 catch IOException ⇒ 400
     *                     `language.parse.language.package.exception`）
     * @throws WrongArgumentException module/textKey/content 为空（厂商同款 400 键，带行号）
     */
    public static LanguageImportParseResultVO parse(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                log.info("表格无内容，或内容不匹配,无法进行导入语言信息");
                return LanguageImportParseResultVO.builder()
                        .lang("zh_CN")
                        .rows(List.of())
                        .build();
            }
            String lang = "zh_CN";
            List<LanguageImportEntryVO> rows = new ArrayList<>(lastRowNum);
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (i == 1) {
                    try {
                        String langInfo = row.getCell(COL_LANG).getStringCellValue();
                        if (langInfo != null && !langInfo.isBlank()) {
                            lang = langInfo.replaceAll("-", "_");
                        }
                    } catch (Exception e) {
                        log.error("获取导入语言包的语言类型出错设置默认语言zh_cn，异常为:", e);
                    }
                }
                String module = readText(row, 0, "获取新导入的模块标识出错，异常为:");
                if (module == null || module.isEmpty()) {
                    throw new WrongArgumentException("language.import.module.must.not.null", i + 1);
                }
                String langKey = readText(row, 1, "获取新导入的标识字符出错，异常为:");
                if (langKey == null || langKey.isEmpty()) {
                    throw new WrongArgumentException("language.import.key.must.not.null", i + 1);
                }
                String description = readText(row, 2, null);
                String langContent = readText(row, 3, "获取新导入的语言值出错，异常为:");
                if (langContent == null || langContent.isEmpty()) {
                    throw new WrongArgumentException("language.import.content.must.not.null", i + 1);
                }
                String abbr = readText(row, 4, null);
                rows.add(LanguageImportEntryVO.builder()
                        .module(module).textKey(langKey).description(description)
                        .content(langContent).abbr(abbr)
                        .build());
            }
            return LanguageImportParseResultVO.builder().lang(lang).rows(rows).build();
        }
    }

    /**
     * 厂商逐格取值：`STRING` ⇒ `getStringCellValue()`；`NUMERIC` ⇒ `String.valueOf((long)value)`；
     * 其它 ⇒ null。单元格不存在（`row` 为 null 或 cell 为 null）时 NPE 被吞掉 ⇒ null。
     *
     * @param errorMessage 非 null 时按厂商在 catch 里记日志；null 表示厂商的**空 catch**（description/abbr）
     */
    private static String readText(Row row, int col, String errorMessage) {
        try {
            Cell cell = row.getCell(col);
            if (cell.getCellType() == CellType.STRING) {
                return cell.getStringCellValue();
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                return String.valueOf((long) cell.getNumericCellValue());
            }
            return null;
        } catch (Exception e) {
            if (errorMessage != null) {
                log.error(errorMessage, e);
            }
            return null;
        }
    }
}
