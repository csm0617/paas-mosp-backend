package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/software/cancelAuth`（厂商 `SoftwareService.cancelAuth:3187-3211`）。**五种情形已全部对拉一致**。
 * <p>两个照抄点：没有激活码的软件**跳过**（不产出结果项）；**一个都没取消到 → 400**
 * `auth.no.software.authorization`（不是空数组）。另外它**没有事务**——"先有效后不存在"时会先清空再抛错
 * （实测两侧库里都已被清空，照抄这个非原子行为）。
 */
class SoftwareCancelAuthTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);
    private final ILicenceQueryService licenceQueryService = mock(ILicenceQueryService.class);

    private SoftwareQueryServiceImpl service() {
        return new SoftwareQueryServiceImpl(repository, webEntryStatusPort, licenceQueryService);
    }

    private static SoftwareEntity software(String id, String key, String activeCode) {
        return SoftwareEntity.builder().id(id).key(key).activeCode(activeCode).build();
    }

    @Test
    void clearsAuthAndReturnsOneEntry() {
        when(repository.queryById("1")).thenReturn(Optional.of(software("1", "appA", "codeA")));

        var results = service().cancelSoftwareAuth(List.of("1"));

        verify(repository).clearSoftwareAuth(org.mockito.ArgumentMatchers.eq("1"), anyLong());
        assertEquals(1, results.size());
        assertEquals("appA", results.get(0).getSoftwareKey());
        assertEquals(false, results.get(0).isResult(), "取消授权的 result 恒为 false");
    }

    @Test
    void softwareWithoutActiveCodeIsSkippedAndEmptyResultIsAnError() {
        when(repository.queryById("2")).thenReturn(Optional.of(software("2", "appB", null)));

        assertEquals("auth.no.software.authorization",
                assertThrows(WrongArgumentException.class, () -> service().cancelSoftwareAuth(List.of("2")))
                        .getI18nKey());
        verify(repository, never()).clearSoftwareAuth(anyString(), anyLong());
    }

    @Test
    void mixedInputReturnsOnlyTheAuthorizedOnes() {
        when(repository.queryById("1")).thenReturn(Optional.of(software("1", "appA", "codeA")));
        when(repository.queryById("2")).thenReturn(Optional.of(software("2", "appB", "  ")));

        var results = service().cancelSoftwareAuth(List.of("1", "2"));

        assertEquals(1, results.size());
        assertEquals("appA", results.get(0).getSoftwareKey());
    }

    @Test
    void unknownIdThrowsImmediately() {
        when(repository.queryById("nope")).thenReturn(Optional.empty());

        assertEquals("software.not.exist.or.deleted",
                assertThrows(WrongArgumentException.class, () -> service().cancelSoftwareAuth(List.of("nope")))
                        .getI18nKey());
    }

    /** **没有事务**：前一个已被清空，随后遇到不存在的 id 抛错 —— 清空**不会回滚**（照抄厂商）。 */
    @Test
    void partialClearPersistsBeforeTheError() {
        when(repository.queryById("1")).thenReturn(Optional.of(software("1", "appA", "codeA")));
        when(repository.queryById("nope")).thenReturn(Optional.empty());

        assertThrows(WrongArgumentException.class, () -> service().cancelSoftwareAuth(List.of("1", "nope")));

        verify(repository).clearSoftwareAuth(org.mockito.ArgumentMatchers.eq("1"), anyLong());
    }
}
