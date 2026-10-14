# `POST /v1/software/image_base64` 规格 + 实现（第 139 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:700-705` + `SoftwareService.imageToBase64Str:2543-2554` 逐行读）
+〔运行实测〕（真集群，字节级回环校验）。2026-10-14。

## 1. 端点

```java
@PostMapping(value = "/image_base64", consumes = {"multipart/form-data"})
public Response<String> imageToBase64(MultipartFile file)   // 厂商的 MultipartFile 无注解 ⇒ 部件名取参数名 file
```
前端（`p__software.*.async.js`）把它当**上传动作**调：`action: "/api/gaop/v1/software/image_base64"`，
`file: originFileObj`，另外还带一个 `flag` 字段（厂商**不读**它）。

## 2. 流程（很短，但守卫有两处照抄点）

```
1. contentType = new Tika().detect(file.getInputStream())
2. 正则 `image/(jpg|jpeg|png|bmp|BMP|JPG|PNG|JPEG|webp)$`
   **不匹配 ⇒ 400 `global.image.format.error`（"请上传格式为图片类型的文件"）**
3. 通过 ⇒ Base64.encode(file.getBytes())，**返回裸 base64**（不带 `data:` 前缀）
```

| # | 照抄点 | 说明 |
| --- | --- | --- |
| 1 | 用 **`Matcher.find()` 而不是 `matches()`**，且正则**没有 `^`** | 只要"以这些类型结尾"就通过（Tika 返回的是精确 MIME，所以实际等价于精确匹配，但写法要照抄） |
| 2 | 正则里**同时列了大写变体**（`BMP`/`JPG`/`PNG`/`JPEG`） | Tika 不会返回大写 MIME，所以那几个分支是死代码；照抄保留 |

## 3. 实现与验证

**实现**：全部放在 **trigger**（与 `ExcelFileValidator` 同款：Tika 是 trigger 的依赖，domain 不碰）——
新增 `ImageBase64Encoder`（`isImage` + `encode`）与控制器端点。**没有 domain 参与**，因为这里除了格式守卫没有业务规则。

### 3.1 离线：`ImageBase64EncoderTest` 4 例，全量 **446** 条 0 失败

覆盖：真 PNG ⇒ 通过；纯文本 ⇒ 拒绝；xls 的 OLE2 魔数 ⇒ 拒绝（与 `ExcelFileValidator` 的判定相反）；
`encode` 返回**裸 base64**（无 `data:` 前缀）且能解回原字节。

### 3.2 在线：真集群（1×1 PNG 70 字节 / 文本 / OLE2 魔数文件）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | 真 PNG | `200`，`data` 长度 96、**无 `data:` 前缀**，`base64decode(data) == 原文件字节` ⇒ **字节级回环一致** |
| ② | 纯文本 | `400 请上传格式为图片类型的文件` |
| ③ | 8 字节 OLE2 魔数（xls 头） | `400` 同上 ⇒ 守卫确实按 MIME 而不是扩展名 |
| ④ | PNG + 额外字段 `flag=true`（前端实际会带） | `200`，与 ① 同长度 ⇒ 多余部件被忽略 |

### 3.3 收尾

临时 NodePort 与后端进程已回收；本轮不写库、不建命名空间（实验室数据未动）。
