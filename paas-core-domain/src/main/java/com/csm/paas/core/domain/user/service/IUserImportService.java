package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.valobj.ImportUserRowVO;

import java.util.List;

/**
 * 用户导入（厂商 {@code POST /v1/user/import} → {@code UserService.handleImportExcel(file, false, isCover)}
 * → {@code importExcel}，`UserService.java:1029-1233`）。
 *
 * <p>规格（逐条照抄，出处与"坑"见 {@code docs/findings/100-k1-file-endpoints-spec.md}）：
 * <ol>
 *   <li>逐行：col0 空 / {@code admin} / {@code root} ⇒ 跳过；已存在 + 不覆盖 ⇒ 跳过；</li>
 *   <li>新用户：用户名长度 ∉[2,36] ⇒ 400；col6 缺失 ⇒ 400 {@code user.import.status.must.not.null}，
 *       非 {@code [01]} ⇒ 400 {@code user.import.status.error}；col7 缺失或空白 ⇒ 400 {@code user.password.empty}，
 *       长度 &lt;6 ⇒ 跳过该行；col1 缺失 ⇒ 400 {@code user.nickname.empty}；desc &gt;255 ⇒ 400；</li>
 *   <li>新用户落库：{@code id=UUID}、{@code createdAt=currentTime}、{@code avatar=默认头像}、口令 BCrypt；</li>
 *   <li>角色关系：{@code roleKey} 为空 ⇒ **角色与子角色都不写**；{@code admin}/{@code dev} ⇒ 只写自身；
 *       否则 ⇒ 写 {@code user} **再加** col3 的每个 key（查不到 {@code g_role} 就跳过）；</li>
 *   <li>子角色关系：写 col4 的每个 key（查不到 {@code g_sub_role} 就跳过），id 用随机 UUID；</li>
 *   <li>已存在 + 覆盖 ⇒ {@code existUpdateUser}：先按 username 删光 {@code g_user_role}，再更新用户
 *       （{@code desc}/{@code nickname} 为 {@code null} 时保持原值），再删光 {@code g_user_sub_role} 并重写，
 *       最后写角色关系（col2 为 {@code null} 才跳过角色，**空串会走 else 写 {@code user}**）。</li>
 * </ol>
 *
 * <p><b>不加事务，且覆盖更新在"解析阶段"就落库</b>——厂商就是这样的（`importExcel` 里
 * 先循环、循环中遇到已存在用户就立刻 {@code existUpdateUser}，新用户收集到 {@code userList} 循环后才插）。
 * ⇒ 中途某行抛 400 时，前面几行的**覆盖更新已生效、新用户一个都没插**。这是**要照抄的行为**，不要"顺手"加
 * {@code @Transactional}（见 {@code findings/100} 第 (5) 条）。
 */
public interface IUserImportService {

    /**
     * @param rows    trigger 解析出的行（顺序即 Excel 行序，从第 1 行开始）
     * @param isCover 已存在的用户名是否覆盖（厂商的 {@code isCover} 请求参数）
     */
    void importUsers(List<ImportUserRowVO> rows, boolean isCover);
}
