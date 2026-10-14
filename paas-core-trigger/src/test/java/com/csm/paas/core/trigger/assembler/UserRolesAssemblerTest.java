package com.csm.paas.core.trigger.assembler;

import com.csm.paas.core.domain.user.model.entity.UserRolesEntity;
import com.csm.paas.core.domain.user.model.entity.UserRolesPageEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `GET /v1/user/list` 的装配契约（`findings/98`/`99`）。
 * <p>**两处最容易写错**：`nickname` 必须**留空**（厂商 `getUserRoles` 没 setNickname）；
 * `subRoles` 空时必须序列化成 **`[]`**（不是 null）——所以域实体里空集合、装配器兜底成 `List.of()`。
 */
class UserRolesAssemblerTest {

    @Test
    void nicknameStaysNullAndEmptySubRolesIsEmptyList() {
        var entity = UserRolesEntity.builder()
                .id("u1").username("admin").status(1)
                .roles(List.of()).subRoles(List.of()).admin(true)
                .build();

        var dto = UserRolesAssembler.toResponse(
                UserRolesPageEntity.builder().list(List.of(entity)).total(1).build()).getList().get(0);

        assertNull(dto.getNickname(), "厂商不设 nickname ⇒ 必须留空");
        assertTrue(dto.getSubRoles().isEmpty(), "空集合必须是 []，不能是 null");
        assertTrue(dto.getRoles().isEmpty());
        assertEquals(1L, UserRolesAssembler.toResponse(
                UserRolesPageEntity.builder().list(List.of(entity)).total(1).build()).getTotal());
    }
}
