# `gaop` 空列表的**真正**根因找到了：过滤链最后一道 `system/appName=gaop`

**证据等级**：〔代码事实〕（逐行）+〔运行实测〕（**对拉首次全绿**）。2026-10-14。

## 1. 根因：我上一轮读到倒数第二行就停了

过滤 lambda 的**最后两行**（`ServiceController:1593-1594`）是：

```java
boolean isGaopApp = (s = labels.get("system/appName")) != null && "gaop".equals(s);
return isGaopApp && result;          // ← 前一行我读到了，这一行没读到
```

也就是说：**工作负载必须带标签 `system/appName=gaop`**，否则无论 `type` 是否匹配**一律丢弃**。
而参考环境的 Deployment 只有 `app=<name>` 标签（实测），既没有 `system/appName` 也没有 `system/appType`
⇒ `isGaopApp=false` ⇒ **9 条全被丢掉** ⇒ 空列表。

**与 TenX 无关**（`findings/57` 已否定我上一轮那个"远端调用所以必空"的推论），
也**不是** `type` 名单的问题——`gaop-be` 本来就在框架名单里。

## 2. 修复后：**对拉首次全绿**

把这道合取项精确实现后：

```
type=1   vendor total=0   mine total=0
type=2   vendor total=0   mine total=0
```

**这是这个端点自实现以来第一次两边一致**。而它之所以有意义，是因为我现在能解释**为什么两边都是 0**——
不是"我们都没数据"，而是"两边都因为缺少 `system/appName=gaop` 标签而正确地过滤掉了全部"。

## 3. 回归

```
/v1/service   mine total=9   vendor total=9     ← 上一轮改过装配器，必须回归（通过）
```
（`serviceAuth` 我们还没实现，所以它没有对应值——那是待办，不是回归失败。）

## 4. 修复内容

- `ServiceRuntimeEntity` 新增 `appName`（取自标签 `system/appName`）；
- 端口里 `appTypeOf` 泛化成 `labelOf(snapshot, key)`，同时取 `system/appType` 与 `system/appName`；
- `GaopServiceFilter.matches(type, name, appType, appName)`：**两条分支（appType 存在与否）都要过 `isGaopApp`**
  ——厂商是在 `return isGaopApp && result` 里统一加的，不是只在某一支加；
- 新增用例 `requiresAppNameLabelToBeGaop`，其中一条断言 `matches(1, "gaop-be", "gaop-core", null) == false`
  （**有 appType 也照样要过 appName 这一关**）——那正是只读一半会写错的地方。

## 5. 这轮串起来的三件事（值得一起看）

1. `findings/55`：我把根因写成"依赖外部 TenX 云"。**错。**
2. `findings/57`：同源端点的 9 条数据否掉了它，我改成"未定论"。**对了一半。**
3. 本轮：**逐行读完那段过滤链**，找到真正的最后一道合取项。**对。**

三步的共同点：每一步都不是"想得更久"，而是**又得到了一条新证据**（独立观测 / 逐行读完）。
**"读了一半就下结论"和"抄错一个字符"是同一类错误**——都缺一次核对，只是错在更靠后的位置。

## 6. 状态

全量 **34 类 229 条全绿**；临时 NodePort 已删；无残留进程；工作树干净。
