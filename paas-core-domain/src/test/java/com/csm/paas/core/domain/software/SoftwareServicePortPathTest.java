package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.valobj.ServicePortVO;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PUT /v1/software/{softwareId}/service/{serviceName}` 的校验顺序（厂商 `SoftwareController:466-483`
 * + `SoftwareService.updateSoftwareServicePort:2308-2322`）。**七种情形已全部对拉一致**（除 trace/id）。
 */
class SoftwareServicePortPathTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);

    private SoftwareQueryServiceImpl service() {
        return new SoftwareQueryServiceImpl(repository, webEntryStatusPort,
                mock(com.csm.paas.core.domain.licence.service.ILicenceQueryService.class));
    }

    private String key(Runnable action) {
        return assertThrows(WrongArgumentException.class, action::run).getI18nKey();
    }

    @Test
    void pathValidationComesBeforeAnyRepositoryCall() {
        assertEquals("software.path.must.not.null", key(() -> service().updateServicePortPath("s", "svc", 1, "  ")));
        assertEquals("software.path.length.invalid", key(() -> service().updateServicePortPath("s", "svc", 1, "ab")));
        assertEquals("software.path.length.invalid",
                key(() -> service().updateServicePortPath("s", "svc", 1, "x".repeat(46))));
        assertEquals("software.path.can.not.start.by.num",
                key(() -> service().updateServicePortPath("s", "svc", 1, "1abc")));
        assertEquals("software.path.must.in.char", key(() -> service().updateServicePortPath("s", "svc", 1, "ABC")));
        verify(repository, never()).existsServicePath(any(), any(), any());
    }

    @Test
    void pathCollisionUsesServiceNameAsArgument() {
        when(repository.existsServicePath("s", "svc", "taken")).thenReturn(true);

        assertEquals("software.path.exist.in.service",
                key(() -> service().updateServicePortPath("s", "svc", 8080, "taken")));
        verify(repository, never()).updateServicePortPath(anyLong(), any());
    }

    @Test
    void missingPortUsesPortAsArgument() {
        when(repository.existsServicePath("s", "svc", "newpath")).thenReturn(false);
        when(repository.queryServicePort("s", "svc", 7070)).thenReturn(null);

        assertEquals("service.port.not.found",
                key(() -> service().updateServicePortPath("s", "svc", 7070, "newpath")));
    }

    @Test
    void successUpdatesByIdAndReturnsRefetchedRow() {
        when(repository.existsServicePath("s", "svc", "newpath")).thenReturn(false);
        when(repository.queryServicePort("s", "svc", 8080))
                .thenReturn(ServicePortVO.builder().id(9L).pathName("oldpath").build());
        when(repository.updateServicePortPath(9L, "newpath"))
                .thenReturn(ServicePortVO.builder().id(9L).pathName("newpath").build());

        var result = service().updateServicePortPath("s", "svc", 8080, " newpath ");

        assertEquals("newpath", result.getPathName(), "path 会先 trim");
        verify(repository).updateServicePortPath(9L, "newpath");
    }
}
