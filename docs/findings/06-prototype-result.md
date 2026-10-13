# 工单 06 结论：软件-服务-端口 纵向切片在真实集群数据上跑通

2026-10-14 02:37，本机运行 `java -jar paas-core-app-1.0.0-SNAPSHOT.jar`，fabric8 通过本机 kubeconfig 直连 172.16.209.25:6443。

## 成立的部分

| 验证点 | 结果 |
| --- | --- |
| 启动 | `/internal/health` → 200 |
| `GET /v1/service` | **200 / 73,698 B**，真实集群数据（10 个 Service / 9 个 Deployment） |
| 信封 | `msg`/`code`/`data` 与厂商**逐字一致** |
| `data` 键 | `runningDeployment`/`total`/`count`/`services` 与厂商一致 |
| 单项键数 | **19 / 19**，既不缺也不多 |
| 具名端口 | fabric8 7.9.0 用 `IntOrString`，数字与字符串端口都能反序列化 |
| 分层 | Trigger(`ServiceController`) → Domain(`ServiceQueryServiceImpl`) → Port(`IServiceRuntimePort`) ← Infra(`ServiceRuntimePort` → `ServiceRuntimeGateway` → fabric8)，无跨层依赖 |

**结论：JDK 21 + Spring Boot 3.3 + fabric8 7.9.0 + 六边形分层这条路是通的。**

## 第三轮补强（2026-10-14 02:44）

三件事都做完并实测：

1. **列表改成 workload 驱动**（照 `TenxService.java:2307-2342`：StatefulSet→Deployment 遍历再找同名 Service）
   → `total/count` 由 10 变成 **9，与厂商一致**；`gaop-mysql` 不再出现。
2. **`runningDeployment` 改用厂商规则**（`replicas > 0` 计数，`TenxService.java:2349`）→ 我方 5；**厂商运行态仍是 0，原因未定论**。
3. **MyBatis + 真实 MySQL 打通**（此前未验证的最后一环）：日志证据
   `paas-core-hikari - Added connection com.mysql.cj.jdbc.ConnectionImpl@…`、
   `ServiceAuthRepository : g_service_auth 读到 0 行`（MySQL 经 NodePort `172.16.209.25:30306`）；
   `serviceAuth` 由该查询回填——表空 → null，与厂商运行态一致。

**同一个请求路径上，fabric8（10 Service/0 StatefulSet/9 Deployment）、MyBatis（真实查询）、Spring MVC（JDK 21）全部跑通。**

## 四处真实差异（原型最值钱的产出）

1. ~~列表驱动源不同~~ **已修复**（见上：改为 workload 驱动，`total` 与厂商一致）。
2. **`runningDeployment` 语义**：厂商=0，我=5。
   厂商规则可查：`TenxService.java:2349` `(int) services.stream().filter(o -> o.getDeployment().getSpec().getReplicas() > 0).count()`；
   按该规则在本集群应为 **5**（`replicas>0` 的有 gaop-auth/be/gateway-auth/redis/web）。
   → **厂商为何返回 0 仍是未定论**（两种假设：转换后 `replicas` 丢失/为 0；或运行时走的是另一条路径）。**不猜**，留给 02/07 用代码追。
3. **null 序列化策略不同**（决定门槛怎么判）：
   - 厂商（client-java `V1*` 模型）输出**大量显式 null**：metadata 7 键、spec 8 键、`ports[].nodePort` 等；
   - fabric8 7 + Jackson 默认**省略 null**，且多 4 个新字段（`clusterIPs`/`internalTrafficPolicy`/`ipFamilies`/`ipFamilyPolicy`）；
   - 厂商对象还多一个**自造键 `proxy`**。
   → **逐字节等价做不到**（除非复刻 client-java 的序列化噪声）。判定标准见工单 08。
4. **原型没有认证**：厂商 `/v1/service` 需要 `gateway-token` cookie。原型范围内可接受，但过门槛前必须补。

## 复现方式

```bash
mvn -DskipTests package
java -jar paas-core-app/target/paas-core-app-1.0.0-SNAPSHOT.jar     # 需本机 kubeconfig 能连到集群
curl -s http://127.0.0.1:8080/v1/service > /tmp/mine-service-list.json
python3 tools/diff-service-list.py /tmp/mine-service-list.json     # 与厂商样本对比，输出写入 06-prototype-diff.txt
```

对比明细见 `06-prototype-diff.txt`；厂商基线见 `runtime-samples/`。
