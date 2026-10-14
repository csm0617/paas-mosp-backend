# 补掉最后一条「近似」：keyword 兜底匹配的精确规则（不是我以为的那条）

**证据等级**：〔代码事实〕（逐行）+〔离线实测〕（3 条用例）。2026-10-14。

## 1. 厂商的精确规则（`ServiceController.filterServiceName:2070-2081`）

```java
private static boolean filterServiceName(String keyword, ServicesItem item) {
    Map<String, String> annotations = item.getDeployment().getSpec().getTemplate().getMetadata().getAnnotations();
    if (annotations != null) {
        String serviceName = annotations.get("gaop/serviceLabel");   // ← 用户设的服务显示名
        if (serviceName == null) return false;
        return serviceName.contains(keyword);                        // ← 大小写敏感
    }
    return false;
}
```

**关键三点**（我上一版三条全错）：

| 我上一版（近似） | 厂商实际 |
| --- | --- |
| 匹配 `fixedName`（中文显示名表） | 匹配 **`gaop/serviceLabel`**（用户在界面上设的服务显示名注解） |
| 匹配 deployment 名（第二段又匹配了一次） | 第二段**只看注解**，不看名字 |
| 忽略大小写 | **大小写敏感** |

也就是说：`fixedName` 是"平台内置的显示名"（`GaopName` 表），
而 `gaop/serviceLabel` 是"**用户在界面上改过的显示名**"（就是 `PUT /v1/service/updateName` 改的那个注解）——
两者是完全不同的东西，**我上一版把它们混为一谈了**。

## 2. 两段式的完整语义（`ServiceController:1631-1644`）

```
keyword != null:
  第一段：deployment.metadata.name.contains(keyword)      // 大小写敏感
  若第一段结果为空 → 第二段：filterServiceName（只匹配 gaop/serviceLabel，大小写敏感）
```
即：**名字能命中就不看注解；名字一个都没命中，才去比对"用户改过的显示名"**。

## 3. 本轮改动

- `ServiceRuntimeEntity` 新增 `serviceLabel`；
- `ServiceRuntimePort` 从工作负载快照取 `spec.template.metadata.annotations["gaop/serviceLabel"]`；
- `ServiceQueryServiceImpl.filterServiceName` 换成**精确实现**（三行）；
- 新增 3 条用例：第一段大小写敏感、第二段只认注解、**没有注解的服务永不命中兜底**。

最后一条是本轮最要紧的断言——它正是我上一版会答错的那一题：
`gaop-be` 名字里含"框架"？不含；它的 `fixedName` 是"框架后端"（含"框架"）
⇒ 我上一版会把它匹配出来，而厂商**不会**（它没有 `gaop/serviceLabel` 注解）。

## 4. 感想（一句话）

这是本项目第 N 次出现同一个形状：**"我读代码得到的结论"与"代码实际做的事"之间，
差着一次逐行核对**。上一版我甚至给它写了 `@Deprecated` 式的注释说明"这是近似"，
但**近似值本身是错的**——标注了"我可能不对"不等于"我核对过了"。

## 5. 状态

全量 **34 类 228 条全绿**。本轮只读集群（无探针写入）；工作树干净。
