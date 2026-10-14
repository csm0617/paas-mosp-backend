# `POST /v1/software/softwareCode`：输出与厂商**逐字节相同**（含机器码）

**证据等级**：〔运行实测〕（逐字节对拉）+〔代码事实〕。2026-10-14。

## 1. 实测

| 入参 | vendor | mine | 结果 |
| --- | --- | --- | --- |
| `["appA","appB"]` | `eyJzZXJ2aWNlSWRMaXN0IjpbImFwcEEiLCJhcHBCIl0s...` | **完全相同** | ✅ 逐字节 |
| `["solo"]` | `eyJzZXJ2aWNlSWRMaXN0IjpbInNvbG8iXSw...` | **完全相同** | ✅ 逐字节 |
| `[]` | 400「接口参数错误」 | 同 | ✅（`trace` 天然不同，已按方法清单忽略） |

解码后的载荷（两侧相同）：

```json
{"serviceIdList":["appA","appB"],"machineCode":"NDIwREY5NzEtMDlFQy1CQTRBLUJFNzQtQjQ4NjhFQjI5QTUzLGdhb3A="}
```

## 2. 为什么这个端点能做到逐字节

厂商算法（`SoftwareService.getSoftwareCode:3166-3186`）**完全确定**：

```
Base64( JSON({"serviceIdList": 入参, "machineCode": getMachineCodeBase64ByService()}) )
```

- 机器码那一版**我们已经在第 34 轮实现并逐字节验证过**（`findings/42`：`原始码 + "," + "gaop"`，**无尾逗号**）——
  这次它第二次被间接验证（解码结果里就是那个值）；
- 载荷里**只有两个字段**，没有时间戳/随机量 ⇒ 天然可逐字节比对。

**这是"前面某个基础件验证过、后面端点就免费拿到强证据"的例子**：机器码那一轮花的力气在这里回本了。

## 3. 一处实现上的分层决定（值得记）

domain 模块**没有 Jackson**（依赖里没有）。第一版我把 JSON 组装写在域服务里 ⇒ 编译失败。
改成：**域服务只返回载荷对象 `SoftwareIdentifierVO`，JSON 组装与 Base64 放在 trigger 层**，
并用**显式 `ObjectNode`**（而不是依赖 POJO 字段顺序/注解）固定字段顺序 —— 因为**字段顺序就是契约**。

这个分层也更合理：序列化是接口层的关注点，域层不该依赖 JSON 库。

## 4. 顺带记：`GET /v1/software/{softwareId}/export` 为什么现在不做

它第一步就是 `detailSoftware(softwareId)` —— 而厂商的详情端点在参考环境**自身 NPE**
（`findings/51` A1）。所以 export 在本环境同样拿不到成功响应，**属于 C 类（无法对拉）**，
应当等详情端点的证据来源确定后再一并处理。

## 5. 状态

K2 跨域端点 **+1**；测试 **41 类 254 条全绿**（上一轮 252）。
临时 NodePort 已删、进程清零、工作树干净。
