# sql/ —— 数据层资产（自研后端自身不带迁移）

## 为什么这里有厂商的 SQL

`paas-core` 是**替换 gaop-be** 的实现，两者共用同一个 `gaop` 库与同一份表结构：
自研仓里没有 Flyway/Liquibase 迁移，领域对象直接映射既有 39 张表（见
`paas-core/docs/findings/` 与 `docs/deviations.md`）。所以「空库起一套」所需的
建表语句只能来自厂商离线包的数据层资产。

本目录的内容是**厂商离线包的迁移脚本副本**，逐文件原样保留，只用于初始化数据库，
不参与编译，也不影响任何 Java 依赖方向。

## 内容

| 路径 | 内容 | 来源 |
| --- | --- | --- |
| `migration/V*.sql` | 87 个 Flyway 迁移（V0.0.1 → V0.8.6），全量灌入后 39 张表 | `msop` 完整安装包 → `install-paas-init-sql.yml` 的 `sql_items` 列表 + 镜像内 `db/migration/` |
| `order.txt` | **87 项执行顺序**（权威） | 厂商 `install-paas-init-sql.yml` 的 `sql_items`，逐字保留 |
| `seed.sql` | 最小可登录数据：`admin` 用户 + `运维管理员门户` + 关联 | 能力等价于 `artifacts/k8s/seed/msop-seed.sql`，补了 `SET NAMES`、幂等与自查输出 |

目录指纹（87 个文件按名排序后各自 SHA-256 再取 SHA-256）：

```
85461c69e84522f4b01f94a4a761b6d26fc74d7c23ec4fb7d5682d7257328a27
```

`build-package.sh` 打包时会重算这个指纹并写进 `MANIFEST.txt`，目标机可用它核对
SQL 在传输中是否被改动。

## 实测结论（2026-10-14，172.16.209.25）

- 87 个迁移按 `sort -V` 顺序执行：**成功 87 / 失败 0**，最终 **39 张表**。
- 必须先建库 + 建 `mysql_native_password` 用户：厂商后端用的
  `mysql-connector-java 5.1.48` 不支持 MySQL 8 默认的 `caching_sha2_password`。
  自研后端用 `mysql-connector-j` 8.x，两种插件都能连；但为了让同一套库能同时服务
  两边（灰度期共存），部署包仍按 `mysql_native_password` 建用户。
- **顺序以 `order.txt` 为准**：它是厂商 `install-paas-init-sql.yml` 里 `sql_items` 的逐字拷贝。
  `sort -V` 只是没有 `order.txt` 时的兜底，**两者在 V0.8.3 上结果不同**：
  厂商是 `V0.8.3__alter-language-table-character-set.sql` 在前、
  `V0.8.3_update_setting_table.sql` 在后；`sort -V` 会反过来（`_u` 排在 `__a` 前）。
  两个文件操作不同表、互不依赖，所以两种顺序都能灌成功，但顺序是契约的一部分，不该由排序算法决定。
- 字典序一定不对：`V0.8.10` 必须排在 `V0.8.9` 之后。

## 什么时候不该灌

已有库（厂商已安装、或已跑过一遍）**不要重复灌**：`CREATE TABLE` 会失败。
部署脚本默认只做校验（表数 ≥ 39、`g_user` 有 admin），只有显式 `--with-schema`
才执行灌入。校验不通过时脚本会打印实际表数与缺失项，不会静默继续。

## 重新生成（厂商包升级时）

```bash
# 从证据根同步（只读源，不做修改）
rsync -a --delete \
  /Users/csm/Desktop/Project/msop/artifacts/k8s/sql/db/migration/ \
  paas-core/deploy/sql/migration/
# 重算指纹并更新本文件里的数字
cd paas-core/deploy/sql/migration && shasum -a 256 *.sql | shasum -a 256
```

升级后必须重跑一次「空库灌入 → 表数」实测，不要只看文件数变了就认为可用。
