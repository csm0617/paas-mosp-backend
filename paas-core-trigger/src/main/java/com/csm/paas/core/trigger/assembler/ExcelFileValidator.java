package com.csm.paas.core.trigger.assembler;

import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 厂商 `ValidateUtils.isExcelFile`（`utils/ValidateUtils.java:18-23`）的等价实现。
 *
 * <p>厂商用 **Tika** 做 **MIME 探测**（不是看扩展名！），接受这 4 种：
 * `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`（xlsx）、
 * `application/x-tika-ooxml`、`application/x-tika-msoffice`、`application/vnd.ms-excel`（xls）。
 */
public final class ExcelFileValidator {

    private static final List<String> EXCEL_MIME_TYPES = List.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/x-tika-ooxml",
            "application/x-tika-msoffice",
            "application/vnd.ms-excel");

    private ExcelFileValidator() {
    }

    /** 与厂商一致：探测出的 MIME 在这 4 种里才算 Excel。 */
    public static boolean isExcel(InputStream in) throws IOException {
        return EXCEL_MIME_TYPES.contains(new Tika().detect(in));
    }
}
