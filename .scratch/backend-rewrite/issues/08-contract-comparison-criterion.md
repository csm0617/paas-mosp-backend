Type: grilling
Status: open
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

## 验收

答案写进 `## Answer`，并据此更新 ADR-0004 里"响应 JSON 形状"的判定方式。

## Answer
