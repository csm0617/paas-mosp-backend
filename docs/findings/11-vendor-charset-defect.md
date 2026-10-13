# 厂商编码缺陷：DB 里的中文在 HTTP 响应里被双重编码

## 观察（三条互相独立的证据）

1. **库里是好的**：`select id,\`key\`,name from g_role` 直接返回 `运维管理员门户` / `席位操作员`（MySQL 8，utf8mb4_bin）。
2. **源码里的中文是好的**：厂商 `/v3/api-docs` 走同一条代理链路，摘要显示 `用户详情`、`修改指定用户的信息…`——**代理是透明的**。
3. **从 DB 取出的中文在响应里是乱码**：`GET /v1/role` 的原始字节是
   `"name":"\xc3\xa8\xc2\xbf\xc2\x90\xc3\xa7\xc2\xbb\xc2\xb4…"`，即 UTF-8 的 latin-1 误读再做一次 UTF-8 编码（双重编码）。
   同一条响应里**硬编码**的 `appName`（源码字面量 `运维管理员门户`）却是正确中文——差异只出现在经过数据库的字符串上。

## 高度可能的成因（未做端到端实证）

厂商用 `mysql-connector-java:5.1.48`，JDBC URL 是
`jdbc:mysql://…/gaop?characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&useSSL=false&&serverTimezone=UTC`
——**缺 `useUnicode=true`**。Connector/J 5.x 下，`characterEncoding` 只有在 `useUnicode=true` 时才生效，
于是连接按服务端默认字符集处理，中文被误读为 latin-1，再序列化成 UTF-8 就成了双重编码。
（URL 里那个双 `&&` 也是同一个配置的粗糙痕迹。）

## 对重写与门槛的影响

- 我们的重写后端用 `com.mysql:mysql-connector-j` 8.x，URL 带 `useUnicode=true&characterEncoding=utf8`
  → **返回正确中文**。这是**有意修正**，不是偏离。
- 因此凡是"值来自 DB 的中文字段"，线上字节不可能与厂商一致——这条已记入
  `read-paths.json` 的 `knownDeviations`（`GET /v1/role` 的 `name`/`appName`）。
- 前端受影响面有限：读 `/v1/role` 的两个页面读的是 `key`/`type`/`createdAt`/`updatedAt`/`softwareList`，
  **不读 `name`**；但其它页面（用户管理、战位管理表格）会显示名称，届时看到的是**我们更正确**的文本。

## 若要端到端坐实

用同一份库、同一驱动跑两次厂商 jar（URL 加/不加 `useUnicode=true`），比对响应字节即可。
目前只有静态证据链（上述三条）+ 驱动版本与 URL 的比对，故标为"高度可能"。
