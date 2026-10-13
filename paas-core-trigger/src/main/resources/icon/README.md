# 厂商默认头像（契约数据，非拷贝实现）

`avatar` 文件 = 厂商 `classpath:icon/avatar` 的**原样内容**（2896 B 的 base64 JPEG，无 `data:` 前缀）。

来源：`recovered/gaop-be/src/main/resources/icon/avatar`（厂商 `UserConfig.getDefaultAvatar()` 读它）。

**为什么算契约**：`POST /v1/user` 不带 `avatar` 时，厂商把这份内容写进 `g_user.avatar`
（`UserService.java:511`），随后 `/v1/user/{userId}` 会把它返回给前端。前端直接把它当 `src` 用，
所以字节必须一致。**只读资产，不得手改**。
