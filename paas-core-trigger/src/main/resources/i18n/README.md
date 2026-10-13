# 厂商 i18n 消息包（契约数据，非拷贝实现）

## 为什么在这里

厂商错误响应里的 `msg` 字段**不是错误码，而是本地化后的文案**（证据：`recovered/gaop-be/.../config/ResponseExceptionConfig.java` 的
`messageSource.getMessage(key, args, locale)`，取不到时**回退成 key 本身**）。实测样本：
`GET /v1/lang/detail`（缺 `lang` 参数）→ `{"msg":"系统错误","code":-1,...}`。

前端把这些 `msg` 直接当 toast 文案用，所以 `msg` 属于契约（ADR-0006 的 L2）。要让 `msg` 逐字一致，
只能使用厂商的同一份消息包——这不是"拷贝实现"，而是**拷贝契约数据**（与 DDL、响应样本同性质）。

## 来源

| 文件 | 来源 |
| --- | --- |
| `messages.properties` | `recovered/gaop-be/src/main/resources/messages.properties`（983 行，默认=中文） |
| `messages_en_US.properties` | 同名文件（934 行） |
| `messages_fr_FR.properties` | 同名文件（921 行） |

**只读资产**：不得手改内容（改了就与厂商 `msg` 不一致）。需要新增本仓自己的消息时，另建 basename（如 `paas-messages`），
不要往这三个文件里加键。

## 接线

`spring.messages.basename=i18n/messages`、`spring.messages.encoding=UTF-8`（见 `paas-core-app/src/main/resources/application.yml`）。
Trigger 层的 `com.csm.paas.core.trigger.i18n.MessageResolver` 负责解析，**解析失败时回退成 key**（与厂商一致）。

## 已验证的键（切片 3 用到的）

`system.exception`、`user.unauthorized`、`role.can.not.edit.admin.key`、`role.can.not.delete.admin.key`、
`role.cannot.delete.storage`、`role.cannot.delete.op.admin`、`role.cannot.modify.super.op.admin.storage`、
`role.normal.user.cannot.user`、`role.normal.user.cannot.modify.other.user`、`role.identifier.reserved.word`、
`user.missing.user.info`、`user.invalid.search.keyword.length`、`user.not.logged.tip`、`user.not.found`、
`user.username.already.exists`、`user.data.limit.exceeded`、`global.import.supported.file.type`、`global.avatar.size.limit`
