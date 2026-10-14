package com.csm.paas.core.domain.user.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户导入 Excel 的**一行**（已由 trigger 层用 POI + Jackson 解析成纯值对象）。
 *
 * <p><b>为什么解析在 trigger</b>：domain 层没有 POI、也没有 JSON 库（硬约束），
 * 而"哪个单元格不存在"这件事**本身带语义**（见下），所以由 trigger 把
 * "单元格读不到"翻译成 {@code null}，domain 只按厂商的判定顺序处理。
 *
 * <p><b>{@code null} 的含义（照抄厂商，不能简化成"空串"）</b>：
 * <ul>
 *   <li>{@code username}（col0）：{@code null} = 单元格不存在或读取异常。厂商此时 {@code StringUtils.isEmpty} 成立 ⇒ **跳过该行**（不报错）；</li>
 *   <li>{@code nickname}（col1）：{@code null} = 单元格不存在 ⇒ 新用户分支抛 400 {@code user.nickname.empty}；
 *       **空串 {@code ""} 不是 null** ⇒ 昵称就写成空串；覆盖分支 {@code null} ⇒ 保持原值；</li>
 *   <li>{@code statusText}（col6）：{@code null} = 单元格不存在 ⇒ 抛 400 {@code user.import.status.must.not.null}；空串 ⇒ 不设状态、不报错；</li>
 *   <li>{@code password}（col7）：{@code null} = 单元格不存在。新用户 ⇒ 400 {@code user.password.empty}；
 *       覆盖分支 ⇒ 回落到默认口令 {@code "123456"}；</li>
 *   <li>{@code desc}（col5）：{@code null} = 单元格不存在 ⇒ 不设（新用户为 null、覆盖为"保持原值"）；</li>
 *   <li>{@code roleKey}（col2）：{@code null} = 单元格不存在（覆盖分支据此 {@code return}）；空串保持空串
 *       （覆盖分支会因此走 {@code else} 写 {@code user} 角色——厂商的怪行为，见 {@code findings/100}）；</li>
 *   <li>{@code userToRole}（col3）/ {@code subRoles}（col4）：JSON 数组解析结果，{@code null} = 单元格不存在、
 *       为空串、或字面量 {@code null}。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportUserRowVO {

    /** col0：用户名（未做任何 strip——厂商也没做）。 */
    private String username;

    /** col1：昵称。 */
    private String nickname;

    /** col2：战位 key（该行的"主角色"）。 */
    private String roleKey;

    /** col3：额外的战位 key 列表（JSON 数组）。 */
    private List<String> userToRole;

    /** col4：子角色 key 列表（JSON 数组）。 */
    private List<String> subRoles;

    /** col5：描述。 */
    private String desc;

    /** col6：状态文本（原样字符串；厂商先按字符串读再校验 {@code [01]}）。 */
    private String statusText;

    /** col7：口令原文（可能是 BCrypt 摘要，厂商以 {@code $2a$} 前缀判定）。 */
    private String password;
}
