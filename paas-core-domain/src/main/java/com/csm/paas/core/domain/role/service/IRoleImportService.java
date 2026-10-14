package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.valobj.ImportRoleRowVO;

import java.util.List;

/**
 * 战位导入的落库逻辑（厂商 `RoleService.handleImportExcel:836-846` + `importRoleExcel:852-1124`）。
 *
 * <h3>逐行判定顺序（照抄，含一处死代码）</h3>
 * <ol>
 *   <li>cell1（唯一标识）读不到 ⇒ 400 `role.key.is.null.or.format.error`；</li>
 *   <li>`key` 长度 &gt;36 或 &lt;1 ⇒ 400 `role.key.is.illegal`；</li>
 *   <li>cell0（名称）读不到 ⇒ 400 `role.name.is.null.or.file.format.error`；</li>
 *   <li>`name` 长度 &gt;100 或 &lt;1 ⇒ 400 `role.name.is.illegal`；</li>
 *   <li>`isEmpty(key) || isEmpty(name)` ⇒ 400 `role.import.role.key.not.valid`——
 *       **不可达**（2/4 已经把空串拦下），照抄保留顺序；</li>
 *   <li>`existKeyName(key, name)`（**key 或 name** 命中）：`isCover=false` ⇒ **整行跳过**；
 *       `true` ⇒ 覆盖该战位；</li>
 *   <li>未命中 ⇒ 收集成"新战位"，**所有行处理完再统一插入**。</li>
 * </ol>
 *
 * <h3>覆盖分支（`importRoleExcel:865-1010`）</h3>
 * 取 `select(key = ? OR name = ?)` 的**第一条** ⇒ 删光该战位的用户关联与软件关联 ⇒
 * 写回 `desc`（**整行 update**，desc 为 null 就写成 NULL）⇒ 按 JSON 列重写用户/软件/子角色关联。
 * 其中用户关联会**顺带给每个用户补上 `user` 战位的关联**（若该用户还没有）。
 *
 * <h3>新战位分支</h3>
 * `id` 用随机 UUID、`type=1`、`createdAt=now`、`updatedAt=null`；插入后按 `key` 反查 id 再写关联。
 *
 * <h3>两个"顺带"行为</h3>
 * <ul>
 *   <li>**自动名**：只有"新战位"分支会调 `saveAutoNameMessage`（键
 *       `i18n.role.en_US.<key>` / `i18n.role.fr_FR.<key>`），值为 null 时**不写**；
 *       覆盖分支正常路径**不写**自动名（厂商在子角色列非空/空两种情况下都 `continue` 掉了）；</li>
 *   <li>**没有事务**：厂商 `handleImportExcel` 上没有 `@Transactional`，中途报错会留下半写状态——照抄。</li>
 * </ul>
 */
public interface IRoleImportService {

    /**
     * @param rows    解析好的行（null/空 ⇒ 什么都不做，对应厂商"表格无内容直接返回成功"）
     * @param isCover true = 命中 key/name 时覆盖该战位；false = 跳过该行
     */
    void importRoles(List<ImportRoleRowVO> rows, boolean isCover);
}
