package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.SoftwareCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/software/order/{orderNum}`（厂商 `SoftwareService.orderSoftWare:3816-3832`）。
 * <p>规格见 `docs/findings/131`。重点：空/null 列表 ⇒ `false`；逐个写 `order_top`+`order_time`；
 * **某个 id 不存在 ⇒ 400（带 id）且前面的 id 已经写进去了**（边遍历边写，照抄）。
 */
class SoftwareOrderTest {

    private final ISoftwareRepository softwareRepository = mock(ISoftwareRepository.class);
    private final com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort relationBroadcastPort =
            mock(com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort.class);
    private final com.csm.paas.core.domain.role.adapter.repository.IRoleRepository roleRepository =
            mock(com.csm.paas.core.domain.role.adapter.repository.IRoleRepository.class);

    private SoftwareCommandServiceImpl service() {
        return new SoftwareCommandServiceImpl(softwareRepository, relationBroadcastPort, roleRepository, mock(com.csm.paas.core.domain.licence.service.ILicenceQueryService.class), mock(com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort.class));
    }

    private void exists(String id) {
        when(softwareRepository.queryById(id)).thenReturn(Optional.of(SoftwareEntity.builder().id(id).build()));
    }

    @Test
    void emptyOrNullListReturnsFalseWithoutTouchingDb() {
        assertFalse(service().orderSoftware(List.of(), 1));
        assertFalse(service().orderSoftware(null, 1));
        verify(softwareRepository, never()).updateSoftwareOrderTop(anyString(), anyInt(), anyLong());
    }

    @Test
    void eachIdGetsOrderTopAndOrderTime() {
        exists("a");
        exists("b");

        assertTrue(service().orderSoftware(List.of("a", "b"), 5));

        verify(softwareRepository).updateSoftwareOrderTop(eq("a"), eq(5), anyLong());
        verify(softwareRepository).updateSoftwareOrderTop(eq("b"), eq(5), anyLong());
    }

    @Test
    void orderNumCanBeNull() {
        exists("a");

        assertTrue(service().orderSoftware(List.of("a"), null));
        verify(softwareRepository).updateSoftwareOrderTop(eq("a"), eq(null), anyLong());
    }

    @Test
    void missingIdIsRejectedWithTheIdAsArgument() {
        exists("a");
        when(softwareRepository.queryById("ghost")).thenReturn(Optional.empty());

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().orderSoftware(List.of("a", "ghost"), 3));
        assertEquals("software.id.not.found", e.getI18nKey());
        assertEquals("ghost", e.getArgs()[0]);
        // 厂商是边遍历边写 ⇒ 前面的 a **已经写进去了**
        verify(softwareRepository).updateSoftwareOrderTop(eq("a"), eq(3), anyLong());
        verify(softwareRepository, never()).updateSoftwareOrderTop(eq("ghost"), anyInt(), anyLong());
    }

    @Test
    void writesHappenInOrder() {
        exists("a");
        exists("b");
        service().orderSoftware(List.of("a", "b"), 1);

        InOrder order = inOrder(softwareRepository);
        order.verify(softwareRepository).queryById("a");
        order.verify(softwareRepository).updateSoftwareOrderTop(eq("a"), anyInt(), anyLong());
        order.verify(softwareRepository).queryById("b");
        order.verify(softwareRepository).updateSoftwareOrderTop(eq("b"), anyInt(), anyLong());
    }
}
