# `GET /v1/image/projectList` 实现：影像域从"已排除"回到"已实现"

**证据等级**：〔代码事实〕+〔离线回放〕（用厂商实测样本）+〔网络实测〕（说明为什么不能在线对拉）。2026-10-14。

## 1. 上一轮的判断被推翻并落地

`findings/77` 证明了 **Harbor 是通的**（厂商从它读出 `library`/`gaop` 两个项目），
所以影像域从"依赖缺失设施（C 类）"回到"可实现"。本轮把它实现了。

## 2. 实现（三步照抄 + 三个同名配置项）

```
GET /v1/image/projectList?page&size&keyword
  → IHarborPort.listAllProjects(keyword)        // Harbor: GET /api/v2.0/projects?page&page_size=100[&name=]
  → 过滤 system_containers / system_store
  → 按 creation_time **字符串**倒序（厂商就是这么比的）
  → skip((page-1)*size).limit(size)，total = 分页前总数
```

**配置项与厂商同名**（便于真实环境复用同一份配置）：
`server.host.harbor`、`docker.username`、`docker.password`。
**仓库里不写口令**：用 `${SERVER_HOST_HARBOR:}` / `${DOCKER_USERNAME:}` / `${DOCKER_PASSWORD:}` 注入。

两处照抄细节：
- 鉴权头是 `Basic ` + **URL-safe** base64(`user:pass`)（厂商用 `Base64.getUrlEncoder()`，不"顺手修正"）；
- `creation_time` 是 **String** 且可能畸形（实测出现过 `+58753-04-29…`，公元 58753 年）——照抄，不解析成时间。

## 3. 最容易写错的地方：**键名是 snake_case**

厂商 `ProjectItem` 用 `@JsonProperty("creation_time")` 等把 camelCase 转成 snake_case；
我们域对象是 camelCase，若交给 Jackson 默认序列化就会输出 `creationTime` —— **形状错**。
所以用 `ImageProjectAssembler` 显式 Map 固定 11 个键的**名称与顺序**，
并写了一条**回放测试**：从实测样本里读键集合，断言我们的键集合与顺序一致。

## 4. 为什么本轮**没有**在线对拉（如实标注）

`HARBOR_HOST=192.168.1.52:30002` 是集群内可达的地址；**本机直连超时**（实测 timed out），
而且 Harbor 不在这个集群里（`kubectl get svc -A` 无 harbor/registry）。
⇒ 我们跑在本机的后端**到不了 Harbor**，本轮的 `/v1/image/projectList` 在本机只能返回空列表。

**因此正确性由两件事保证**（而不是"看起来对"）：
1. 三步规则与厂商代码逐行对应（含过滤名单、字符串倒序、分页口径）；
2. **回放测试**用厂商实测样本锁定键名与顺序。

**要拿到在线对拉**，需要把后端**部署进集群**（或让 Harbor 对本机可达）——这是一次拓扑变更，
已记为待办；它不是"实现问题"。

## 5. 状态

新增：影像域（port/service/gateway）+ `ImageProjectAssembler` + 4 条测试；
全量 **41 类 269 条全绿**（上一轮 262）。工作树干净。
