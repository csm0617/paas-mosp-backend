# 中文乱码的真正来源：**我们自己的播种**，不是厂商（2026-10-14 自查纠正）

> **纠正记录**：本文件的前一版（`11-vendor-charset-defect.md`）把"接口返回双重编码的中文"归因为厂商缺陷
> （connector 5.1.48 缺 `useUnicode=true`）。**这条是错的，已作废。** 同名的旧文件已删除，历史留在 git 里。

## 决定性证据

1. **库里的字节就是双重编码的**：`select hex(name) from g_role` →
   `C3A8C2BFC290…`（45 字节），而正确中文「运维管理员门户」应是 24 字节的 `E8BF90E7BBB4…`。
2. **厂商与我方返回的字节完全一致**：
   `"name":"` 之后两边都是 `c3 a8 c2 bf c2 90 c3 a7 c2 bb c2 b4 …`——同一份数据，同一份输出。
3. **`mysql` CLI 看起来正常是个陷阱**：当时的 CLI 会话是 `character_set_client=latin1`，
   utf8mb4 列 → latin1 客户端 的转换恰好把双重编码**还原**成正确的中文，所以肉眼看到的是好的。

## 真正的成因

早期播种时用 `kubectl exec mysql -- mysql -u… < seed.sql`（**没有 `--default-character-set=utf8mb4`**），
客户端会话是 latin1，UTF-8 的种子文件被当作 latin1 解读后再转成 utf8mb4 入库 → 双重编码。
厂商只是忠实返回库里的内容。

## 影响与修法

- **不是契约差异**：两边一致，`read-paths.json` 里 `/v1/role` 的 knownDeviation 已相应改写。
- **修法**：修数据，而不是追字节。重播种要带字符集：
  ```bash
  kubectl -n default exec -i <mysql-pod> -- mysql -uroot -p… --default-character-set=utf8mb4 < seed.sql
  ```
  修完之后**厂商与重写版会一起变成正确中文**（读的是同一份库），所以之前录下的样本会显得"过时"——需要重录。
- 更广的教训：接口层出现怪异现象时，**先怀疑自己的数据与工具链**，再归因上游。这是本会话第二次因为跳过这一步而误判
  （前一次是"依赖时速云"）。
