package com.csm.paas.core.trigger.assembler;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `POST /v1/software/image_base64` 的格式守卫与编码
 * （厂商 `SoftwareService.imageToBase64Str:2543-2554`）。
 * <p>规格见 `docs/findings/132`：Tika 探测 MIME + 正则 `image/(jpg|jpeg|png|bmp|BMP|JPG|PNG|JPEG|webp)$`
 * （用 `find()`、无 `^`），通过后返回**裸 base64**。
 */
class ImageBase64EncoderTest {

    /** 1x1 的合法 PNG（真实字节，让 Tika 能探测出 image/png）。 */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    void pngIsAccepted() throws Exception {
        assertTrue(ImageBase64Encoder.isImage(new ByteArrayInputStream(PNG)));
    }

    @Test
    void textFileIsRejected() throws Exception {
        assertFalse(ImageBase64Encoder.isImage(
                new ByteArrayInputStream("not an image at all".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void excelIsRejected() throws Exception {
        // xls 的 OLE2 魔数（与 ExcelFileValidator 接受的那类相反：这里必须拒绝）
        byte[] ole2 = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        assertFalse(ImageBase64Encoder.isImage(new ByteArrayInputStream(ole2)));
    }

    @Test
    void encodeReturnsBareBase64() {
        String encoded = ImageBase64Encoder.encode(PNG);
        assertEquals(Base64.getEncoder().encodeToString(PNG), encoded);
        assertFalse(encoded.startsWith("data:"), "厂商返回的是裸 base64，不带 data: 前缀");
        // 能解回原字节
        assertTrue(java.util.Arrays.equals(PNG, Base64.getDecoder().decode(encoded)));
    }
}
