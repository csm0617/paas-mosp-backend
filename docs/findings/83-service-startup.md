# `POST /v1/service/startUp`：独立 namespace e2e 通过（服务切片 13/33）

**证据等级**：〔运行实测〕（独立 namespace）+〔代码事实〕+〔从厂商 jar 里取到的配置默认值〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-su2`，用完即删）

| 用例 | 结果 |
| --- | --- |
| A：`time=30` | 200 `data:true`；注解 `gaop/serviceInitTime="30"`；initContainer `name=startup`、**`image=192.168.1.52:30002/system_containers/curl-alpine:latest`**、`command=["sh","/data/curl.sh"]`、`env=[{_SERVICE_NAME:"su2-svc"}]` ✅ |
| B：`time=0` | 200；注解与 initContainers **都被清掉**（走"取消"分支）✅ |
| C1：`time=-1` | 400「延时启动时间不能为负数」✅ |
| C2：服务不存在 | 400「获取服务: no-such 配置信息出现异常，无法正常设置延时启动时间，请查看是否还存在该服务」✅ |

**镜像字符串完全符合厂商公式**：`<harborIp>/system_containers/<initPodConfig.image>`，
其中 `harborIp` 是把 `server.host.harbor` 去掉 `http(s)://` 前缀。

## 2. 配置项的来历（不是猜的）

厂商 `initPodConfig.image` 的默认值是从**厂商运行中的 jar 里**读出来的：

```
BOOT-INF/classes/application.yml
initPodConfig: image: ${INITIMAGE:curl-alpine:latest}
```
所以我们用**同名配置** `initPodConfig.image`（默认值照抄 `curl-alpine:latest`），
Harbor 地址用 `server.host.harbor`（厂商同名）。

## 3. 一个循环里的**双分支**（容易只写一半）

`time == 0` 与 `time > 0` 走**两条不同路径**，连 K8s 调用方式都不同：

| time | 注解 | initContainers | K8s 调用 |
| --- | --- | --- | --- |
| `0` | **删除** `gaop/serviceInitTime` | 置 null | **整份 replace** |
| `> 0` | **写入** `gaop/serviceInitTime = <秒>` | 注入 `startup` 容器 | **merge patch** |

（`cancelStartUp` 那个端点相当于只做 `time=0` 这一支——两个端点共享同一段语义。）

## 4. 状态

服务切片 **13/33**；测试 **41 类 273 条全绿**（上一轮 270）。
临时 namespace 已删（NotFound）、NodePort 已删、进程清零、工作树干净。
