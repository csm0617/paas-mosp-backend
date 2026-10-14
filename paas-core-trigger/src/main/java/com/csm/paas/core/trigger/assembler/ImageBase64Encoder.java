package com.csm.paas.core.trigger.assembler;

import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 厂商 `SoftwareService.imageToBase64Str:2543-2554` 的等价实现：
 * 先用 **Tika** 探测 MIME，只有匹配 `image/(jpg|jpeg|png|bmp|webp)`（**大小写两种写法都列了**）
 * 才把文件内容 base64 编码返回。
 *
 * <p>照抄两点：
 * <ol>
 *   <li>用的是 `Matcher.find()` **不是** `matches()`，且正则**没有 `^`**——只要能"以这些类型结尾"就通过；</li>
 *   <li>格式不对 ⇒ 400 `global.image.format.error`（由调用方抛）。</li>
 * </ol>
 */
public final class ImageBase64Encoder {

    /** 逐字照抄厂商的正则（含大写变体与末尾 `$`）。 */
    private static final Pattern IMAGE_CONTENT_TYPE =
            Pattern.compile("image/(jpg|jpeg|png|bmp|BMP|JPG|PNG|JPEG|webp)$");

    private ImageBase64Encoder() {
    }

    /** 与厂商一致：Tika 探测出的 MIME 命中上面那条正则才算图片。 */
    public static boolean isImage(InputStream in) throws IOException {
        return IMAGE_CONTENT_TYPE.matcher(new Tika().detect(in)).find();
    }

    /** 与厂商一致：直接 base64 整个文件内容（**不**加 `data:` 前缀）。 */
    public static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
