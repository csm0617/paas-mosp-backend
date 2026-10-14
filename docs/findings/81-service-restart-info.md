# `GET /v1/service/restartInfo`：三种情形对拉一致 + **影像域依赖地图**

**证据等级**：〔运行实测〕（对拉）+〔代码事实〕。2026-10-14。

## 1. 本轮端点：`GET /v1/service/restartInfo?serviceName=`

厂商实现只有 15 行（`ServiceController:3950-3965`）：查 `g_pod_event`（`service_name` 等值、`create_at` 倒序），
映射成 `{podName, createTime, restartCount}`；**没有记录返回空数组**。

实测（用的是库里**已有的 3 行真实数据**，无需造探针）：

| serviceName | vendor | mine | 结果 |
| --- | --- | --- | --- |
| `gaop-web` | `[{podName:"gaop-web-96bbccb46-7rs5d", createTime:1791907176318, restartCount:1}]` | 同 | **完全相等** ✅ |
| `gaop-gateway-auth` | `[{podName:"…-5b9878ff77-hwgpd", createTime:1791907174695, restartCount:2}]` | 同 | **完全相等** ✅ |
| `no-such-svc` | `[]` | `[]` | **完全相等** ✅ |

两处容易写错、本轮都照抄对了：
- **键顺序**是 `podName → createTime → restartCount`（厂商 `PodRestartInfo` 的声明顺序，我第一版把后两个写反了）；
- 查询只取三列（不 `select *`），且**没有记录是空数组**，不是 404/400。

**服务切片：11/33。**

## 2. 顺带把**影像域的依赖地图**查清了（省得以后逐个试）

上一轮把 `projectList` 做对了（数据源是库 `g_group`）。本轮把影像域其余端点逐个看了数据源：

| 端点 | 数据源 | 本机/参考环境 |
| --- | --- | --- |
| `GET /v1/image/projectList` | **库 `g_group`** | ✅ 已实现并对拉一致 |
| `GET /v1/image/repositories` | `dockerRegistryApiService.getImageListByProject` → **Docker Registry API** | ❌ 外部 registry |
| `GET /v1/image/allProjectImageList` | 库 `g_group` + 上面那个 registry 调用 | ❌ 依赖 registry |
| `GET /v1/image/service/imageStorage` | **SSH 到 `sshConfig.host`** 取磁盘信息；连不上 → `{free:0,total:0}` | ❌ 外部 SSH（厂商自己也拿不到，返回 0/0） |
| `existRepositories` / `getImageTagList` | 都走 `getImageTagList` → registry | ❌ |

**结论**：影像域里**只有 `projectList` 是纯库读取**；其余都依赖 registry / Harbor / SSH 这些外部系统，
在本机不可达、厂商在参考环境里也大多拿不到真实数据（`imageStorage` 就是 0/0）。
⇒ 影像域的可验证部分**基本到 `projectList` 为止**，其余属于"依赖外部设施"一类，
**不要**在没有对端的情况下硬写（这正是我上一轮踩过的坑）。

## 3. 状态

服务切片 11/33；测试 **41 类 268 条全绿**（上一轮 266）。
临时 NodePort 已删、进程清零、工作树干净。
