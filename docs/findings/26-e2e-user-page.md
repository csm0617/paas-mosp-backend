# 切片 3a 端到端实测：厂商原样前端渲染出我们的「用户管理」页

**证据等级**：〔运行实测〕。2026-10-14，本机 `paas-core` + 集群 `172.16.209.25` 的真实 MySQL/Redis/K8s。

## 1. 结论（三条，逐条可复现）

1. **厂商前端一行不改**，`/gaop/usermanage/account`（用户管理）用**我们的后端**渲染成功：
   标题 `用户管理`、10 个表头、1 行真实数据（`admin`），关联席位显示 `运维管理员门户` 芯片，操作为 `编辑/删除`。
   截图：[我们](artifacts/2026-10-14-user-page.png) / [厂商](artifacts/2026-10-14-user-page-vendor.png)。
2. **`GET /v1/user/info` 与厂商在线响应逐字节相等**（同一时刻、同一库、同一会话）：
   ```
   equal = True        # 两侧 JSON 完全一致（含双重编码的中文字节、subRoles:null、belongUsers:[]）
   ```
3. **A/B 对照**（同一份前端、同一浏览器、两个后端）：标题 / 表头 / 数据行 / 左侧导航**全部一致**；
   唯一差异是**一个已知缺口**——`GET /v1/sub_role/switchRole` 我们 404、厂商 200（属切片 3c，未实现）。

## 2. 复现步骤（照抄可重放）

```bash
# 1) 临时暴露 Redis（ClusterIP → NodePort；**用完必须删除**）
kubectl -n gaop-namespace expose deploy gaop-redis --type=NodePort --name=gaop-redis-tmp --port=6379
kubectl -n gaop-namespace get svc gaop-redis-tmp -o jsonpath='{.spec.ports[0].nodePort}'   # 例：32594

# 2) 起我们的后端（连集群真实 MySQL/Redis）
mvn -o -q -DskipTests package
MYSQL_HOST=172.16.209.25 MYSQL_PORT=30306 REDIS_HOST=172.16.209.25 REDIS_PORT=32594 \
  java -jar paas-core-app/target/paas-core-app-1.0.0-SNAPSHOT.jar

# 3) **只登录一次**，把同一个 token 交给两侧 harness（max-login=1，两次登录会互相踢掉）
TOK=$(curl -s -D - -o /dev/null -X POST http://127.0.0.1:8080/v1/pub/login \
      -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"admin123","role":"admin"}' \
      | sed -n 's/.*gateway-token=\([^;]*\).*/\1/p' | tr -d '\r')

GAOP_TOKEN=$TOK BACKEND=http://127.0.0.1:8080 node tools/frontend-e2e-harness.mjs 8099 &   # 我们的
kubectl proxy --port=18001 &                                                               # 厂商
GAOP_TOKEN=$TOK BACKEND=http://127.0.0.1:18001/api/v1/namespaces/gaop-namespace/services/gaop-be:8080/proxy \
  node tools/frontend-e2e-harness.mjs 8098 &                                               # 厂商的

# 4) 驱动 Chrome（需要 puppeteer-core；NODE_PATH 指向它，脚本位置不参与解析）
cd /tmp/e2e && NODE_PATH=/tmp/e2e/node_modules GAOP_TOKEN=$TOK \
  node /Users/csm/Desktop/PaaS/paas-core/tools/frontend-e2e-check.js \
       http://127.0.0.1:8099/gaop/usermanage/account /tmp/e2e-account

# 5) 清理（**必做**）
pkill -f frontend-e2e-harness.mjs; pkill -f 'paas-core-app.*jar'; pkill -f 'kubectl proxy'
kubectl -n gaop-namespace delete svc gaop-redis-tmp
```

## 3. 页面实际调用的端点（我们这一侧，全部 200）

```
200 GET /api/gaop/settings
200 GET /api/gaop/v1/image/projectList?page=1&size=999
200 GET /api/gaop/v1/lang/current
200 GET /api/gaop/v1/licence/info
200 GET /api/gaop/v1/mode/current
200 GET /api/gaop/v1/pub/quickStart
200 GET /api/gaop/v1/role/software
200 GET /api/gaop/v1/user
200 GET /api/gaop/v1/user/info?page=1&size=10&keyword=      ← 本切片新增
200 GET /api/gaop/ws/info
404 GET /api/gaop/v1/sub_role/switchRole?page=1&size=9999    ← 唯一缺口，属切片 3c
```
（原始记录：`artifacts/2026-10-14-user-page.api.json`）

**注意**：页面用 `size=10` 请求列表（不是我们以为的 20），并且**先**拉 `/v1/sub_role/switchRole` 再拉用户列表——
说明子角色下拉是这一页的**前置**，所以切片 3c 不是"可延后"，而是这一页完整可用的一部分。

## 4. 一个必须记住的操作坑（本轮踩了）

**`max-login=1`（厂商单点登录）让"两次登录"互相踢掉**：先给 harness 取一个 token，再用另一个 token 驱动浏览器，
第一个 token 立刻失效 → 页面被路由守卫弹回登录页（`401 /v1/user`）。
**正确做法：只登录一次，把同一个 token 同时交给 harness 与 CDP 脚本。**
本轮的第一次尝试就是这么失败的（页面停在 `/gaop/user/login?r=/`），不是实现问题。

## 5. 与厂商的一致性核对（逐字段）

A/B 机器比对结果：`标题 true / 表头 true / 数据行 true / 左侧导航 true / 行数 1:1`；
`/api` 端点集合唯一差异就是上面那条 404。首行 11 个单元格逐字相同：

```
["", "1", "admin", "ç®¡ç†å‘˜", "", "è¿ç»´ç®¡ç†å‘˜é—¨æˆ·\n 添加席位", "添加子角色",
 "å¹³å�°ç®¡ç†å‘˜ï¼ˆé›†æˆ�éªŒè¯�åˆ�å§‹åŒ–ï¼‰", "2026-10-14 00:18:44", "启用", "编辑删除"]
```

其中 `关联角色`（`belongUsers`）为空是**正确的**：本环境 `admin` 没有 type=2 的角色，厂商同样为空。
中文的乱码是**库里就是双重编码**（`docs/findings/11-own-seed-double-encoding.md`），两侧字节一致——不是本轮引入的问题。
