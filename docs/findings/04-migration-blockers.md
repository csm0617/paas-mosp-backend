# 04 — JDK 21 + Spring Boot 3.3 + fabric8 7 迁移障碍（逐条定论）

> 工单：04-migration-blockers
> 证据输入（**只读**）：`recovered/gaop-be/pom.xml`、`recovered/gaop-be/BOOT-INF-lib-list.txt`（256 个运行期 jar）、`recovered/gaop-be/src/main/java/**`（1600 个 `.java`，CFR 0.152 反编译）、`recovered/gaop-be/src/main/resources/**`。
> 目标基线：**Spring Boot 3.3.5**（本仓 `pom.xml` 实际父版本）+ JDK 21 + fabric8 **7.9.0** + mybatis-spring-boot-starter **3.0.3** + springdoc **2.6.0**（骨架已 `mvn package` 绿，依赖树 `io.kubernetes` 命中 0，见 `docs/findings/01-deps.txt`）。
> 方法：一手来源优先——上游 release notes / CHANGELOG / 官方文档 / Maven Central `maven-metadata.xml` 与**制品本体**（`javap` 常量池、`unzip -l`、源码 jar）；**凡是能在本机执行的都执行了**（最小反序列化探针、依赖解析验证、根因复现）。无法定论的一律显式标 **未定论** 并写清缺什么证据。`recovered/` 未被修改。
> 取证环境：JDK 21.0.7 (arm64) / Maven 3.9.9 / 本地仓 `/Users/csm/DevTools/repository`。本机 `web_fetch` 对 `github.com` 报 "resolves to a non-public IP address"（DNS 走内网），故 GitHub 内容用 `curl` 读 `raw.githubusercontent.com` / `api.github.com`；`repo1.maven.org` 可直连。

**厂商侧基线（本机实测）**

| 指标 | 数值 |
|---|---|
| `src/main/java/**/*.java` | **1600** 个文件 / 206,099 行 |
| 引用 `io.kubernetes` 的文件 | **66** |
| 其中不同的 `io.kubernetes.*` 类型 | **128**（`openapi.models.*` **97** + `openapi.apis.*` **9** + `util/custom/common.*` **14**） |
| 厂商**手抄**的旧 k8s 模型 `com.glriverside.gaop.be.third.k8s/` | **608** 个文件（其中 `IoK8sApi*` 前缀 **596** 个） |
| `org.mybatis.dynamic.sql` | 108 个文件 |
| `spring-boot-maven-plugin` 配置 | `<includeSystemScope>true</includeSystemScope>` + `<extdirs>${project.basedir}/lib</extdirs>`（pom.xml L478/L490） |

---

## 0. 结论速览

| # | 结论 | 定性 |
|---|---|---|
| 1 | **pagehelper 没有 3.x 线**：`2.1.1`（Boot 3 / JDK 17+）直接跳到 `4.0.0`（Boot 4.0.2）。Boot 3 可用版本 = **2.1.1** | §1.3 |
| 2 | **mybatis-dynamic-sql 有 Boot3/MyBatis 3.5.16+ 可用版本**：**2.0.0**（Java 17 / MyBatis 3.5.19）；`1.5.2` 是 Java 8 线末版（MyBatis 3.5.16） | §1.2 |
| 3 | dynamic-sql 1.1.4 → 1.5.x/2.0.0 有一次**真源码级不兼容**：`where(col, cond, SqlCriterion<?>...)` → `where(col, cond, AndOrCriteriaGroup...)`。本仓反编译产物里有 **518 处 `new SqlCriterion[0]`** 落在 **69 个文件**，改法就是**把该实参删掉** | §1.2.1 |
| 4 | `javax.servlet(50)` / `javax.websocket(7)` 是**出现处数**，不是文件数：servlet 落在 **26 个文件**、websocket 落在 **1 个文件** | §2.0 |
| 5 | `javax.crypto`(2) / `javax.net.ssl`(2) / `javax.sql`(4) 共 **8 个文件完全不受影响**（JDK SE 自带，永远不叫 `jakarta.*`） | §2.6 |
| 6 | **fabric8 7.9.0 可以正常反序列化字符串型 `targetPort`/probe `port`——本机已跑通验证**（含 `"http"` 与 `8080` 双向保真） | §4.2 |
| 7 | 白屏根因**不是 client-java 18**（其字段也是 `IntOrString`），而是厂商自建 DTO `third/k8s/IoK8sApiCoreV1ServicePort.targetPort` 声明为 **`Integer`**，被 `TenxService.convertObj` 的 **GSON→Jackson** 往返命中；本机已**逐字复现**该报错 | §4.3 |
| 8 | 目标依赖集已用临时工程在 **Boot 3.3.5 / JDK 21** 上**解析通过**（`BUILD SUCCESS`，依赖树 `grep javax` 命中 **0**） | §1.5 |
| 9 | 离线私仓必须预置的「Central 拉不到的」只有两件：`org.csource:fastdfs-client-java`（Central 无此 GAV）、厂商现用的 `org.fife.ui:rsyntaxtextarea`（Central 无此 GAV，但换 `com.fifesoft` 即可解） | §5.1 |
| 10 | arm64：原厚包**无 JNI/native 落地**，无阻塞；唯二待复核的是 `zstd-jni` / `snappy-java` 的 aarch64 打包 | §5.2 |

---

## 1. 依赖逐行表（现版本 → 目标版本 → 是否必须改代码 → 一手证据）

### 1.1 工单点名的依赖

| 依赖 | 现版本 | 目标版本 / 替代 | 必须改代码？ | 一手证据 |
|---|---|---|---|---|
| `org.mybatis.spring.boot:mybatis-spring-boot-starter` | 2.1.3 | **3.0.5**（骨架现用 3.0.3 也已跑通；3.0.5 带 MyBatis 3.5.19） | **否**（`@Mapper`/`@MapperScan`/`mybatis.*` 配置键不变） | [3.0.5 pom](https://repo1.maven.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot/3.0.5/mybatis-spring-boot-3.0.5.pom)（`java.version=17`、`mybatis.version=3.5.19`、`spring-boot.version=3.5.0`）；[3.0.3 pom](https://repo1.maven.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot/3.0.3/mybatis-spring-boot-3.0.3.pom)（mybatis 3.5.14）；版本列表 [maven-metadata](https://repo1.maven.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot-starter/maven-metadata.xml) |
| `org.mybatis.dynamic-sql:mybatis-dynamic-sql` | 1.1.4 | **2.0.0**（Boot3/Java17 线）；若要留 Java 8 兼容则 **1.5.2** | **是**（518 处 `new SqlCriterion[0]`；空 `in` 列表语义变更） | [maven-metadata](https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/maven-metadata.xml)（`<release>2.0.0`）；[2.0.0 pom](https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/2.0.0/mybatis-dynamic-sql-2.0.0.pom)（Java 17 / mybatis 3.5.19 provided）；[CHANGELOG](https://github.com/mybatis/mybatis-dynamic-sql/blob/master/CHANGELOG.md) |
| `com.github.pagehelper:pagehelper-spring-boot-starter` | 1.2.13 | **2.1.1**（**Boot 3 线；没有 3.x**） | **部分**（PageHelper 5.1.11 → 6.1.1，需回归分页接口） | [maven-metadata](https://repo1.maven.org/maven2/com/github/pagehelper/pagehelper-spring-boot-starter/maven-metadata.xml)（`…2.1.1 → 4.0.0 → 4.1.1`）；[README](https://github.com/pagehelper/pagehelper-spring-boot/blob/master/README.md) 原文「当前版本支持 JDK17+ 和以下依赖版本：mybatis-spring-boot-starter 3.0.4、spring-boot-dependencies 3.5.0」 |
| `com.alibaba:druid-spring-boot-starter` | 1.2.15 | **`com.alibaba:druid-spring-boot-3-starter:1.2.28`**（artifactId 必须换） | **否**（换坐标即可；配置键 `spring.datasource.druid.*` 不变） | [boot3 版 pom](https://repo1.maven.org/maven2/com/alibaba/druid-spring-boot-3-starter/1.2.28/druid-spring-boot-3-starter-1.2.28.pom)（依赖 `jakarta.servlet:jakarta.servlet-api:6.0.0`；父 POM `springboot3.version=3.0.6`）；[boot2 版 pom](https://repo1.maven.org/maven2/com/alibaba/druid-spring-boot-starter/1.2.15/druid-spring-boot-starter-1.2.15.pom)（`spring-boot.version=1.5.12.RELEASE`）；[官方 README](https://github.com/alibaba/druid/blob/master/druid-spring-boot-3-starter/README.md)「要求：Spring Boot 3.0+，Java 17+」 |
| `mysql:mysql-connector-java` | 5.1.48 | **`com.mysql:mysql-connector-j:8.3.0`**（Boot 3.3.5 托管） | **是（仅配置层）**：`driver-class-name` 建议改 `com.mysql.cj.jdbc.Driver`。旧类名在 8.3.0 仍以**弃用 shim**存在（能启动、只告警） | [官方 Maven 坐标文档](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html)（`groupId: com.mysql` / `artifactId: mysql-connector-j`）；[8.0.31 release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-8-0-31.html)「the Maven groupId and artifactId for Connector/J have been changed … 旧坐标指向 relocation」；本机 `unzip -l mysql-connector-j-8.3.0.jar` 实测 `com/mysql/jdbc/Driver.class`(760B) 与 `com/mysql/cj/jdbc/Driver.class` **并存** |
| `com.github.tobato:fastdfs-client` | 1.27.2 | **无 Boot 3 后继版本**（1.27.2 即 Central 末版） | **未定论**（见 §6-4）：API 不含 javax，风险只在 `FdfsClientConfig` 这套 Boot-2 时代 autoconfigure 能否在 Boot 3 装配 | [maven-metadata](https://repo1.maven.org/maven2/com/github/tobato/fastdfs-client/maven-metadata.xml)；上游仓库是 `tobato/FastDFS_Client`（**最后提交 2024-05-17**）：<https://github.com/tobato/FastDFS_Client> |
| `org.csource:fastdfs-client-java` | 1.30-SNAPSHOT（`<scope>system</scope>` + `systemPath=${pom.basedir}/lib/fastdfs-client-java-1.30-SNAPSHOT.jar`，pom.xml L351–357） | **Central 上不存在该 GAV**；保 systemPath 或改自有坐标 / maintained fork | **否**（API 不变），但**构建方式必须改** | Central 元数据 **404**：<https://repo1.maven.org/maven2/org/csource/fastdfs-client-java/maven-metadata.xml> ；上游 tag 已到 V1.40 但从未以此 GAV 发布：<https://github.com/happyfish100/fastdfs-client-java/tags> |
| `org.apache.sshd:sshd-core` | 2.12.0 | **2.20.0**（3.0.0 仅到 M6，无 GA，不建议） | **否** | [maven-metadata](https://repo1.maven.org/maven2/org/apache/sshd/sshd-core/maven-metadata.xml)；[2.14.0 changes](https://github.com/apache/mina-sshd/blob/master/docs/changes/2.14.0.md)「JDK 17 at build time, while the target runtime still remains unchanged to support JDK 8」；本机实测 2.20.0 含厂商用到的全部类（`SshClient`/`ClientSession`/`ChannelExec`/`ClientChannelEvent`/`ConnectFuture`/`CancelOption`） |
| `cn.hutool:hutool-all` | 5.7.2 | **5.8.48** | **否**。厂商**完全未使用** servlet 模块（全仓 `ServletUtil`/`JakartaServletUtil` 命中 **0**） | [maven-metadata](https://repo1.maven.org/maven2/cn/hutool/hutool-all/maven-metadata.xml)；本机字节码比对：5.7.2 只有 `cn/hutool/extra/servlet/ServletUtil.class`（常量池 `javax.servlet.*`）；5.8.48 **同时**含 `ServletUtil`(javax) 与 `JakartaServletUtil`(jakarta) |
| `org.freemarker:freemarker` | 2.3.31 | **2.3.33**（Boot **3.3.5** 托管；3.3.13 抬到 2.3.34） | **否**。厂商只用核心模板 API（`Configuration`/`Template`/`TemplateException`/`TemplateExceptionHandler`，2 个文件），**不用** `freemarker.ext.servlet` | 本机读 `spring-boot-dependencies-3.3.5.pom` 得 `freemarker.version=2.3.33`（3.3.13 为 2.3.34）；[2.3.33 release notes](https://freemarker.apache.org/docs/versions_2_3_33.html)「minimum required Java version was increased from Java 7 to Java 8 … Servlet 3.0, and JSP 2.2」 |
| `com.google.code.gson:gson` | 2.8.8 | **2.10.1**（Boot 托管；Central 最新 2.14.0） | **否**（纯 Java，公开 API 向后兼容） | 本机 `spring-boot-dependencies-3.3.5.pom`：`gson.version=2.10.1`（3.3.13 同值）；[gson releases](https://github.com/google/gson/releases) |
| `com.fatboyindustrial.gson-javatime-serialisers` | 1.1.2 | **1.1.2**（**即末版，无更新**） | **否**（唯一用法 `JsonConfig` 的 `Converters.registerOffsetDateTime`） | [maven-metadata](https://repo1.maven.org/maven2/com/fatboyindustrial/gson-javatime-serialisers/gson-javatime-serialisers/maven-metadata.xml)（`<release>1.1.2`）；注：gson **2.14+** 内置 java.time 适配器会与之重叠（[2.14.0 release notes](https://github.com/google/gson/releases)），故建议钉 Boot 托管的 2.10.1 |
| `org.springdoc:springdoc-openapi-ui` | 1.5.2 | **`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`**（已定） | **是**：artifactId 改名 + **全仓 21 个文件用 Swagger 1.x 注解** `io.swagger.annotations.{ApiModelProperty,ApiModel,ApiOperation}`（25 处），springdoc 2.x 只认 `io.swagger.v3.oas.annotations.*` | 本仓 [01-deps.txt](01-deps.txt) 已解析 `springdoc-openapi-starter-webmvc-ui:2.6.0` + `swagger-*-jakarta:2.2.22`；[springdoc 官方站](https://springdoc.org/) |
| `org.bouncycastle:bcprov-jdk15on` | 1.65 | **`org.bouncycastle:bcprov-jdk18on:1.86`**（`jdk15on` 线终止于 1.70） | **代码不用改**（`org.bouncycastle.jce.provider.BouncyCastleProvider` 全限定名不变，全仓仅此 1 处 import），**坐标必须换** | [BC 官方下载页](https://www.bouncycastle.org/download/bouncy-castle-java/)分栏原文：「**JDK 1.8 and later** … `bcprov-jdk18on-1.86.jar`」/「JDK 1.5 - JDK 1.8」；[jdk15on metadata](https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/maven-metadata.xml)（末版 1.70）；[jdk18on metadata](https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml)（1.86） |
| `org.fife.ui:rsyntaxtextarea` | 2.0.4.1 | **`com.fifesoft:rsyntaxtextarea:4.0.1`** | **是（坐标必须改）**：`org.fife.ui` 在 Central **不存在**。厂商唯一用法是 `service/DatabaseService.java:120` 的 `org.fife.io.UnicodeReader` → 换坐标后**零代码改动** | [`org.fife.ui` 元数据 **404**](https://repo1.maven.org/maven2/org/fife/ui/rsyntaxtextarea/maven-metadata.xml)；[`com.fifesoft` metadata](https://repo1.maven.org/maven2/com/fifesoft/rsyntaxtextarea/maven-metadata.xml)（**含 2.0.4.1**，最新 4.0.1）；本机实测 4.0.1：包名仍是 `org.fife.ui.rsyntaxtextarea`、`Automatic-Module-Name: org.fife.RSyntaxTextArea`、`org/fife/io/UnicodeReader.class` 在、class 文件 major version **55（Java 11）** |

### 1.2 `mybatis-dynamic-sql` 深挖（Boot3/MyBatis 3.5.16+ 可用版本 = 有）

**版本线与 Java/MyBatis 要求**

| 坐标 `org.mybatis.dynamic-sql:mybatis-dynamic-sql:` | Java | 编译期 MyBatis（provided） | 定位 |
|---|---|---|---|
| **2.0.0** | **17** | **3.5.19** | **Boot 3 线推荐** |
| 1.5.2 | 8 | 3.5.16 | 「最后一个兼容 Java 8 的版本」（CHANGELOG 原文） |
| 1.5.1 / 1.5.0 | 8 | — | 1.5.0 引入 where/having 重构 |
| 1.1.4（厂商） | 8 | parent `mybatis-parent:31`、spring-jdbc 5.2.1 | — |

证据：[metadata](https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/maven-metadata.xml)（`<release>2.0.0`）· [2.0.0 pom](https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/2.0.0/mybatis-dynamic-sql-2.0.0.pom)（`<java.version>17</java.version>`，`org.mybatis:mybatis:3.5.19` scope `provided`，另加 runtime 依赖 `org.jspecify:jspecify:1.0.0`）· [CHANGELOG](https://github.com/mybatis/mybatis-dynamic-sql/blob/master/CHANGELOG.md)。

**2.0.0 的破坏性变更（官方 CHANGELOG 原文摘要）**

- 「The library now requires Java 17」「Deprecated code from prior releases is removed」。
- 有 where 子句的 DSL（`CountDSL`/`DeleteDSL`/`SelectDSL`/`UpdateDSL`）新增**非泛型**版本放在**新包** `org.mybatis.dynamic.sql.dsl.*`，**旧 DSL 与原包同时保留** → 原文 *"The new DSLs will live side by side with the old DSLs for now to maintain backwards compatibility."* 因此厂商现在用的 `select.SelectDSLCompleter` / `update.UpdateDSL` / `delete.DeleteDSLCompleter` **仍然编译得过**。
- 已知「潜在破坏点」：① 空列表的 `isIn`/`isNotIn`/`isInCaseInsensitive`/`isNotInCaseInsensitive` 现在会**渲染并抛 `InvalidSqlException`**（原来渲染成非法 SQL、报错在 DB 侧）；② 自定义 `SortSpecification` 实现必须改（`isDescending`/`orderByName` → `renderForOrderBy`）；③ 自定义函数（基类由 `BindableColumn` 改持 `BasicColumn`）与自定义 `VisitableCondition` 需调整；④ 移除「可独立渲染的 where 子句」。

#### 1.2.1 一次**真源码级不兼容**：`where(..., SqlCriterion...)` → `where(..., AndOrCriteriaGroup...)`

我下载了同坐标的 `-sources.jar` 做对比：

| 版本 | 签名 | 出处（sources jar 内路径） |
|---|---|---|
| 1.1.4 | `public <S> T where(BindableColumn<S> column, VisitableCondition<S> condition, SqlCriterion<?>... subCriteria)` | `org/mybatis/dynamic/sql/where/AbstractWhereDSL.java:38` |
| 1.5.2 | `public <T> F where(BindableColumn<T> column, VisitableCondition<T> condition, AndOrCriteriaGroup... subCriteria)` | `org/mybatis/dynamic/sql/where/AbstractWhereStarter.java:41` |

且 `SqlCriterion` 本身从 `public class SqlCriterion<T>`（1.1.4，`SqlCriterion.java:25`）变成 `public abstract class SqlCriterion`（1.5.2，`SqlCriterion.java:22`），而 **`AndOrCriteriaGroup` 是独立类、不继承 `SqlCriterion`**（1.5.2 `AndOrCriteriaGroup.java:33` 为 `public class AndOrCriteriaGroup`）。→ 传 `SqlCriterion[]` 到 `AndOrCriteriaGroup...` 形参上**编译不过**。

证据 jar：<https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/1.1.4/mybatis-dynamic-sql-1.1.4-sources.jar> · <https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/1.5.2/mybatis-dynamic-sql-1.5.2-sources.jar>

**本仓实测影响面**：

```
grep -rho 'new SqlCriterion\[0\]' --include=*.java .  → 518
grep -rl 'import org.mybatis.dynamic.sql.SqlCriterion;' --include=*.java .  → 69 个文件
grep -rhoE '\.isIn\(|isInWhenPresent\(' --include=*.java .  → 36
```

**改法（对 1.1.4 与 1.5.x/2.0.0 都成立）：把 `, new SqlCriterion[0]` 这个实参整个删掉。** 例（本机核过行号）：

```java
// service/impl/SubRoleServiceImpl.java:577-579
this.userSubRoleMapper.delete(c -> c.where(..., SqlBuilder.isIn(subroleIdList), new SqlCriterion[0]));
//                                                                   ↑ 删掉这一段即可
// service/SoftwareService.java:2132 / 2301、service/AlertService.java:93 / 135 / 142、service/UserService.java:763 …
```

> ⚠️ **反编译歧义（必须记下）**：CFR 会把「源码里省略可变参数」的调用也物化成 `new SqlCriterion[0]`。因此**不能断定**这 518 处都是原源码显式写的。但两种情形下**处理动作相同**——删掉该实参既能编过 1.1.4 也能编过 1.5.x/2.0.0（1.5.2 还有一个 `where(SqlCriterion, AndOrCriteriaGroup...)` 重载，删掉尾参后第一个 `where(col, cond)` 走无 vararg 重载，仍然合法）。**未定论项**：这 518 处里有多少是原始源码实写（见 §6-3）。

**空 `in` 列表（1.5.2 起的行为陷阱）**

官方 CHANGELOG「Release 1.5.2 - June 3, 2024」原文：*"**Potentially Breaking Change**: the 'in' conditions ('isIn', 'isNotIn', 'isInCaseInsensitive', 'isNotInCaseInsensitive') will now render if the input list of values is empty. This will lead to a runtime exception."*
本仓确有 `SqlBuilder.isIn(serviceList)`（`SoftwareService.java:2132`）、`SqlBuilder.isIn(services)`（`…:2301`）这类调用 → **空集合会从「静默忽略」变成「运行期异常」**。改法：用 `isInWhenPresent(...)`（1.5.2 已提供，见 sources jar `SqlBuilder.java`）或提前判空。
→ 保守路线可**锁 1.5.1**；走 2.0.0 则把上面两件事一起做。

### 1.3 `pagehelper` 深挖（**没有 3.x**；Boot 3 用 2.1.1）

**完整版本线（Maven Central 元数据）**：
`0.1.0 … 1.2.13 → 1.3.0/1.3.1 → 1.4.0 … 1.4.7 → 2.0.0 → 2.1.0 → 2.1.1 → 4.0.0 → 4.1.0 → 4.1.1`

各线对 Boot 的定位（读各自父 POM）：

| 版本 | 父 POM `spring-boot.version` | `mybatis-spring-boot.version` | `pagehelper.version` | 结论 |
|---|---|---|---|---|
| **1.4.6** | **2.7.3** | 2.2.2 | 5.3.2 | **Boot 2**，**不是** Boot 3 版本 |
| 1.4.7 | 2.7.12 | 2.3.1 | — | Boot 2 |
| **2.1.1** | 2.7.18（**仅构建用**） | 2.3.2（**仅构建用**） | 6.1.1 | **Boot 3（README 明示支持 Boot 3.5.0 + mybatis-spring-boot-starter 3.0.4）** |
| 4.0.0 | **4.0.2** | **4.0.1** | 6.1.1 | Boot 4（`maven.compiler.release=21`） |
| 4.1.1 | **4.1.0** | 4.0.1 | — | Boot 4 |

> **纠正**：不要把 `1.4.6` 当作 Boot 3 可用版本——它的父 POM 是 `spring-boot 2.7.3` / `mybatis-spring-boot 2.2.2`（[1.4.6 pom](https://repo1.maven.org/maven2/com/github/pagehelper/pagehelper-spring-boot/1.4.6/pagehelper-spring-boot-1.4.6.pom)）。

**为什么 2.1.1 是 Boot 3 可用版本（三条硬证据）**

1. [README](https://github.com/pagehelper/pagehelper-spring-boot/blob/master/README.md) `v2.1.1 - 2025-06-20` 段原文：「**当前版本支持 JDK17+ 和以下依赖版本：** mybatis-spring-boot-starter **3.0.4**、spring-boot-dependencies **3.5.0**」。
2. 制品内部带 Boot 3 的自动装配入口：`pagehelper-spring-boot-autoconfigure-2.1.1.jar` 里同时有 `META-INF/spring.factories` 与 **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**（内容 `com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration`）——本机 `unzip` 实测。
3. 该 jar 的 3 个 class 用 `javap -v` 扫常量池：**没有任何 `javax.*` / `jakarta.*` 引用**。

**传递依赖是干净的（不会拽进 Boot 2 的 mybatis 栈）**

- `pagehelper-spring-boot-autoconfigure:2.1.1` 对 `org.mybatis.spring.boot:mybatis-spring-boot-autoconfigure` 标了 **`<optional>true</optional>`**（不传递）。
- `pagehelper-spring-boot-starter:2.1.1` 里 `mybatis-spring-boot-starter` **无版本号**（走父 POM 的 dependencyManagement）→ **照抄厂商的 `<exclusion>` 排除即可**。
- `pagehelper:6.1.1` 的 pom 里 `mybatis:3.5.19`、`guava`、`druid`、`HikariCP-java6`、`tomcat-jdbc`、`c3p0`、`commons-dbcp2` **全部 `<optional>true</optional>`**；唯一实打实的 compile 依赖是 `com.github.jsqlparser:jsqlparser:4.7`。

```xml
<dependency>
  <groupId>com.github.pagehelper</groupId>
  <artifactId>pagehelper-spring-boot-starter</artifactId>
  <version>2.1.1</version>
  <exclusions>
    <exclusion>
      <groupId>org.mybatis.spring.boot</groupId>
      <artifactId>mybatis-spring-boot-starter</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

**代码影响**：厂商只用 `PageHelper`(19 文件) / `Page`(17) / `PageInfo`(2)，三者包名类名在 5.1.11 → 6.1.1 之间不变。**未定论**：`Page`/`PageInfo` 的方法级行为差异需要跑分页回归用例（我没有在 5.x→6.x 的 release notes 里核到破坏性签名变化）。

### 1.4 额外高风险依赖（工单未点名，但不处理会编译/启动失败）

| 依赖 | 现版本 | 目标 | 结论 | 一手证据 |
|---|---|---|---|---|
| `org.projectlombok:lombok` | 1.18.12（`BOOT-INF-lib-list.txt:129`） | **1.18.48**（Central release） | **必须升**：Lombok < 1.18.20 在 JDK 16+ 上因 `IllegalAccessError`/`NoSuchFieldError` 无法工作；JDK 21 需 1.18.30+ | [maven-metadata](https://repo1.maven.org/maven2/org/projectlombok/lombok/maven-metadata.xml)（`<release>1.18.48`） |
| `org.yaml:snakeyaml`（pom 显式钉 1.30） | 1.30 | **2.2**（Boot 3.3.5 托管） | **必须改**：显式钉 1.30 会与 Boot 的 2.2 冲突；snakeyaml 2.0 移除部分 API | 本机 `spring-boot-dependencies-3.3.5.pom`：`snakeyaml.version=2.2`（3.3.13 同） |
| `com.github.mkopylec:charon-spring-webmvc` | 4.6.0 | **5.x**（5.4.0 依赖 `spring-web:6.2.0`） | **必须换**：4.x 面向 Spring 5；`config/ReverseProxyConfig.java` 用其 9 个类 | [maven-metadata](https://repo1.maven.org/maven2/com/github/mkopylec/charon-spring-webmvc/maven-metadata.xml)；[5.4.0 pom](https://repo1.maven.org/maven2/com/github/mkopylec/charon-spring-webmvc/5.4.0/charon-spring-webmvc-5.4.0.pom) |
| `io.lettuce:lettuce-core`（pom 显式钉 6.0.1.RELEASE） | 6.0.1 | Boot 3.3.5 托管 **6.3.2.RELEASE** | 建议**去掉显式版本** | 本仓 [01-deps.txt](01-deps.txt)（`lettuce-core:6.3.2.RELEASE`） |
| `javax.validation:validation-api`（pom 显式声明、无版本） | Boot 2.3.1 托管 2.0.1.Final（`BOOT-INF-lib-list.txt:241`） | **删除该依赖**；由 `jakarta.validation:jakarta.validation-api:3.0.2` 承担（Boot 3.3.5 托管） | **必须删/换坐标** | 本机 `spring-boot-dependencies-3.3.5.pom`：`jakarta-validation.version=3.0.2`；[Boot 2.3.1 POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.3.1.RELEASE/spring-boot-dependencies-2.3.1.RELEASE.pom) |
| `org.apache.tika:tika-parsers` / `poi` / `commons-fileupload` | 1.24.1 / 4.1.2 / 1.4 | 随 Boot 3.3 重选 | 非阻塞，但 Tika 1.x → 2.x/3.x 是包名级大改，需单独评估 | — |
| `hibernate-validator` | 6.1.5.Final | Boot 3.3.5 托管 **8.0.1.Final**（3.3.13 为 8.0.2.Final） | 随 `spring-boot-starter-validation` 自动升 | 本机 `spring-boot-dependencies-3.3.5.pom` |
| `javax.servlet`/`websocket`/`annotation` 的**编译期** API | Tomcat 9.0.36 / `javax.annotation-api 1.3.2` | Tomcat **10.1.31**（3.3.13 为 10.1.42） / `jakarta.annotation-api 2.1.1` | 见 §2 | 本机 `spring-boot-dependencies-3.3.5.pom`：`tomcat.version=10.1.31` |

### 1.5 目标依赖集**可执行验证**（本机实跑，未改动本仓）

在 `/tmp/verifymig/` 建临时 Maven 工程（父 `spring-boot-starter-parent:3.3.5`、`java.version=21`），声明全部目标坐标：

```xml
mybatis-spring-boot-starter 3.0.5 · mybatis-dynamic-sql 2.0.0
pagehelper-spring-boot-starter 2.1.1（exclusion: mybatis-spring-boot-starter）
druid-spring-boot-3-starter 1.2.28 · com.mysql:mysql-connector-j（Boot 托管）
freemarker（托管） · gson（托管） · bcprov-jdk18on 1.86
com.fifesoft:rsyntaxtextarea 4.0.1 · hutool-all 5.8.48 · sshd-core 2.20.0
```

```
$ mvn -B dependency:list -DoutputFile=/tmp/verifymig/deps.txt -DincludeScope=runtime
[INFO] BUILD SUCCESS
```

解析结果摘录（`deps.txt` 原文）：

```
com.github.pagehelper:pagehelper-spring-boot-starter:jar:2.1.1
com.github.pagehelper:pagehelper-spring-boot-autoconfigure:jar:2.1.1
com.github.pagehelper:pagehelper:jar:6.1.1
com.github.jsqlparser:jsqlparser:jar:4.7
org.mybatis.dynamic-sql:mybatis-dynamic-sql:jar:2.0.0
org.mybatis.spring.boot:mybatis-spring-boot-starter:jar:3.0.5
org.mybatis:mybatis:jar:3.5.19
com.alibaba:druid-spring-boot-3-starter:jar:1.2.28      com.alibaba:druid:jar:1.2.28
com.mysql:mysql-connector-j:jar:8.3.0(runtime)
org.freemarker:freemarker:jar:2.3.33     com.google.code.gson:gson:jar:2.10.1
org.bouncycastle:bcprov-jdk18on:jar:1.86
com.fifesoft:rsyntaxtextarea:jar:4.0.1   cn.hutool:hutool-all:jar:5.8.48
org.apache.sshd:sshd-core:jar:2.20.0     org.apache.sshd:sshd-common:jar:2.20.0
jakarta.annotation:jakarta.annotation-api:jar:2.1.1
jakarta.validation:jakarta.validation-api:jar:3.0.2
```

```
$ grep javax /tmp/verifymig/deps.txt || echo "(依赖树无 javax.*)"
(依赖树无 javax.*)
```

这同时证明了**pagehelper 2.1.1 的 exclusion 写法有效**（依赖树里只有 `mybatis-spring-boot-starter:3.0.5`，没有被拽进 2.x）。
**未定论**：这是**依赖解析**通过，不等于**运行时**通过——README 的配对版本是 Boot **3.5.0**，本仓是 3.3.5，需要在本仓骨架上加依赖后 `mvn package` + 启一次上下文才算闭环（属后续工单验收项）。

---

## 2. jakarta 化清单（逐文件 + 替代类）

### 2.0 计数口径纠正（重要）

工单里的 `javax.servlet(50)` / `javax.websocket(7)` 是 **出现处数**，不是文件数。按**文件**口径实测：

| 包 | 出现处数 | 文件数 | 是否受 Jakarta 影响 |
|---|---|---|---|
| `javax.servlet.*` | 50 | **26** | 是 |
| `javax.annotation.Resource` | 27 | **27** | 是 |
| `javax.websocket.*` | 7 | **1** | 是 |
| `javax.validation.*` | 5 | **5** | 是 |
| `javax.crypto.*` | 7 | 2 | **否**（JDK 自带） |
| `javax.net.ssl.*` | 8 | 2 | **否**（JDK 自带） |
| `javax.sql.DataSource` | 4 | 4 | **否**（JDK 自带） |

复核命令：`grep -rlE 'javax\.<pkg>\.' recovered/gaop-be/src/main/java --include='*.java' | wc -l`（另用 `grep -rhoE 'javax\.<pkg>\.[A-Za-z]' | wc -l` 得出现处数）。
**受影响合计：59 个文件 / 89 处**（无跨包重叠：websocket 那 1 个文件不在其他三组里）。

**语义依据（一手）**：Spring 官方博客 *Preparing for Spring Boot 3.0* —— <https://spring.io/blog/2022/05/24/preparing-for-spring-boot-3-0> 原文：

> *"Spring Boot 3.0 … will also be the first version of Spring Boot that makes use of Jakarta EE 9 APIs (`jakarta.*`) instead of EE 8 (`javax.*`). … Jakarta EE 9 a new top-level `jakarta` package, replacing EE 8's `javax` top-level package. For example, the Servlet specification in Jakarta EE 8 uses a `javax.servlet` package but this has changed to `jakarta.servlet` in EE 9. … Generally speaking, it's not possible to mix Java EE and Jakarta EE APIs in the same project."*

→ **不能混用**，所以 servlet / annotation / websocket / validation 四组必须**同批改完**。

### 2.1 需要 `javax.*` → `jakarta.*`（仅换包名前缀，简单类名不变）

| javax 类 | 出现处数 | 替代类 | 由谁提供（Boot 3.3.5） |
|---|---|---|---|
| `javax.servlet.http.HttpServletResponse` | 19 | `jakarta.servlet.http.HttpServletResponse` | `tomcat-embed-core 10.1.31` |
| `javax.servlet.ServletOutputStream` | 15 | `jakarta.servlet.ServletOutputStream` | 同上 |
| `javax.servlet.http.HttpServletRequest` | 13 | `jakarta.servlet.http.HttpServletRequest` | 同上 |
| `javax.servlet.http.Cookie` | 3 | `jakarta.servlet.http.Cookie` | 同上 |
| `javax.annotation.Resource` | 27 | `jakarta.annotation.Resource` | `jakarta.annotation-api 2.1.1`（`spring-boot-starter` 带入） |
| `javax.websocket.{OnOpen,OnMessage,OnError,OnClose,Session}` | 各 1 | `jakarta.websocket.*` | `tomcat-embed-websocket 10.1.31`（`spring-boot-starter-websocket` 带入） |
| `javax.websocket.server.{ServerEndpoint,PathParam}` | 各 1 | `jakarta.websocket.server.*` | 同上 |
| `javax.validation.constraints.NotNull` | 3 | `jakarta.validation.constraints.NotNull` | `jakarta.validation-api 3.0.2` + `hibernate-validator 8.0.1.Final` |
| `javax.validation.constraints.NotEmpty` | 1 | `jakarta.validation.constraints.NotEmpty` | 同上 |
| `javax.validation.constraints.NotBlank` | 1 | `jakarta.validation.constraints.NotBlank` | 同上 |

### 2.2 `javax.servlet.*` — 26 个文件的完整清单

（前缀统一为 `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/`）

```
aspect/AuditAnnotationAspect.java          aspect/RequestLoggingAspect.java
controller/BackController.java             controller/ClientController.java
controller/ConfigMapController.java        controller/CsSoftwareController.java
controller/DataBaseController.java         controller/ImageController.java
controller/K8sProxyController.java         controller/RoleController.java
controller/SoftwareController.java         controller/SubRoleController.java
controller/SyncController.java             controller/UserAuditController.java
controller/UserController.java             controller/pub/PublicController.java
controller/pub/PublicFdfsController.java
service/BackService.java                   service/ClientService.java
service/FileService.java                   service/RoleService.java
service/SoftwareService.java               service/UserAuditService.java
service/UserService.java
utils/I18nUtil.java                        utils/ResponseUtils.java
```

**同一批文件里还有 Spring 6 的移除项，必须一并改**：
`config/CustomJacksonConfig.java:15,19` 继承 `org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter` —— 该类在 Spring Framework 6 中**已被删除**，必须改为 `implements WebMvcConfigurer`（本机 grep 确认全仓仅此一处）。

### 2.3 `javax.annotation.Resource` — 27 个文件的完整清单（与 27 处 1:1，无遗漏）

```
controller/ConfigMapController.java        controller/DashboardsController.java
controller/ImageController.java            controller/K8sProxyController.java
controller/LanguageController.java         controller/ModeController.java
controller/ServiceController.java          controller/SubRoleController.java
controller/SyncController.java             controller/pub/PubVipController.java
controller/pub/PublicController.java
service/BackService.java                   service/ComputerService.java
service/DashboardsService.java             service/DockerService.java
service/FileService.java                   service/ImageService.java
service/RoleService.java                   service/SoftwareService.java
service/TenxService.java                   service/UserService.java
service/impl/K3sImageServiceImpl.java      service/impl/K8sImageServiceImpl.java
service/impl/LanguageServiceImpl.java      service/impl/ModeServiceImpl.java
service/impl/NodeVersionServiceImpl.java
utils/FastDFSClientUtil.java
```

### 2.4 `javax.websocket.*` — 唯一的 1 个文件

`config/websocket/WebSocket.java`（7 个 import，见 §2.1）。Tomcat 10 的 websocket 实现随之换成 `tomcat-embed-websocket-10.1.x`，`jakarta.websocket` 由 `spring-boot-starter-websocket` 传递提供，**无需额外坐标**。

### 2.5 `javax.validation.*` — 5 个文件

| 文件 | 注解 |
|---|---|
| `controller/form/LoginForm.java` | `@NotEmpty` |
| `controller/form/software/RelateServices.java` | `@NotNull` |
| `controller/request/NeedBuckUpTableForm.java` | `@NotBlank` |
| `service/TenxService.java` | `@NotNull` |
| `service/K8sService.java` | `@NotNull` |

### 2.6 **不受影响**的 `javax.*`（属 JDK，不要错改）

| 包 | 文件 | 说明 |
|---|---|---|
| `javax.sql.DataSource` | `config/multidb/MultiDataSource.java`、`config/multidb/MybatisConfig.java`、`service/DatabaseService.java`、`utils/RunSqlScript.java` | `javax.sql` 是 JDK 标准库（`java.sql` 模块），**永远是 `javax.sql`** |
| `javax.crypto.*`（`Cipher`、`SecretKeySpec`…） | `config/AesConfig.java`、`config/RsaConfig.java` | JDK 自带（JCE），保持原样 |
| `javax.net.ssl.*`（`SSLContext`、`X509TrustManager`…） | `config/FeignConfig.java`、`config/ReverseProxyConfig.java` | JDK 自带，保持原样 |

> 判据：`javax.*` 里**只有** Jakarta EE 规范（Servlet / Annotation / Validation / WebSocket / JMS / JAX-RS / Persistence / Transaction / Mail）从 EE 9 起改名 `jakarta.*`；`javax.crypto`/`javax.net`/`javax.sql`/`javax.naming`/`javax.management` 属 JDK，不变。

### 2.7 第三方库的 jakarta 状态（迁移时一并核对）

| 库 | 现版本 | Boot 3 下的状态 | 证据 |
|---|---|---|---|
| hutool-all | 5.7.2 | 5.8.48 起提供 **`JakartaServletUtil`**（javax 的 `ServletUtil` 仍在）；厂商**未使用** → 无影响 | 本机 `javap -v` 常量池比对 5.7.2 / 5.8.48 |
| freemarker | 2.3.31 | 2.3.33 的 Servlet/JSP 支持要求 Servlet 3.0+；厂商只用核心 API，**不碰 Servlet** | [2.3.33 notes](https://freemarker.apache.org/docs/versions_2_3_33.html) |
| pagehelper starter | 1.2.13 | 2.1.1 的 autoconfigure 常量池**无任何 javax/jakarta 引用**；带 Boot 3 的 `AutoConfiguration.imports` | 本机 `javap -v` + `unzip -p` |
| druid starter | 1.2.15 | **必须换** `druid-spring-boot-3-starter`（依赖 `jakarta.servlet-api:6.0.0`） | [boot3 pom](https://repo1.maven.org/maven2/com/alibaba/druid-spring-boot-3-starter/1.2.28/druid-spring-boot-3-starter-1.2.28.pom) |
| springdoc | 1.5.2 | 已由骨架换成 `springdoc-openapi-starter-webmvc-ui:2.6.0`（swagger `*-jakarta`） | 本仓 [01-deps.txt](01-deps.txt) |
| fastdfs（两条线）/ sshd-core / rsyntaxtextarea | — | 均不依赖 Servlet/Spring → **无 jakarta 问题** | 厂商导入清单 + 上述 Central 事实 |

---

## 3. K8s 客户端换装：`io.kubernetes:client-java:18.0.1` → fabric8 7.9.0

- 目标坐标：`io.fabric8:kubernetes-client:7.9.0`（Central `<release>` 已是 8.0.0；7.9.0 发布于 2026-09-04，是本工单选定的 7.x 末班车）。
  <https://repo1.maven.org/maven2/io/fabric8/kubernetes-client/maven-metadata.xml> · <https://github.com/fabric8io/kubernetes-client/releases>
- fabric8 7 的 Java 基线 = **Java 11+**：官方 `doc/MIGRATION-v7.md`（<https://github.com/fabric8io/kubernetes-client/blob/main/doc/MIGRATION-v7.md>）「Java baseline set to Java 11」。
- 官方 DSL 速查表：<https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md>

**本机对 7.9.0 制品做的可用性实测**（`Class.forName` + `javap`，classpath 取自本仓 `paas-core-infrastructure`）：

- ✅ `KubernetesClient`（接口）/ `KubernetesClientBuilder` / `Config` / `ConfigBuilder` / `KubernetesClientException` / `KubernetesClientTimeoutException`
- ✅ DSL：`PodResource`、`MixedOperation`、`NonNamespaceOperation`、`Resource`、`NamespaceableResource`、`LogWatch`、`ExecWatch`、`ExecListener`、`ContainerResource`、`Loggable`、`RollableScalableResource`、`ServiceResource`、`PatchContext`、`PatchType`、`ResourceDefinitionContext`
- ✅ 模型：`ServicePort`、`TCPSocketAction`、`IntOrString`、`Quantity`、`apps.Deployment`、`apps.StatefulSet`、`apps.DaemonSet`、`batch.v1.Job`、`networking.v1.Ingress`、`storage.StorageClass`、`autoscaling.v2.HorizontalPodAutoscaler`、`autoscaling.v2beta2.HorizontalPodAutoscaler`（**未标 `@Deprecated`**）、`events.v1.Event`、`metrics.v1beta1.{PodMetrics,NodeMetrics}`
- ❌ **不存在**：`io.fabric8.kubernetes.api.model.Deployment`（无包前缀）→ 必须用 `...api.model.apps.Deployment`
- ❌ **不存在**：`io.fabric8.kubernetes.client.dsl.Top` → 指标入口是 `MetricAPIGroupDSL`（`client.top().nodes().metrics(name)` / `client.top().pods().metrics(ns, name)`）
- ⚠️ KubeConfig 工具在 **`io.fabric8.kubernetes.client.internal.KubeConfigUtils`**（不在 `.utils` 下）；`utils.PatchUtils` **不存在**（patch 走 `PatchContext`）

### 3.1 API 映射表

| client-java 18.0.1 | fabric8 7.9.0 对应 | 是否需改写 | 依据 |
|---|---|---|---|
| `CoreV1Api` / `AppsV1Api` / `BatchV1Api` / `NetworkingV1Api` / `StorageV1Api` / `AutoscalingV*Api` / `CustomObjectsApi` | 无「Api 类」；改为**一个注入式 `KubernetesClient`**：`client.pods()` / `.services()` / `.nodes()` / `.configMaps()` / `.secrets()` / `.namespaces()` / `client.apps().deployments()/.statefulSets()/.daemonSets()` / `client.batch().v1().jobs()` / `client.network().v1().ingresses()` / `client.storage().v1().storageClasses()` / `client.autoscaling().v1()/v2()/v2beta2()` / `client.genericKubernetesResources(ctx)` | **必须**（结构性改写） | CHEATSHEET；`KubernetesClient` 方法实测 |
| `listNamespacedService(ns, null×10)` | `client.services().inNamespace(ns).list()` | 必须 | CHEATSHEET L250–L254 |
| `listNamespacedPod(…)` / `listPodForAllNamespaces(…)` | `client.pods().inNamespace(ns).list()` / `client.pods().inAnyNamespace().list()` | 必须 | CHEATSHEET L104/L108 |
| `listNamespacedDeployment` / `listDeploymentForAllNamespaces` / StatefulSet / DaemonSet 同族 | `client.apps().deployments()/statefulSets()/daemonSets()` + `.inNamespace(ns)` / `.inAnyNamespace()` | 必须 | CHEATSHEET L328/L332 |
| `listIngressForAllNamespaces` | `client.network().v1().ingresses().inAnyNamespace().list()` | 必须 | `network()` 实测存在 |
| `readNamespaced*(…)` | `client.<res>().inNamespace(ns).withName(name).get()` | 必须 | CHEATSHEET L116/L291 |
| `createNamespaced*` / `replaceNamespaced*` / `deleteNamespaced*` | `client.<res>().inNamespace(ns).resource(obj).create()` / `.replace()` / `.serverSideApply()`；删除 `.withName(n).delete()` | 必须 | CHEATSHEET L129/L133/L151/L320 |
| `patchNamespacedServiceCall(...)` + `PatchUtils.patch(V1Service.class, call, "application/merge-patch+json", apiClient)` | `client.services().inNamespace(ns).withName(n).edit(…)` 或 `patch(PatchContext.of(PatchType.JSON_MERGE), json)` | **必须**（补丁类型逐处核对：ServiceController 是 `merge-patch+json` → `JSON_MERGE`；TenxService 是 `strategic-merge-patch+json` → `STRATEGIC_MERGE`） | `PatchContext`/`PatchType` 实测；CHEATSHEET L133/L137 |
| `Yaml.load(...)` / `Yaml.dump(...)` | `Serialization.unmarshal(...)` / `Serialization.asYaml(obj)`；或实例方法 `client.getKubernetesSerialization()` | 必须 | `utils.Serialization` 实测；CHEATSHEET |
| `Watch.createWatch(client, call, type)` + `Watch.Response<T>` | `client.<res>().…watch(new Watcher<T>(){ onEvent(WatchEvent<T>) })` | 必须（迭代器 → 回调 `Watcher`） | CHEATSHEET L173/L403 |
| `PodLogs`（`LogsHandler`/`KubeLogsHandler`） | `.getLog()` / `.tailingLines(n).watchLog(out)` → `LogWatch` | 必须 | `Loggable.watchLog(OutputStream)` 实测；CHEATSHEET L143/L147 |
| `Exec` → `Process` | `.exec(cmd…)` → `ExecWatch`（`redirectingInput()`/`readingInput()`/`writingOutput()`/`usingListener()`） | **必须（语义也变，见 §3.3）** | `ExecWatch`/`ContainerResource` 实测；CHEATSHEET L200/L220 |
| `Configuration.getDefaultApiClient()` / `setDefaultApiClient` | 删除；注入 `KubernetesClient`（或 `new KubernetesClientBuilder().build()`） | 必须 | CHEATSHEET |
| `ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(in))` / `ClientBuilder.cluster()` / `AccessTokenAuthentication` | `Config.fromKubeconfig(text)` / `Config.autoConfigure(null)` + `new KubernetesClientBuilder().withConfig(cfg).build()`；`withTrustCerts(true)`、`withOauthToken(...)` | 必须 | `Config`/`ConfigBuilder` 实测 |
| `io.kubernetes.client.custom.IntOrString` | `io.fabric8.kubernetes.api.model.IntOrString`（构造 `IntOrString(Object)`；getter **`getIntVal()`/`getStrVal()`**） | 必须（换包名 + 换方法名） | 本机 `javap` 实测 |
| `io.kubernetes.client.custom.Quantity` | `io.fabric8.kubernetes.api.model.Quantity`（`getNumericalAmount()`/`getFormat()`） | 必须（换包名） | 实测命中 |
| `io.kubernetes.client.common.KubernetesObject` | `io.fabric8.kubernetes.api.model.{HasMetadata,KubernetesResource}` | 必须 | 实测命中 |
| `openapi.JSON`（`JSON.serialize/deserialize`） | `client.getKubernetesSerialization().asYaml/unmarshal`、静态 `utils.Serialization.*` | 必须 | `KubernetesSerialization` 实测 |
| `openapi.ApiResponse`（`getData()`/`getStatusCode()`） | 无直接对应：DSL 直接返回对象；要状态码则 catch `KubernetesClientException.getCode()` | 必须 | 实测 `KubernetesClientException` |
| `openapi.Pair` | 无对应 | 必须 | — |
| `util.ModelMapper.addModelMap(...)` | 不需要（fabric8 内置模型注册；动态场景用 `GenericKubernetesResource` + `client.genericKubernetesResources(apiVersion, kind)`） | 必须 | `genericKubernetesResources` 实测 |
| `util.Streams.copy` | `java.io.InputStream.transferTo` / `utils.Utils` | 必须 | — |
| `ApiException`（39 处 import，**受检**） | `KubernetesClientException`（**非受检**，继承 `RuntimeException`） | **必须全局改 catch/throws** | 实测 |
| `V1*` 模型（97 个类） | `io.fabric8.kubernetes.api.model.*`，同名去 `V1` 前缀（`Pod`/`Service`/`ConfigMap`…）；特例 `V1Status`→`Status`、`V1DeleteOptions`→`DeleteOptions`、`V1Patch`→无（用 `PatchContext`+String） | 必须 | 实测 + CHEATSHEET |

### 3.2 66 个引用文件的分级

统计口径：`grep -rl 'io\.kubernetes' --include=*.java`；对每个文件统计不同类型数与所用 API 形态。

**A 档 · 必须重写调用逻辑（API 调用 / 异常 / 序列化 / exec / watch / patch）—— 47 个文件**

```
ApplicationStarted.java                            config/websocket/handler/{ExecHandler,KubeLogsHandler,LogsHandler}.java
controller/AuthController.java                     controller/ConfigMapController.java
controller/DashboardsController.java               controller/K8sController.java
controller/K8sProxyController.java                 controller/ServiceController.java      ← 51 个类型
controller/cloud/{TenxApiV2Controller,TenxCloudImpl,TenxController,TenxSpiV2Controller}.java
controller/pub/{PubVipController,PublicComputerController,PublicController}.java           ← PublicController 26 个类型
controller/third/api/loki/LokiApiService.java
service/{AlertService,AppService,AuthService,BackService,CicdService,ComputerService}.java
service/{DashboardsService,FileService,FreeMarkerJobCreator,ImageService,InstancesService}.java
service/{K8sService,LicenceService,RoleService,SettingService,SoftwareService,UserService}.java ← SoftwareService 35 个类型
service/TenxService.java                           ← 82 个类型（最大）
service/impl/{ModeServiceImpl,NodeVersionServiceImpl}.java
utils/K8sApiClientManager.java                     ← 客户端装配总闸
utils/K8sObjectUtil.java                           utils/K8sPodCommandExecutor.java
third/metrics/MetricsServerApi.java
```

**B 档 · 只需换类型名 + 换异常（无控制流变化）—— 5 个文件**

```
config/CustomJacksonConfig.java   config/IntOrStringDeserializer.java
config/IntOrStringSerializer.java config/JsonConfig.java
third/k8s/IoK8sApiCoreV1NodeStatus.java
```

**C 档 · 只有模型 import/字段类型引用（不调用 k8s API），但仍需改类名 —— 14 个文件**

```
controller/DataBaseController.java                    controller/SyncController.java
controller/form/tenx/RadondbInfo.java                 controller/pub/CicdController.java
controller/response/{DeploymentInfo,ExportServiceInfo,ExportSoftwareRelationService,ListenBackInfo,OtherService}.java
controller/response/sync/SyncResult.java              controller/response/tenx/Nodes.java
service/impl/{K3sImageServiceImpl,K8sImageServiceImpl}.java
third/response/tenx/v2/services/{Quantity,ResourceFieldSelector}.java
third/response/tenx/v2/services/list/ServicesItem.java
```

47 + 5 + 14 = **66**，与 `grep -rl` 一致。

### 3.3 优先文件逐调用点

#### `utils/K8sApiClientManager.java`（168 行，客户端总闸，A 档）

| 行 | 厂商代码 | fabric8 7.9.0 |
|---|---|---|
| L49–L51 | `public CoreV1Api getCoreV1Api() { return new CoreV1Api(); }` | **删掉**；改暴露 `KubernetesClient` |
| L53–L55 | `getAppsV1Api()` | → `client.apps()` |
| L57–L59 | `getCustomObjectsApi()` | → `client.genericKubernetesResources(ctx)` |
| L61–L63 | `getBatchV1Api()` | → `client.batch().v1()` |
| L65–L67 | `getMetricsServerApi()` | → `client.top()`（并删除自建 `MetricsServerApi`） |
| L69–L76 | `Configuration.getDefaultApiClient()` + `ApiClient.getHttpClient().newBuilder().readTimeout(0, SECONDS)` 造「watch 专用长连接 client」 | fabric8 的 watch 由同一 `KubernetesClient` 承载；**这整块 OKHttp 代码作废** |
| L86 | `listNamespacedService(ns, null ×10)`（11 个位置参数） | `client.services().inNamespace(ns).list()` |
| L106 | `listPodForAllNamespaces(allowWatchBookmarks,_continue,fieldSelector,labelSelector,limit,pretty,resourceVersion,resourceVersionMatch,timeoutSeconds,watch)` / `listNamespacedPod(...)` | `client.pods().inAnyNamespace().list()` / `.inNamespace(ns).list()`；选择器 → `.withLabelSelector(...)`/`.withFields(...)` |
| L122 | `listNamespacedDeployment(ns, pretty, …, watch)` | `client.apps().deployments().inNamespace(ns).list()` |
| L135–L167 | `ClientBuilder.cluster()` / `ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(input))` / `AccessTokenAuthentication` / `setVerifyingSsl(false)` / `Configuration.setDefaultApiClient` / `HttpLoggingInterceptor` | `Config.autoConfigure(null)` / `Config.fromKubeconfig(text)`；`new ConfigBuilder(cfg).withTrustCerts(true).withOauthToken(token).build()`；`new KubernetesClientBuilder().withConfig(cfg).build()`。**`okhttp3.logging.HttpLoggingInterceptor` 依赖整体移除** |

> 这一处是**所有 66 个文件的地基**：`getCoreV1Api()/getAppsV1Api()/...` 一旦删掉，调用点必须一起改，必须先落地。

#### `controller/cloud/TenxCloudImpl.java`（1040 行，A 档）

| 调用点 | 厂商代码 | fabric8 7.9.0 |
|---|---|---|
| **L849 / L929** | `coreV1Api.listNamespacedService(ns, null×4, "metadata.name="+ingressName, null×6)` | `client.services().inNamespace(ns).withName(ingressName).get()` |
| **L858 / L938** | `v1ServicePort.setTargetPort(targetPort)`（`targetPort = new IntOrString(Integer.valueOf(form.getExportPort()))`）、`setPort/setName/setProtocol`、`v1Service.getSpec().setPorts(ports)` | `new io.fabric8…IntOrString(form.getExportPort())`；模型类名去 `V1` |
| **L863 / L943** | `coreV1Api.replaceNamespacedService(name, ns, v1Service, null×4)` | `client.services().inNamespace(ns).resource(v1Service).replace()` / `.serverSideApply()` |
| L866–L869 / L946–L949 | `catch (ApiException e) { log…; throw new RuntimeException(e); }` | 异常改 `KubernetesClientException`（**非受检 → catch 子句要删/改**） |
| **L878 / L958** | `coreV1Api.replaceNamespacedConfigMap(name, ns, v1ConfigMap, null×4)` | `client.configMaps().inNamespace(ns).resource(cm).replace()` |
| 注入 | `@Resource` | `jakarta.annotation.Resource`（§2.3） |

#### `service/TenxService.java`（3071 行 / 82 个 k8s 类型 / 108 个 dynamic-sql 引用，最重）

（a）**API 调用**：
- L482 `coreV1Api.listNamespacedService(K8s.NAMESPACE, null×5, "name="+serviceName, null×5)` → `.withName(serviceName).get()`
- L492 `listNamespacedPod(...)` → `client.pods().inNamespace(...).withName(...).get()`
- L1060 `mapper.readValue(s, List.class)` 手工拼 `IoK8sApiCoreV1ServiceList`（L1055）→ `client.services().inAnyNamespace().list()`
- `appsV1Api.listNamespacedDeployment`×11 / `replaceNamespacedDeployment`×10 / `listNode`×7 / `listIngressForAllNamespaces`×3 / `replaceNamespacedConfigMap`×3 / `listPodForAllNamespaces`×3
- L1136/1155/1245/2401/2579 `Yaml.load(...)` → `Serialization.unmarshal(...)`
- L1154 `ModelMapper.addModelMap("", "v1", "Service", V1Service.class)` 与 L2578 `Yaml.addModelMap("apps/v1", "Deployment", V1Deployment.class)` → 删除（fabric8 内置模型注册）
- L2404 `PatchUtils.patch(V1Deployment.class, () -> appsV1Api.patchNamespacedDeploymentCall(…, new V1Patch(deployment.toString()), …), "application/strategic-merge-patch+json", …)` → `patch(PatchContext.of(PatchType.STRATEGIC_MERGE), json)`
- `AutoscalingV2beta2Api` / `V2beta2*` 模型（1 个文件）→ `client.autoscaling().v2beta2()`（7.9.0 仍在）；建议顺势切 `v2()`

（b）**`serviceResponse` / `convertObj` 的 GSON→Jackson 往返（L496–L517）——白屏现场，整条链删除**（见 §4）。

（c）**探针转换（L519–L620）——第二处会炸的代码**：
```java
ioK8sApiCoreV1HTTPGetAction.setPort(httpGet.getPort().getIntValue());     // L542, L578
ioK8sApiCoreV1TCPSocketAction.setPort(tcpSocket.getPort().getIntValue()); // L555, L591
```
client-java 18 的 `IntOrString.getIntValue()` 在具名端口下**抛 `IllegalStateException("Not an integer")`**（[v18.0.1 源码](https://github.com/kubernetes-client/java/blob/v18.0.1/kubernetes/src/main/java/io/kubernetes/client/custom/IntOrString.java)）。fabric8 侧 `HTTPGetAction.setPort(IntOrString)` / `TCPSocketAction.setPort(IntOrString)` **直接接受 `IntOrString`** → 这 4 处整块删掉，顺带修掉具名探针端口的 bug。

（d）**`savePortinfo(...)`（L2129–L2165）**：`new IntOrString(port)`（L2140）+ 调试日志（L2141，删）+ `replaceNamespacedService`（L2162）+ `serviceResponse`（L2163）+ `replaceNamespacedDeployment`（L2165）。

（e）`@NotNull` → `jakarta.validation.constraints.NotNull`。

#### `controller/ServiceController.java`（引用数 51，A 档）

- **`Configuration.getDefaultApiClient()` 22 处**（含 `getJSON()` 于 L532/661/744/804/1074/1147/1150/1256/1319/1387/1513/2401）→ 全部删除，改注入 `KubernetesClient`。
- `PatchUtils.patch(V1Service.class, …, "application/merge-patch+json", api.getApiClient())` 共 **8 处**（L534 / L663 / L746 / L806 / L1006 / L1076 / L1148 / L1151）→ `patch(PatchContext.of(PatchType.JSON_MERGE), …)`（**注意与 TenxService 的 `STRATEGIC_MERGE` 不同**）。
- 全仓 patch 介质分布实测：`"application/merge-patch+json"` **31 处**、`"application/strategic-merge-patch+json"` **4 处**——**必须逐处映射到 `PatchType.JSON_MERGE` / `PatchType.STRATEGIC_MERGE`**，不能一刀切。
- `readNamespacedService` / `patchNamespacedServiceCall`；14× `new IntOrString(...)`（L526/609/656/739/775/797/1069/1311 等）→ 换包名。

#### `utils/K8sObjectUtil.java`（245 行，A 档）

纯模型操作 + 少量调用：`deleteDeployment` / `deleteServices` / `deleteService` / `getNodeIpInfo` / `getVipFromStaticPod` / `getVipConfig` / `getMasterNodeName`。
`V1Node/V1NodeStatus/V1NodeCondition/V1NodeAddress/V1NodeSpec/V1Pod/V1PodList/V1Service/V1ServiceList/V1DaemonSetList/V1DaemonSetSpec/V1EnvVar/V1Container/V1ObjectMeta` → 同名去 `V1` 前缀（`apps.DaemonSet*` 带包前缀）；`throws ApiException` 全删。

#### `utils/K8sPodCommandExecutor.java`（44 行，A 档，**语义级重写**）

```java
ApiClient client = this.k8sApiClientManager.initK8sClient();
Exec exec = new Exec(client);
Process process = exec.exec(namespace, podName, command, true, false);
Streams.copy(process.getInputStream(), stdout);
boolean completed = process.waitFor(60L, TimeUnit.SECONDS);   // ← 60s 超时强杀
```
→ fabric8 `ExecWatch w = client.pods().inNamespace(ns).withName(pod).exec(command);`
**注意**：fabric8 返回的是 `ExecWatch`（Closeable 会话），**没有 `waitFor`**。要保留「60s 超时后强杀」的语义必须自己用监听器/超时 + `w.close()` 实现。**这一处是真正的行为重写，不是换名字。**

#### 其余 A 档要点

- `ApplicationStarted.java`：**L192** `Watch.createWatch(watchClient, listNodeCall, new TypeToken<Watch.Response<V1Node>>(){}.getType())`、**L324** `Watch.createWatch(watchClient, api.listNamespacedPodCall(namespace, …, true, null), Watch.Response.class)` → `client.nodes().watch(new Watcher<Node>(){…})` / `client.pods().inNamespace(ns).watch(…)`。
- `controller/K8sProxyController.java`：**L105** `Watch.createWatch(coreV1Api.getApiClient(), this.buildCall(coreV1Api.getApiClient(), watchUrl + "?watch=true", "GET", null, accepts, null), new TypeToken<Watch.Response<Object>>(){}.getType())`，自建 `buildCall` 在 **L182–L194**（用 `ApiClient.buildCall(...)` + `execute(...)`，见 L202）—— **保留「任意子资源 watch」能力需要额外设计**，fabric8 没有等价的 `buildCall`（通常用 `genericKubernetesResources` 或 `client.raw(url)`）。**未定论项**：`KubernetesClient.raw(String)` 在 7.9.0 是否可用我没核（`KubernetesClient` 方法表里未见 `raw`）。
- `controller/ConfigMapController.java`：`JsonConfig.JACKSON.readValue(json.serialize(configMap), V1ConfigMap.class)`（L142）及反向（L168/231/320）—— **client-java(Gson) ↔ Jackson 双向桥可整体删除**（fabric8 模型本身是 Jackson 模型），且这正是 §4 白屏的同类风险点。
- `service/CicdService.java`：`listIngressForAllNamespaces`、`getNamespacedCustomObject` → `client.network().v1().ingresses().inAnyNamespace().list()`、`genericKubernetesResources(...)`。
- `service/FileService.java`：`podLogs` + 文件读写 → `PodResource.file(...)` / `.getLog()`。
- `controller/cloud/TenxController.java`：`Yaml.load(yaml)` → `Serialization.unmarshal(yaml, Service.class)`（L484/514）。
- `service/FreeMarkerJobCreator.java`：`Yaml.load(jobYamlString)` → `Serialization.unmarshal(…, Job.class)`（L48）。
- `third/metrics/MetricsServerApi.java`（74 行，**整类删除**）：现在用 `ApiClient.buildCall("/apis/metrics.k8s.io/v1beta1/nodes", …)` + `execute(call, MetricsNodeList.class)` → `client.top().nodes().metrics(name)` / `client.top().pods().metrics(ns, name)`；自建 `MetricsNode`/`MetricsNodeList`/`MetricsPodList` 一并删除。

### 3.4 `third/k8s` 的 608 个手抄模型

厂商在 `com.glriverside.gaop.be.third.k8s` 下**手抄了 608 个旧版 k8s 模型**（`IoK8sApi*` 前缀 **596** 个），与 `io.kubernetes.client.openapi.models` 并存，靠 `TenxService.convertObj` 在两者间做 JSON 往返。fabric8 **只有一套模型**，因此：

- **608 个文件整体删除**，业务代码直接用 `io.fabric8.kubernetes.api.model.*`。
- 涉及面就是 §3.2 A/B 档的大部分工作量来源。
- 这些类里 `Integer targetPort` / `Integer port` 的旧字段定义，正是 §4 白屏根因——**删除它们是「修 bug」而不仅是重构**。

### 3.5 序列化配置一起处理

`config/JsonConfig.java` / `CustomJacksonConfig.java` / `IntOrStringSerializer.java` / `IntOrStringDeserializer.java` 这 4 个类是**为 client-java 的 `IntOrString` 打的补丁**（client-java 的 `IntOrString` 暴露 `getStrValue()`/`getIntValue()` 两个 getter，Jackson 会序列化成 `{"intValue":…,"strValue":…}`，故必须自定义）。

用 fabric8 后：**这些类整体删除**。fabric8 的 `AnyType`/`IntOrString` 自带 `@JsonCreator` + `@JsonSerialize(using = AnyType.Serializer.class)`（本机 `javap -v` 实测），Jackson 原生即可读写。

⚠️ **容易漏的坑**：client-java 的 `IntOrString` 带 `@JsonAdapter(...)`，**只作用于 client-java 的类**。换成 fabric8 后，凡是用 `new Gson()` 直接啃 fabric8 模型的代码点（如 `SyncController` 的 `TypeToken<List<V1Service>>`、`ReverseProxyConfig` 的 `ServiceListBody`、`JsonConfig.GSON`）会退化成 `{"intVal":…,"strVal":…}` 形状或直接失败 → **统一改走 `client.getKubernetesSerialization()`（Jackson）**。

---

## 4. 具名端口定论 —— **fabric8 7.9.0 接受字符串，本机已跑通；白屏根因在厂商自建 DTO**

### 4.1 问题

集群里 Service 的 `targetPort` 写成具名端口（`targetPort: "http"`，Helm chart 里非常常见）时，厂商「网络页」白屏。工单记录的报错是 `Cannot deserialize Integer from String`，并把归因写成「厂商用 client-java 18 时抛」。**归因需要修正。**

### 4.2 结论 A：**fabric8 7.9.0 的模型接受字符串**（可执行验证 ✅）

用**本仓 `paas-core-infrastructure` 现有的 fabric8 7.9.0 依赖**导出 classpath，编译运行最小反序列化探针（源码见 §7）：

```java
ServicePort.class.getMethod("getTargetPort").getReturnType().getName()   // → IntOrString
TCPSocketAction.class.getMethod("getPort").getReturnType().getName()     // → IntOrString
new ObjectMapper().readValue("…\"targetPort\":\"http\"…", Service.class) // → 成功
```

**实跑输出（Java 21 / fabric8 7.9.0）**：

```
== fabric8 7.9.0 ServicePort.targetPort 类型 ==
ServicePort.getTargetPort -> io.fabric8.kubernetes.api.model.IntOrString
TCPSocketAction.getPort  -> io.fabric8.kubernetes.api.model.IntOrString

== A. 具名 targetPort "http" (plain ObjectMapper) ==
  OK  getStrVal()=http isInt=false
  回写 JSON={"name":"http","port":80,"protocol":"TCP","targetPort":"http"}

== B. 具名 targetPort "http" (KubernetesSerialization) ==
  OK  getStrVal()=http

== C. 数字 targetPort 8080 ==
  OK  getIntVal()=8080 strVal=null
  回写 JSON={"name":"http","port":80,"protocol":"TCP","targetPort":8080}

== D. Pod livenessProbe.tcpSocket.port="http" ==
  OK  getStrVal()=http
```

**判定**：`ServicePort.targetPort` 与 `TCPSocketAction.port` 都是 `IntOrString`，**字符串与整数双向支持，且回写 JSON 保持原类型**（`AnyType` 的 `@JsonCreator` 构造器 + `AnyType.Serializer` 负责保真）。→ 换到 fabric8 后**这条根因从模型层就不存在**。这是执行验证，不是文档推断。

### 4.3 结论 B：那条 `Integer` 来自厂商**自己手抄的模型**（根因复现 ✅）

**证据链（三步，全部一手）**

1. **client-java 18 自己不是 `Integer`**：[v18.0.1 `V1ServicePort.java`](https://github.com/kubernetes-client/java/blob/v18.0.1/kubernetes/src/main/java/io/kubernetes/client/openapi/models/V1ServicePort.java) 第 55 行 `private IntOrString targetPort;`；[`V1TCPSocketAction.java`](https://github.com/kubernetes-client/java/blob/v18.0.1/kubernetes/src/main/java/io/kubernetes/client/openapi/models/V1TCPSocketAction.java) 第 35 行 `private IntOrString port;`。（本机另有 client-java 12.0.0 的 jar，`javap` 确认同为 `IntOrString`。）
2. **厂商自己的模型才是 `Integer`**：`third/k8s/IoK8sApiCoreV1ServicePort.java` 第 20–21 行
   ```java
   @JsonProperty(value="targetPort")
   private Integer targetPort = null;
   ```
   （同类：`IoK8sApiCoreV1TCPSocketAction.port`、`IoK8sApiCoreV1HTTPGetAction.port` 也是 `Integer`。注：`controller/form/service/UpdatePortItem.java:18` 的 `Integer targetPort` 是**入参表单**，不是响应模型，不在本链路。）
3. **转换路径就是它**：`TenxService.serviceResponse(V1Service)`（L506–L517）调 `TenxService.convertObj(v1Service, IoK8sApiCoreV1Service.class)`；`convertObj`（L496–L504）= `JsonConfig.GSON.toJson(obj)` → `JsonConfig.JACKSON.readValue(json, 目标类型)`。client-java 的 `IntOrString` 带 `@JsonAdapter(IntOrString.IntOrStringAdapter.class)`，GSON 忠实吐成裸字符串 `"http"`；而 `JsonConfig.JACKSON`（L24 声明、L38 只注册了 `addSerializer`、L39 `registerModule`）**没有对应的 `addDeserializer`**，目标字段又是 `Integer` → 炸。

**用真实 `client-java IntOrString` + GSON + Jackson 复现**（`/tmp/convprobe`）：

```
1) GSON 输出: {"name":"http","port":80,"protocol":"TCP","targetPort":"http"}
2) 目标类型: com.glriverside.gaop.be.third.k8s.IoK8sApiCoreV1ServicePort.targetPort = Integer
3) FAIL -> com.fasterxml.jackson.databind.exc.InvalidFormatException
   Cannot deserialize value of type `java.lang.Integer` from String "http": not a valid `java.lang.Integer` value
```

**报错文字与工单记录一致。**

### 4.4 失败链路与现象对齐

「网络页」数据来自 `TenxService.allService(...)`（L2294），它对每个 Service 调 `serviceResponse(v1Service)`：

```java
public IoK8sApiCoreV1Service serviceResponse(V1Service v1Service) {
    IoK8sApiCoreV1Service service = new IoK8sApiCoreV1Service();
    try {
        service = TenxService.convertObj(v1Service, IoK8sApiCoreV1Service.class);  // ← 在这里炸
    } catch (Exception e) {
        log.error("转换类型失败", e);
        return null;                        // ← 吞掉异常，返回 null
    }
    ...
}
```

1. GSON 把 `IntOrString("http")` 写成 `"targetPort": "http"`；
2. Jackson 读进 `Integer` 字段 → `InvalidFormatException`；
3. `serviceResponse` 捕获后 **`return null`** → `servicesItem.setService(null)`；
4. 前端拿到 `service == null` → **白屏**（不是 500 错误页，与「白屏」现象吻合）。

**同一根因的第二处、更早的硬失败**：`TenxService.responseContainer(...)` 对探针端口直接调 `getPort().getIntValue()`（L542/555/578/591）→ 具名端口下 `IllegalStateException("Not an integer")`。

> 旁证：厂商写了 `CustomJacksonConfig` + `IntOrStringDeserializer`/`Serializer` 来补 `IntOrString`，但 `CustomJacksonConfig` 只作用于 **Web 层 `MappingJackson2HttpMessageConverter` 的 ObjectMapper**，**不作用于 `JsonConfig.JACKSON`**，所以救不了这条链路。可直接复核。

### 4.5 对本次重写的直接含义

1. **换 fabric8 不会自动修好白屏**：如果重写时继续保留 `third/k8s/IoK8sApiCoreV1*` 这套 `Integer` DTO（它同时是对外 API 契约），故障原样复现。
2. 修法（两条都做）：
   - 把这套 DTO 的 `targetPort` / 探针 `port` 由 `Integer` 改为 `Object`/`IntOrString`（保持对外 JSON 上「数字或字符串」）；
   - fabric8 化之后**统一用 Jackson**（fabric8 原生即 Jackson），从根上消掉 `convertObj`；`serviceResponse` 的 `catch (Exception) → return null` 必须改成显式失败或降级，否则同类问题仍会**静默白屏**。
3. 具名端口必须进回归用例：`targetPort: http`、`httpGet.port: metrics`、`tcpSocket.port: db`。

> **未定论（严格说明）**：我没有启动厂商应用、没有连真集群做端到端复现，所以「白屏 == 这一条异常」是**基于代码路径 + 逐字复现的强推断**，不是端到端观测。坐实需要：一个 `targetPort: "http"` 的 Service + 跑厂商 jar 抓栈。

---

## 5. 离线与 arm64 影响

### 5.1 必须进离线私仓（或必须随仓携带）的制品

| 制品 | 为什么必须预置 | 证据 |
|---|---|---|
| **`org.csource:fastdfs-client-java:1.30-SNAPSHOT`** | **Maven Central 根本没有这个 GAV**（元数据 404）；`systemPath` 依赖不参与仓库解析，任何私仓也拉不到——只能随源码树带 `lib/` 或手工 `install:install-file` | <https://repo1.maven.org/maven2/org/csource/fastdfs-client-java/maven-metadata.xml>（404）；`pom.xml:351-357` |
| **`org.fife.ui:rsyntaxtextarea:2.0.4.1`**（厂商现坐标） | 该 groupId 在 Central **完全不存在**（只能来自已下线的 jcenter 或内网镜像）→ 换到 `com.fifesoft:rsyntaxtextarea:4.0.1` **即可免预置**（Central 有，且同版本 2.0.4.1 也有） | <https://repo1.maven.org/maven2/org/fife/ui/rsyntaxtextarea/maven-metadata.xml>（404）；<https://repo1.maven.org/maven2/com/fifesoft/rsyntaxtextarea/maven-metadata.xml> |
| `com.github.tobato:fastdfs-client:1.27.2` + 传递依赖（`commons-beanutils`、`commons-io`、`commons-pool2`、`slf4j-api`） | Central 有，但**上游 2024-05 后无发布**；一旦自维护 fork 必须以自有坐标入库 | <https://repo1.maven.org/maven2/com/github/tobato/fastdfs-client/1.27.2/fastdfs-client-1.27.2.pom>；<https://github.com/tobato/FastDFS_Client> |
| `com.fatboyindustrial:gson-javatime-serialisers:1.1.2` | Central **有**（1.1.2 即末版）；建议**冻结版本**（不预置也能拉到） | <https://repo1.maven.org/maven2/com/fatboyindustrial/gson-javatime-serialisers/gson-javatime-serialisers/maven-metadata.xml> |
| fabric8 全家桶 + vertx/netty + `org.jspecify:jspecify:1.0.0` | 都在 Central；离线构建需预拉（fabric8 会带出 20+ 个 `kubernetes-model-*`） | 本仓 [01-deps.txt](01-deps.txt) |

其它结论：
- `druid-spring-boot-3-starter`、`springdoc-openapi-starter-webmvc-ui`、`bcprov-jdk18on`、`com.mysql:mysql-connector-j`、`pagehelper 2.1.1`、`mybatis-dynamic-sql 2.0.0` 都是 Central 正常发布物，联网预拉一次 `dependency:go-offline` 即可。
- Maven 侧须**移除**三处遗留写法：`<scope>system</scope>` + `<systemPath>`（pom.xml:356）、`<includeSystemScope>true</includeSystemScope>`（L478）、`<extdirs>${project.basedir}/lib</extdirs>`（L490）——Maven 3.9+/4 会告警并在未来移除。

### 5.2 arm64（aarch64）

| 项 | 结论 |
|---|---|
| **整体无阻塞**：原厚包 256 个 jar 里**没有** `netty-transport-native-epoll`、没有 `.so`/JNI 落地（netty 只有 buffer/codec/common/handler/resolver/transport，纯 Java） | `BOOT-INF-lib-list.txt` 全文核对 |
| `com.github.luben:zstd-jni`（Kafka 压缩，`zstd-jni-1.4.4-7.jar`） | **待复核**：该构件按平台打包 native，须确认私仓缓存的是含 `linux-aarch_64` 的同一构件（见 §6-1） |
| `org.xerial.snappy:snappy-java`（`snappy-java-1.1.7.3.jar`） | **待复核**（同类 native 打包） |
| `netty-tcnative-classes`（fabric8 运行时带入） | 只是 classes，非 native；只有走「native TLS」才需要 `netty-tcnative-boringssl-static` 的 `osx-aarch_64`/`linux-aarch_64` classifier |
| `docker-java` + `jna 5.5.0`（`BOOT-INF-lib-list.txt:110`） | ⚠️ **待复核**：JNA 5.5.0 年代较早，macOS/aarch64 支持需确认（见 §6-2） |
| 其余（`mysql-connector-j`/`druid`/`bcprov`/`sshd-core`/`freemarker`/`hutool-all`/`gson`/`tika`/`poi`/fabric8） | 纯 Java，无 arm64 适配需求 |

---

## 6. 未定论清单（还差什么证据）

| # | 未定论项 | 还差什么证据 |
|---|---|---|
| 1 | `zstd-jni` / `snappy-java` 是否已为 linux-aarch64 预置 | 在目标 arm64 机器跑一次真实 Kafka 压缩收发，或核对私仓构件的 native 清单（本机只能看到 x86_64 参考物，属间接证据） |
| 2 | JNA 5.5.0 在 macOS/arm64 是否可用（docker-java 传递依赖） | 缺 JNA 官方 CHANGELOG 里 `darwin-aarch64` 的引入版本；或在 arm64 上跑一次 docker-java 调用 |
| 3 | 反编译产物里 518 处 `new SqlCriterion[0]` 有多少是**原始源码显式写的** | 需要原始（未反编译）源码，或对 dynamic-sql 1.5.x/2.0.0 直接编译 `recovered` 源码树拿到编译错误清单。当前结论：**处理动作相同（删掉该实参）**，故不阻塞 |
| 4 | `com.github.tobato:fastdfs-client` 在 Boot 3 下能否自动装配（`FdfsClientConfig`），以及有无第三方 Boot 3 fork | 需要在 Boot 3 骨架上引一次并启上下文；fork 需检索 GitLab/Gitee 与私仓（GitHub 侧无官方 Boot 3 发布已定论） |
| 5 | `pagehelper 2.1.1` 在 **Boot 3.3.5**（README 配对 3.5.0）的运行时兼容性；`Page`/`PageInfo` 从 5.1.11→6.1.1 的方法级差异 | 本仓骨架加依赖 + `mvn package` + 启一次上下文 + 分页回归；release notes 未见破坏性签名变化 |
| 6 | 「网络页白屏 == `InvalidFormatException`」的端到端坐实 | 真集群 + `targetPort: "http"` 的 Service + 跑厂商 jar 抓栈（§4 已把代码路径逐字复现） |
| 7 | `bcprov-jdk15on 1.65` 在 JDK 21 上是否**一定**坏 | BC 官方无「1.65 不支持 JDK 21」声明。只能按官方推荐线换 `bcprov-jdk18on`（代价为零），**不能断言不换必坏** |
| 8 | MySQL 8.x 的 `serverTimezone` 是否必须改 `connectionTimeZone` | `dev.mysql.com` 的 connector-j props 页本机抓到空内容（缺一条官方原文） |
| 9 | `sshd-core 2.12.0` 在 JDK 21 上是否踩坑 | 官方 change log 无 JDK 21 相关条目（有/无都不能证）。建议直接上 2.20.0 规避 |
| 10 | fabric8 `KubernetesClient.raw(String)` 是否可用于「任意子资源 watch/patch」替代 client-java 的 `buildCall` | 需要在 7.9.0 上核 `raw` 的存在与签名（本机 `KubernetesClient` 方法表里未见 `raw`） |
| 11 | `charon-spring-webmvc` 5.x 中哪个版本精确匹配 Spring Boot 3.3 | 5.4.0 依赖 `spring-web:6.2.0`（对应 Boot 3.4/3.5 线），需确认 5.0–5.3 里面向 Spring 6.0/6.1 的那个 |

---

## 7. 本次取证的可复现命令

```bash
# ---- 计数 ----
cd recovered/gaop-be/src/main/java
for p in javax.servlet javax.annotation javax.websocket javax.validation javax.crypto javax.net javax.sql; do
  echo "$p files=$(grep -rlE "$p\." --include=*.java . | wc -l) hits=$(grep -rhoE "$p\.[A-Za-z]" --include=*.java . | wc -l)"
done
grep -rl 'io\.kubernetes' --include=*.java . | wc -l                                       # 66
grep -rhoE 'io\.kubernetes\.[A-Za-z0-9_.]+' --include=*.java . | sort -u | wc -l           # 128
ls com/glriverside/gaop/be/third/k8s/*.java | wc -l                                        # 608
grep -rho 'new SqlCriterion\[0\]' --include=*.java . | wc -l                               # 518
grep -rl 'import org.mybatis.dynamic.sql.SqlCriterion;' --include=*.java . | wc -l          # 69

# ---- 具名端口「结论 A」：用本仓 fabric8 依赖跑最小反序列化 ----
cd /Users/csm/Desktop/PaaS/paas-core/paas-core-infrastructure
mvn -q -o dependency:build-classpath -Dmdep.outputFile=/tmp/paas-cp.txt
#   编译运行 /tmp/portprobe/PortProbe.java（判 ServicePort/TCPSocketAction 字段类型 + 反序列化 "http"/8080）

# ---- 具名端口「结论 B」：真实 client-java IntOrString + GSON→Jackson 复现 ----
#   编译运行 /tmp/convprobe/ConvProbe.java（classpath 加 client-java-api-12.0.0.jar + gson）

# ---- 目标依赖集解析验证 ----
#   /tmp/verifymig/pom.xml + mvn -B dependency:list -DoutputFile=/tmp/verifymig/deps.txt -DincludeScope=runtime
grep javax /tmp/verifymig/deps.txt || echo "(依赖树无 javax.*)"

# ---- dynamic-sql 1.1.4 vs 1.5.2 的 where(...) 签名 ----
cd /tmp/dsql && curl -sLO .../mybatis-dynamic-sql/1.1.4/mybatis-dynamic-sql-1.1.4-sources.jar
           && curl -sLO .../mybatis-dynamic-sql/1.5.2/mybatis-dynamic-sql-1.5.2-sources.jar
unzip -oq 1.1.4-src.jar -d a; unzip -oq 1.5.2-src.jar -d b
grep -n "where(" a/org/mybatis/dynamic/sql/where/AbstractWhereDSL.java
grep -n "where(" b/org/mybatis/dynamic/sql/where/AbstractWhereStarter.java
grep -n "class SqlCriterion" a/org/mybatis/dynamic/sql/SqlCriterion.java b/org/mybatis/dynamic/sql/SqlCriterion.java

# ---- pagehelper Boot3 证据 ----
unzip -l pagehelper-spring-boot-autoconfigure-2.1.1.jar | grep -i "AutoConfiguration.imports"
#   + javap -v 扫 3 个 class 的常量池确认无 javax/jakarta

# ---- hutool javax vs jakarta ----
javap -v -cp x5.8.48 cn.hutool.extra.servlet.ServletUtil        | grep -oE '(javax|jakarta)\.servlet\.[A-Za-z]+'
javap -v -cp x5.8.48 cn.hutool.extra.servlet.JakartaServletUtil | grep -oE '(javax|jakarta)\.servlet\.[A-Za-z]+'

# ---- Boot 托管版本（本仓实际父版本 3.3.5）----
grep -E '<(freemarker|gson|mysql|snakeyaml|hibernate-validator|tomcat|jakarta-validation)\.version>' \
  /Users/csm/DevTools/repository/org/springframework/boot/spring-boot-dependencies/3.3.5/spring-boot-dependencies-3.3.5.pom
```

> `/tmp/portprobe`、`/tmp/convprobe`、`/tmp/verifymig`、`/tmp/dsql` 是验证脚手架，**不在本仓**；建议另开工单把前两个搬成 `paas-core-infrastructure/src/test` 下的回归测试（具名端口用例正好可作为契约测试的一部分）。

## 附：本地证据索引

| 证据 | 路径 |
|---|---|
| 厂商 pom | `recovered/gaop-be/pom.xml`（父 `spring-boot-starter-parent:2.3.1.RELEASE`；`java.version=1.8`） |
| 运行期 jar 清单（256 个） | `recovered/gaop-be/BOOT-INF-lib-list.txt` |
| 源码（1600 个 `.java`） | `recovered/gaop-be/src/main/java/**` |
| 配置 | `recovered/gaop-be/src/main/resources/application{,-dev,-k8s,-prod}.{yml,properties}`（**驱动类仍是 `com.mysql.jdbc.Driver`**） |
| 白屏根因 DTO | `…/third/k8s/IoK8sApiCoreV1{ServicePort,TCPSocketAction,HTTPGetAction}.java` |
| 白屏根因调用链 | `…/service/TenxService.java:496-517`（`convertObj`/`serviceResponse`）、`:2294`（`allService`）、`:542/555/578/591`（`getPort().getIntValue()`） |
| Jackson/Gson 配置（补丁类） | `…/config/JsonConfig.java`、`…/config/CustomJacksonConfig.java`、`…/config/IntOrString{De}Serializer.java` |
| K8s 客户端管理 | `…/utils/K8sApiClientManager.java` |
| 大文件（改写重点） | `…/service/TenxService.java`(3071)、`…/controller/ServiceController.java`、`…/controller/pub/PublicController.java`、`…/service/SoftwareService.java` |
