# `GET /v1/software/auth`（软件授权列表）：**7 种情形对拉全部一致** —— 但先踩了自己两个坑

**证据等级**：〔运行实测〕（探针行 + 对拉）+〔代码事实〕。2026-10-14。
这是 K2 跨域欠账里的第一个（授权列表），也是**用"全分支探针"方法拿到非空对拉**的一次。

## 1. 探针与结果

造 4 行 `g_software` 覆盖三态：

| 探针 | `active_code` | `active_expire` | 期望 status | 实测两侧 |
| --- | --- | --- | --- | --- |
| A | null | null | **0**（且两个时间字段 null） | 一致 ✅ |
| B | `codeB` | 4102444800000（未来） | **1** | 一致 ✅ |
| C | `codeC` | 1000（过去） | **2** | 一致 ✅ |
| D | `codeD` | **0** | **2**（不是 0！见 §4） | 一致 ✅ |

对拉 7 种情形**全部完全相等**（整个 `data` 对象比较）：
无 `name`、`name=授权探针`、`name=probeauth`（仅命中 key）、`type=WEB`、`type=NOPE`、
`size=2` 分页、`page=2`。

## 2. 我自己踩的两个坑（都是"顺手复用了隔壁端点的查询"）

| # | 现象 | 根因 |
| --- | --- | --- |
| 1 | `name=probeauth` 我们 4 条、厂商 0 条 | 授权列表的 `likeName` **只打在 `name` 上**；我复用了列表端点的 `name OR key` |
| 2 | 修完 #1 后 `total` 仍不一致 | `countAuthList` 复用了列表端点的 `count()`（也是 `name OR key`）⇒ **total 与 list 来自两套 WHERE** |

两处都是同一个动作：**"隔壁端点已经有现成的查询，拿来用"**。而这两个端点的过滤条件
**看着像、实际不同**（`name OR key` vs `name`）。已各自补上专用查询（`selectAuthPage`/`selectAuthCount`）
并在注释里写明"必须与 list 用同一套 WHERE，否则 total 与 list 不一致"。

⇒ 与之前几轮同族：`edit()` vs `patch`、`fixedName` vs `gaop/serviceLabel`、"存在" vs "值为空"、
现在是"列表的过滤" vs "授权列表的过滤"。**复用相邻实现的代价，是在它们分叉的那一点上静默出错。**

## 3. 一处反直觉但已确认的行为

`active_expire = 0` 时 status 是 **2**（已过期），不是 0。因为厂商的第一条分支是
`activeExpire != null && now > activeExpire`，`0 != null && now > 0` 为真 ⇒ 直接判过期；
那句 `activeExpire == null || == 0 → 0` **在 now > 0 时不可达**。已用单测钉住。

## 4. 其它照抄点

- `name` 长度 > 36 → 400 `software.search.length.invalid`（文案与厂商逐字一致：
  「软件搜索长度不合法,搜索长度应在0~36个字符之内」）；
- 排序**只按 `created_at DESC`**（与列表端点的"`order_top` 恒在最前"**不同**）；
- `active_code` 非空时两个时间字段**原样取**（不因 expire 为 0 而置 null）。

## 5. 状态

服务/K2 端点总数 **+1**；测试 **40 类 240 条全绿**（上一轮 236）。
探针行已删（库内 0 残留）、临时 NodePort 已删、进程清零、工作树干净。
