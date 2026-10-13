package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;
import com.csm.paas.core.domain.software.service.impl.SoftwareCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 新增软件的校验（离线）。规则来源：`SoftwareController.java:169-182` 与 `SoftwareService.java:607-693`。
 * <p>三类"照抄不修好"的点都锁在下面：保留字只挡 `api`、key 首字符规则、以及 **`routerKey` 被写死成 `/<key>/`**
 * （表单里的 `routerKey` 不生效）。
 */
class SoftwareCommandServiceImplTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);

    private SoftwareCommandServiceImpl service() {
        return new SoftwareCommandServiceImpl(repository);
    }

    @Test
    void reservedWordIsOnlyApi() {
        assertEquals("software.key.identifier.reserved.word", key(() -> service().prepareNewSoftware(form("api", "应用"))));
        // 其它"像保留字"的 key 不受保护
        when(repository.existsKey("admin")).thenReturn(false);
        when(repository.existsName("应用")).thenReturn(false);
        assertEquals("admin", service().prepareNewSoftware(form("admin", "应用")).getKey());
    }

    @Test
    void duplicateKeyAndName() {
        when(repository.existsKey("dup")).thenReturn(true);
        assertEquals("software.key.identifier.existed", key(() -> service().prepareNewSoftware(form("dup", "应用"))));

        when(repository.existsKey("fresh")).thenReturn(false);
        when(repository.existsName("占用的名字")).thenReturn(true);
        assertEquals("software.name.existed", key(() -> service().prepareNewSoftware(form("fresh", "占用的名字"))));
    }

    @Test
    void nameRules() {
        when(repository.existsKey("ok")).thenReturn(false);
        when(repository.existsName("")).thenReturn(false);
        assertEquals("software.name.must.not.null", key(() -> service().prepareNewSoftware(form("ok", ""))));

        when(repository.existsName("a")).thenReturn(false);
        assertEquals("software.name.illegal", key(() -> service().prepareNewSoftware(form("ok", "a"))));
    }

    @Test
    void keyFirstCharRules() {
        when(repository.existsKey("1abc")).thenReturn(false);
        when(repository.existsName("应用")).thenReturn(false);
        assertEquals("software.key.identifier.illegal.not.number.start",
                key(() -> service().prepareNewSoftware(form("1abc", "应用"))));

        when(repository.existsKey("-abc")).thenReturn(false);
        assertEquals("software.key.identifier.illegal.not.dash.start",
                key(() -> service().prepareNewSoftware(form("-abc", "应用"))));

        when(repository.existsKey("Abc")).thenReturn(false);
        assertEquals("software.key.identifier.illegal.format",
                key(() -> service().prepareNewSoftware(form("Abc", "应用"))));

        when(repository.existsKey("a")).thenReturn(false);
        assertEquals("software.key.identifier.illegal.length",
                key(() -> service().prepareNewSoftware(form("a", "应用"))));
    }

    @Test
    void routerKeyIsRebuiltAndOrderTopZeroed() {
        when(repository.existsKey("myapp")).thenReturn(false);
        when(repository.existsName("我的应用")).thenReturn(false);

        SoftwareFormVO form = form("myapp", "我的应用");
        form.setRouterKey("/ignored/");
        SoftwareEntity created = service().prepareNewSoftware(form);

        assertEquals("/myapp/", created.getRouterKey(), "厂商把 routerKey 重写成 /<key>/（表单里的值不生效）");
        assertEquals(0, created.getOrderTop());
        assertNull(created.getUpdatedAt(), "厂商只设 createdAt");
        assertEquals(36, created.getId().length(), "id 是 UUID");
    }

    @Test
    void screenshotsAreJoinedByComma() {
        when(repository.existsKey("myapp")).thenReturn(false);
        when(repository.existsName("我的应用")).thenReturn(false);

        SoftwareFormVO form = form("myapp", "我的应用");
        form.setScreenShotList(List.of("a.png", "b.png"));
        assertEquals("a.png,b.png", service().prepareNewSoftware(form).getScreenshot());

        form.setScreenShotList(List.of());
        assertNull(service().prepareNewSoftware(form).getScreenshot(), "空列表 → null");
    }

    @Test
    void descRules() {
        when(repository.existsKey("myapp")).thenReturn(false);
        when(repository.existsName("我的应用")).thenReturn(false);

        SoftwareFormVO tooLong = form("myapp", "我的应用");
        tooLong.setDesc("x".repeat(256));
        assertEquals("software.desc.length.invalid", key(() -> service().prepareNewSoftware(tooLong)));
    }

    private String key(java.util.concurrent.Callable<SoftwareEntity> action) {
        return assertThrows(WrongArgumentException.class, action::call).getI18nKey();
    }

    private static SoftwareFormVO form(String key, String name) {
        return SoftwareFormVO.builder().key(key).name(name).type("WEB")
                .framework("springboot").build();
    }
}
