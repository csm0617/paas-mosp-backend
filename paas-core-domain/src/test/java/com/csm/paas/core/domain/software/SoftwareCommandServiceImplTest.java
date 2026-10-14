package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;
import com.csm.paas.core.domain.software.service.impl.SoftwareCommandServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新增软件的校验（离线）。规则来源：`SoftwareController.java:169-182` 与 `SoftwareService.java:607-693`。
 * <p>三类"照抄不修好"的点都锁在下面：保留字只挡 `api`、key 首字符规则、以及 **`routerKey` 被写死成 `/<key>/`**
 * （表单里的 `routerKey` 不生效）。
 */
class SoftwareCommandServiceImplTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort relationBroadcastPort =
            mock(com.csm.paas.core.domain.software.adapter.port.ISoftwareRelationBroadcastPort.class);
    private final com.csm.paas.core.domain.role.adapter.repository.IRoleRepository roleRepository =
            mock(com.csm.paas.core.domain.role.adapter.repository.IRoleRepository.class);

    private SoftwareCommandServiceImpl service() {
        return new SoftwareCommandServiceImpl(repository, relationBroadcastPort, roleRepository);
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

    // ---------- releaseSoftware（放行 / 禁止）----------

    @Test
    void emptyListIsTrueNoop() {
        assertTrue(service().releaseSoftware(1, List.of()), "厂商：list 为空直接 true");
        assertTrue(service().releaseSoftware(0, List.of()));
    }

    @Test
    void typeOneInsertsWhiteListPathForEveryExistingSoftware() {
        when(repository.queryById("s1")).thenReturn(java.util.Optional.of(soft("s1", "appA")));
        when(repository.queryById("s2")).thenReturn(java.util.Optional.empty());

        assertTrue(service().releaseSoftware(1, List.of("s1", "s2")));

        verify(repository).insertWhiteListPath(eq("/appA/**"), org.mockito.ArgumentMatchers.anyLong());
        verify(repository, org.mockito.Mockito.never()).insertWhiteListPath(eq("/appB/**"), org.mockito.ArgumentMatchers.anyLong());
    }

    /** type=0 只看**第一个存在**的软件：删到就 true、删不到就 false，都不继续往后看。 */
    @Test
    void typeZeroReturnsOnTheFirstExistingSoftware() {
        when(repository.queryById("s1")).thenReturn(java.util.Optional.of(soft("s1", "appA")));
        when(repository.deleteWhiteListPath("/appA/**")).thenReturn(1);
        assertTrue(service().releaseSoftware(0, List.of("s1")));

        when(repository.deleteWhiteListPath("/appA/**")).thenReturn(0);
        assertFalse(service().releaseSoftware(0, List.of("s1")));
    }

    @Test
    void otherTypeValuesAreNoopTrue() {
        assertTrue(service().releaseSoftware(9, List.of("s1")));
        assertTrue(service().releaseSoftware(null, List.of("s1")));
    }

    private static SoftwareEntity soft(String id, String key) {
        return SoftwareEntity.builder().id(id).key(key).name("名字").type("WEB").build();
    }

    // ---------- prepareUpdatedSoftware（修改）----------

    /** 修改时 desc 传空白 → 落库 null；而**新增时同样的空白会变成空串**（两处相反，都照抄）。 */
    @Test
    void updateBlankDescBecomesNullWhileAddKeepsEmptyString() {
        when(repository.queryById("s1")).thenReturn(java.util.Optional.of(
                SoftwareEntity.builder().id("s1").key("myapp").name("旧名字").routerKey("/myapp/").build()));

        SoftwareEntity updated = service().prepareUpdatedSoftware("s1",
                SoftwareFormVO.builder().key("myapp").name("新名字").desc("").build());
        assertNull(updated.getDesc(), "修改：空白 → null");

        when(repository.existsKey("newapp")).thenReturn(false);
        when(repository.existsName("新应用")).thenReturn(false);
        SoftwareEntity created = service().prepareNewSoftware(
                SoftwareFormVO.builder().key("newapp").name("新应用").desc("").build());
        assertEquals("", created.getDesc(), "新增：空白 → 空串（厂商那个 setDesc(null) 被返回值覆盖了）");
    }

    /** 表单没带的字段会被置成 null（厂商 `BeanUtils.copyProperties` 连 null 一起拷）。 */
    @Test
    void updateNullsOutFieldsTheFormDoesNotCarry() {
        when(repository.queryById("s1")).thenReturn(java.util.Optional.of(
                SoftwareEntity.builder().id("s1").key("myapp").name("旧名字")
                        .routerKey("/myapp/").location("旧地点").icon("旧图标").build()));

        SoftwareEntity updated = service().prepareUpdatedSoftware("s1",
                SoftwareFormVO.builder().key("myapp").name("新名字").build());

        assertNull(updated.getRouterKey(), "routerKey 没带 → 被置 null（不是保持原值）");
        assertNull(updated.getLocation());
        assertNull(updated.getIcon());
        assertEquals("/myapp/", SoftwareEntity.builder().id("s1").routerKey("/myapp/").build().getRouterKey(),
                "（对照：实体本身能持有 routerKey）");
    }

    @Test
    void updateRejectsDuplicateKeyAndNameWithTheSameI18nKey() {
        when(repository.queryById("s1")).thenReturn(java.util.Optional.of(
                SoftwareEntity.builder().id("s1").key("myapp").name("旧名字").build()));
        when(repository.existsByKeyExcludingId("taken", "s1")).thenReturn(true);
        assertEquals("software.key.identifier.existed", key(() -> service().prepareUpdatedSoftware("s1",
                SoftwareFormVO.builder().key("taken").name("新名字").build())));

        when(repository.existsByKeyExcludingId("myapp", "s1")).thenReturn(false);
        when(repository.existsByNameExcludingId("占用的", "s1")).thenReturn(true);
        assertEquals("software.key.identifier.existed", key(() -> service().prepareUpdatedSoftware("s1",
                SoftwareFormVO.builder().key("myapp").name("占用的").build())),
                "厂商 name 重复时用的**也是** software.key.identifier.existed");
    }

    @Test
    void updateRequiresExistingRow() {
        when(repository.queryById("nope")).thenReturn(java.util.Optional.empty());
        assertEquals("software.not.exist.by.id", key(() -> service().prepareUpdatedSoftware("nope",
                SoftwareFormVO.builder().key("myapp").name("新名字").build())));
    }
}
