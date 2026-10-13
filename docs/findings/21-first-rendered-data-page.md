# 里程碑：厂商前端不改一行，渲染出我们后端的真实数据（2026-10-14 04:25）

## 证据

页面 `GET /gaop/software/application/service`（左侧「应用配置 → 应用服务」）在**厂商原样前端**下渲染出：

- **表头**：序号 / 服务名称 / 服务标识 / 服务类型 / 所属应用 / 状态 / 镜像 / 授权状态
- **9 行数据**：gaop-auth、gaop-be、gaop-diagnostic、gaop-fdfs、gaop-gateway-auth、gaop-image-uploader、gaop-startup、gaop-redis、gaop-web
- **状态列来自真实集群**：`运行中 1/1 全部运行 已重启 0 次` / `已停止 0/0 全部停止`
- **数据来源**：`200 GET /api/gaop/v1/service?page=1&size=10&keyword=&authType=` —— 就是本仓的 `/v1/service`（fabric8 读真实 gaop-namespace）
- 同页其它调用全部 200：`/v1/user`、`/settings`、`/v1/mode/current`、`/v1/lang/current`、`/v1/licence/info`、`/v1/pub/quickStart`、`/v1/pub/etcd_label/check`、`/v1/role/software`、`/v1/image/projectList`
- 截图：`docs/findings/artifacts/2026-10-14-service-page.png`

**这就是第一条纵切（软件-服务-端口）的 e2e 实测**：厂商前端（一行未改）→ 我们的后端 → 真实集群数据 → 页面表格。
配合前两轮的结果，门槛三条均已具备证据：① 前端不改（本页真实渲染）② 契约比对（`tools/contract-check.py` L1+L2 通过，2 条已记录偏离）③ e2e（本页 + 登录/会话/外壳）。

## 顺带修的一处数据缺口（部分生效）

`/v1/service` 的 `imageInfo` 之前恒为 null，而厂商返回的是**容器镜像串**（如 `system_containers/gaop-auth:0.0.3-arm64`）。
已从 workload 快照的 `spec.template.spec.containers[0].image` 填充，现在 9 行的 `imageInfo` 与厂商格式一致：

```
gaop-auth      -> system_containers/gaop-auth:0.0.3-arm64
gaop-be        -> gaop-be:V1.3.4-20260415-1857-arm64
gaop-diagnostic-> system_containers/gaop-diagnostic:1.0.0-arm64
gaop-fdfs      -> gaop-fdfs:0.0.18-arm64
```

## 仍未定论：页面「镜像」列仍显示 `undefined/undefined`

截图里部分行的镜像列是 `undefined/undefined`（部分行有值）。已确认：
- 我们返回的 `imageInfo` **是对的**（curl 直连可见，格式与厂商样本逐字一致）；
- 但产物里**没有任何 `imageInfo` 字面量**（全量 grep 0 命中）→ **该列不是读 `imageInfo` 的**，它读的是别的字段（`imageName/imageTag` 只在 CI 页出现）。

**下一步**：定位服务表「镜像」列真正的 dataIndex（在 `p__ServiceManage`/`p__BaseServices` 的列定义里找 `MIRROR`/`IMAGE` 文案附近的 `dataIndex`），再补该字段。

## 其它残留

- `404 GET /api/gaop/register/ip/harbor`：前端启动时会 ping 的 Harbor 注册地址，属镜像域（外部系统），当前未实现，**不影响页面渲染**。
