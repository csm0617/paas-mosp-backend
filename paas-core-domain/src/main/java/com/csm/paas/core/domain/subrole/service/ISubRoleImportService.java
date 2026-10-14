package com.csm.paas.core.domain.subrole.service;

import com.csm.paas.core.domain.subrole.model.valobj.ImportSubRoleRowVO;

import java.util.List;

/**
 * 子角色导入的落库逻辑（厂商 `SubRoleServiceImpl.handleImportExcel:521-533` +
 * `importSubroleExcel:583-625`）。
 *
 * <h3>逐行判定顺序（照抄，含一处死代码）</h3>
 * <ol>
 *   <li>cell0（名称）读不到 ⇒ 400 `subrole.name.is.null.or.file.format.error`；
 *       长度 &gt;128 或空 ⇒ 400 `subrole.name.is.illegal`；</li>
 *   <li>cell1（唯一标识）读不到 ⇒ 400 `subrole.key.is.null.or.format.error`；
 *       长度 &gt;36 或 &lt;1 ⇒ 400 `subrole.key.is.illegal`；</li>
 *   <li>`isEmpty(key) || isEmpty(name)` ⇒ 400 `subrole.import.key.not.valid`（**不可达**，照抄）；</li>
 *   <li>`existKeyName(key, name)`（**查的是 `g_role` 战位表**，厂商如此）命中：
 *       `isCover=false` ⇒ 整行跳过；`true` ⇒ 继续走"更新描述 + 重写关联"；</li>
 *   <li>未命中 ⇒ 走 {@link ISubRoleCommandService#addSubRole} 新建（`userList` 传空）；</li>
 *   <li>按 `key` 反查子角色，查不到 ⇒ 400 `subrole.import.key.not.valid`；</li>
 *   <li>写回 `desc`（表格读不到就写 **NULL**）；</li>
 *   <li>用户列非空 ⇒ 替换该子角色的用户关联（用户名 → 用户 id，不存在的跳过）；</li>
 *   <li>战位列非空 ⇒ 替换该子角色的"子角色-战位"关联（战位 key → id，不存在的跳过）；</li>
 *   <li>写两个自动名（`i18n.sub_role.en_US.<key>` / `fr_FR`）——值为 null 时**不写**。</li>
 * </ol>
 *
 * <h3>与厂商的两处刻意偏离（见 `docs/deviations.md`）</h3>
 * <ul>
 *   <li><b>B27</b>：第 8 步不照抄"把每个用户的 `created_at` 覆盖成 now"（厂商写错字段）；</li>
 *   <li><b>B28</b>：第 9 步按**意图**删 `g_sub_role_role`——厂商那段 `delete` 写错了表
 *       （删的是 `g_user_sub_role`），会把第 8 步刚写好的用户关联清掉。</li>
 * </ul>
 * 另：厂商 `handleImportExcel` **没有事务**（`importSubroleExcel` 是私有方法），照抄。
 */
public interface ISubRoleImportService {

    /**
     * @param rows    解析好的行（null/空 ⇒ 什么都不做，对应厂商"表格无内容直接返回成功"）
     * @param isCover true = 命中 key/name 时继续覆盖；false = 跳过该行
     */
    void importSubRoles(List<ImportSubRoleRowVO> rows, boolean isCover);
}
