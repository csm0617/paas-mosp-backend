# sql/ —— 数据层资产（自研后端自身不带迁移）

## 为什么这里有厂商的 SQL

`paas-core` 是**替换 gaop-be** 的实现，两者共用同一个 `gaop` 库与同一份表结构：
自研仓里没有 Flyway/Liquibase 迁移，领域对象直接映射既有 39 张表（见
`paas-core/docs/findings/` 与 `docs/deviations.md`）。所以「空库起一套」所需的
建表语句只能来自厂商离线包的数据层资产。

本目录的内容是**厂商离线包的迁移脚本副本**，逐文件原样保留，只用于初始化数据库，
不参与编译，也不影响任何 Java 依赖方向。

**版本归属（重要，别搞混）**：本目录对应 **MSOP v1.3.4**（`gaop-be:V1.3.4-20260415-1857`）。
文件**内容**与证据根 `Project/msop/artifacts/k8s/sql/db/migration/` 逐字节一致；
文件**清单与执行顺序**取自 `msop/install-paas-init-sql.yml` 的 `sql_items`。
这两件事来源不同、且厂商两处内容并不同步——见下节「与 V1.3.5 安装包的差异」。

## 内容

| 路径 | 内容 | 来源 |
| --- | --- | --- |
| `migration/V*.sql` | 87 个 Flyway 迁移（V0.0.1 → V0.8.6），全量灌入后 39 张表 | **内容**取自 `gaop-be` V1.3.4 镜像内 `db/migration/`（= `Project/msop/artifacts/k8s/sql/db/migration/`）；**清单与顺序**取自 `install-paas-init-sql.yml` 的 `sql_items` |
| `order.txt` | **87 项执行顺序**（权威） | 厂商 `install-paas-init-sql.yml` 的 `sql_items`，逐字保留 |
| `seed.sql` | 最小可登录数据：`admin` 用户 + `运维管理员门户` + 关联 | 能力等价于 `artifacts/k8s/seed/msop-seed.sql`，补了 `SET NAMES`、幂等与自查输出 |

目录指纹（87 个文件按名排序后各自 SHA-256 再取 SHA-256）：

```
85461c69e84522f4b01f94a4a761b6d26fc74d7c23ec4fb7d5682d7257328a27
```

`build-package.sh` 打包时会重算这个指纹并写进 `MANIFEST.txt`，目标机可用它核对
SQL 在传输中是否被改动。

## 实测结论（2026-10-14，172.16.209.25）

> 前置事实：该次实测**没有**用厂商 Ansible 装库。MySQL 是 `default/mysql-deployment`
> （镜像 `mysql:8.0.32`，环境既有），`gaop-namespace/gaop-mysql` 只是指向它的
> `ExternalName`；schema 由 `Project/msop/artifacts/k8s/deploy-msop.sh schema` 灌入。
> 所以「87 成功 / 39 张表」验证的是**本目录这套 SQL 本身可用**，不是厂商
> `install-paas-init-sql.yml` 流程可用。

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

## 与 `msop/`（V1.3.5，VERSION 20260811）安装包的 6 处差异

`msop/roles/gaop-mysql/templates/*.sql.j2` 是 **Ansible 实际执行**的那一套。它**不含任何 Jinja
变量**（`grep '{{\|{%'` 命中 0），`template` 模块渲染 = 原样拷贝，所以可直接与本目录逐字节比对。
（另注：该 role 里灌库的两个 task 都带 `ignore_errors: true`，厂商自己跑也会静默吞错。）

87 个文件里 **81 个字节一致**，6 个不同，都是 v1.3.4→v1.3.5 的厂商演进，不是我们改的：

| 文件 | 本目录（v1.3.4，当前库） | `msop/`（V1.3.5） | 影响 |
| --- | --- | --- | --- |
| `V0.2.4__add-avatar-column-to-user.sql` | 头像 base64 长 90405B | 3053B | 内置默认头像图不同（数据） |
| `V0.4.5__alter-cicd.sql` | `image_storage` **varchar(100)** | varchar(255) | 镜像仓库组名长度上限 |
| `V0.6.3__add-data-backup.sql` | 「**席位**数据」 | 「战位数据」 | `g_backup.database_name` 文案 |
| `V0.7.4__alter-language-and-init.sql` | 索引 `text_key`**(500)** | (55) | 语言键索引前缀长度 |
| `V0.8.3__alter-language-table-character-set.sql` | `where **mode_name** = 'MAINTAIN'` | `where mode` | `g_mode` 两列都在，两版都能跑，**删的行不同** |
| `V0.8.3_update_setting_table.sql` | loki 上限 **60** | 300 | `g_setting` 默认告警阈值 |

**当前库属于哪一套，用这三处可查指纹判定**（2026-11-10 实测，全部落在 v1.3.4 一侧）：

```sql
select column_type from information_schema.columns
 where table_schema='gaop' and table_name='g_ci_cd' and column_name='image_storage';  -- varchar(100)
select val from g_setting where `key`='system.loki.log.max.memory';                    -- 60
select database_name from g_backup where id=2;                                          -- 席位数据
```

**含义**：气隙现场若改用 `msop/` 新包走厂商 Ansible 装库，得到的 schema 与本文档、与
`docs/deviations.md` 里验证过的基线**不是同一套**（列宽、索引前缀、默认值、部分数据都不同），
必须重跑一次「空库灌入 → 表数 → 契约比对」，不可直接复用现有结论。判定记录见
`docs/deviations.md` **D1/D2**。

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
注意 **v1.3.5 起不能再由上述证据根 rsync**——那个目录是 v1.3.4 的；要换到新包的
`roles/gaop-mysql/templates/*.sql.j2`（去掉 `.j2` 即可，无 Jinja 变量），并先按上节的
6 处差异逐条重判影响。
