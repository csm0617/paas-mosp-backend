package com.csm.paas.core.domain.role.service;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;

import java.util.List;

/**
 * 战位写操作的前置校验（厂商把这两条规则写在 `RoleService.deleteRoleRelation` 里，我们把它收回领域层）。
 * <p>规则（逐字照抄厂商 `RoleService.java:711-719`）：
 * <ol>
 *   <li>id 不存在 → `role.missing.storage`；</li>
 *   <li>`key` 是 `admin`/`dev` → `role.cannot.delete.storage`（内置战位不可删）。</li>
 * </ol>
 * <p><b>厂商的 `cicd`/`root` 保留字只声明未使用</b>（`RoleConstant` 里零引用），所以那两个 key 实际**可被删除**；
 * 重写版照抄该行为而不是"顺手修好"——要改必须先挑战本判断（见 docs/findings/22 §1.8 的 R5）。
 */
public interface IRoleCommandService {

    /**
     * 按厂商顺序逐条校验，返回可删除的战位实体（顺序与入参一致）。
     * <p>任何一条不合法都抛 {@code WrongArgumentException}（由 Trigger 翻译成 HTTP 400 + i18n 文案）。
     */
    List<RoleEntity> validateDeletable(List<String> ids);
}
