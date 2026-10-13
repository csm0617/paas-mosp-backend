Type: grilling
Status: closed (2026-10-14，采纳选项 1「语义等价」；落 ADR-0006)
Blocked by: —

## Question

**契约比对按什么判定？**（工单 06 在真实数据上暴露的问题，HITL）

事实：厂商响应里带**大量显式 null**（metadata 7 键、spec 8 键、`ports[].nodePort`），因为它用的是 client-java 的 `V1*` 模型；
fabric8 7 + Jackson 默认省略 null，并且多出 4 个较新的 K8s 字段；厂商对象还多一个自造键 `proxy`。
证据：`docs/findings/06-prototype-diff.txt`。

选项：

1. **语义等价（推荐）**：以"厂商前端**实际读取的字段路径 + 值**"为判定，其余差异（null、多出的新字段、`proxy`）记入"已知偏离"清单。
   需要 02 先产出前端 196 个函数的读取路径清单。
2. **逐字节等价**：必须复刻 client-java 的 null 噪声与 `proxy` 键。代价高、脆（K8s 加字段就会漂移），但门槛最硬。
3. **弱判定**：只要求页面能渲染、不报错。最省事，但等于放弃"契约"这条门槛。

## 决策所需的证据（已备齐，2026-10-14）

`docs/findings/09-frontend-read-paths.md`（静态检索全部前端 chunk）：

- 19 个单项键里 **7 个前端从不读**：`runningDeployment`、`fixedName`、`imageInfo`、`volumeTypeList`、`cpuOrder`、`memoryOrder`、`gpuOrder`（全产物 0 次出现）
  → 厂商 `runningDeployment=0` 与我方 `5` **对前端不可见**；
- 前端确实读：`services`、`total`、`count`、`deployment.metadata.name/.namespace/.spec.template.*`、`restartCount`、`serviceAuth.auth`、`mappingInfo`、`nodePortService`、`podInfoList`、`belongNodeList`、`nameFr/nameUs`；
- 列表项里的 `service.spec.*` 前端**不读**（`spec.ports` 只出现在**写**路径）；读 `spec.ports` 的是 **K8s 代理**那条链。

⇒ 「逐字节等价」的全部代价花在前端看不见的键上；但 `serviceAuth`/`restartCount` 必须填（`serviceAuth` 为 null 会直接显示"认证失败"）。

## 工具已就绪（两条路都能走，决定权仍在你）

`tools/contract-check.py` 已经实现三层判定：L1 形状 / L2 前端读取路径 / L3 已知偏离；
读取路径清单在 `docs/findings/read-paths.json`（可随域扩展）。当前 `GET /v1/service`：L1、L2 全过，
另有 **2 条"允许偏离"**（`restartCount`、`mappingInfo`，理由已逐条写入清单）——**需要你在本工单追认或否决**，
以及决定 L3 的差异清单是"记录在案"还是"必须补齐"。

## 追加（2026-10-14）：判定已可自动化

`mvn test` 现在离线跑 L1+L2（含允许偏离的受控校验），`tools/contract-check.py` 在线跑同一套判定。
**所以你只需要定一件事**：L2 的两条允许偏离（`restartCount` 显示等价、`mappingInfo` 我们更完整）是追认还是否决；
以及 L3 的差异清单（client-java 的 null 噪声 / `proxy` 键 / fabric8 多出的新字段）是"记录在案"还是"必须补齐"。

## 验收

答案写进 `## Answer`，并据此更新 ADR-0004 里"响应 JSON 形状"的判定方式。→ 已落 [ADR-0006](../../docs/adr/0006-contract-semantic-equivalence.md)

## Answer

**已定（2026-10-14）：选选项 1「语义等价」，细节落 [ADR-0006](../../docs/adr/0006-contract-semantic-equivalence.md)。**

- **L1 形状 / L2 前端读取路径**：不绿 = 缺陷，必须修；键的类型按**值**判等价（数字比数值）。
- **L3 已知偏离**：**记录在案**（不是"必须补齐"）。
- **两条 `allowDeviation` 全部追认**：`restartCount`（`display-equivalent`）、`mappingInfo`（`at-least-as-complete`，且**反向不允许**——我们给空而厂商给值即算缺陷）。
- **新增的第四条规则（把"语义等价"与"弱判定"区分开）**：L3 中任何偏离一旦被证明**前端可见**，立即升级为 L1/L2 缺陷。`proxy` 键是否被前端读取**仍未定论**（产物里未检索到读路径），入账时不得标成永久豁免。
- `deviationKind` 只允许 `display-equivalent` 与 `at-least-as-complete` 两类；需要第三类必须先说明再加。
