# `GET /v1/image/projectList` 按 K3s 实现重写后：**与厂商逐字节相同**

**证据等级**：〔运行实测〕（在线对拉，两种入参）+〔代码事实〕+〔离线单测〕。2026-10-14。

## 1. 实测

| 请求 | 结果 |
| --- | --- |
| `?page=1&size=20` | **整个响应体完全相等** ✅（2 条：`library` / `gaop`，含 `+58753-04-29…` 的畸形时间与 `project_id`） |
| `?page=1&size=20&keyword=gaop` | **完全相等** ✅ |

上一轮（`findings/78`）的 Harbor 版**数据源就是错的**，所以这条"逐字节相等"才是它第一次真正对上。

## 2. 按 K3s 实现重写的内容（逐行照抄）

```
① 按 name like ? 查 g_group、create_date 倒序、分页
② 若查询为空 → 依次取/建 system_containers / gaop / library（查不到就 INSERT，create_date = now 毫秒）
③ 库里的行**追加**在内置项目之后
④ 过滤掉**只有** system_containers（没有 system_store）
⑤ 映射：只设 projectId / name / creationTime，其余 8 个字段保持 null
   creationTime = ISO8601(Instant.ofEpochSecond(createDate), Asia/Shanghai)，格式 yyyy-MM-dd'T'HH:mm:ssXXX
⑥ total = 过滤后的条数
```

**第 ⑤ 步里的"毫秒当秒"是厂商缺陷**（`+58753` 年）——实测对拉一致，说明我照抄对了；
如果"顺手修正"成毫秒，这个端点立刻就会与厂商不一致。

## 3. 顺带清理

- 删掉了上一轮那套 Harbor 实现（gateway / port / 端口接口 / `application.yml` 里的 `server.host.harbor`
  与 `docker.*` 配置）——**参考环境里 `getImageServiceInstance()` 无条件返回 k3s 实现，Harbor 路径不可达**，
  留着就是死代码（这条纪律本会话一直在用：没有调用方的东西不写）。
- `docs/findings/78` 保留并标注"数据源错误，见 79/80"。

## 4. 一个必须记住的**副作用**

第 ② 步会在"表为空"时**写库**（创建 3 个内置项目）。本环境 `g_group` 已经被厂商自己写入了 3 行
（`system_containers`/`gaop`/`library`，时间戳是毫秒），所以本次对拉**没有触发写入**——
清理时核对过 `g_group` 仍是 3 行。**若在干净库上做对拉，必须预期并处理这 3 行写入。**

## 5. 测试

域规则 3 条：只过滤 `system_containers` 且保持库序、`creationTime` 把毫秒当秒（断言 `+58753-` 前缀）、
空表时创建三个内置项目（断言插了 3 次、过滤后剩 2 条）。
全量 **41 类 266 条全绿**。

## 6. 状态

`g_group` 仍 3 行（未被本次对拉改动）；临时 NodePort 已删、进程清零、工作树干净。
