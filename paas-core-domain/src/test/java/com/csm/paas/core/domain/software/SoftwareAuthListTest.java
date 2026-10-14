package com.csm.paas.core.domain.software;

import com.csm.paas.core.domain.software.adapter.port.IWebEntryStatusPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareAuthInfoVO;
import com.csm.paas.core.domain.software.service.impl.SoftwareQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `GET /v1/software/auth` 的三态判定（厂商 `SoftwareService.getSoftwareAuthList:3298-3312`）。
 *
 * <p>三态（**实测对拉确认**）：
 * <ul>
 *   <li>`active_code` 空白 → **0**，且两个时间字段都置 null；</li>
 *   <li>`active_code` 非空：`activeExpire != null && now > activeExpire` → **2**；</li>
 *   <li>`activeExpire == null || == 0` → 0；其余 → **1**。</li>
 * </ul>
 * <b>注意</b>：`activeExpire == 0` 实际落到 **2**（不是 0）——因为第一条分支先命中
 * （`0 != null && now > 0`）。那个 `== 0` 分支在 now>0 时**不可达**。这是实测对拉确认的。
 */
class SoftwareAuthListTest {

    private final ISoftwareRepository repository = mock(ISoftwareRepository.class);
    private final IWebEntryStatusPort webEntryStatusPort = mock(IWebEntryStatusPort.class);

    private SoftwareQueryServiceImpl service() {
        return new SoftwareQueryServiceImpl(repository, webEntryStatusPort);
    }

    private SoftwareAuthInfoVO only(String activeCode, Long expire) {
        when(repository.queryAuthPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                SoftwareEntity.builder().id("s1").key("k1").name("n1").framework("springboot")
                        .activeCode(activeCode).activeCreate(11L).activeExpire(expire).build()));
        return service().queryAuthList(1, 20, null, null).get(0);
    }

    @Test
    void blankActiveCodeIsUnauthorizedWithNullTimes() {
        SoftwareAuthInfoVO info = only(null, null);
        assertEquals(0, info.getStatus());
        assertNull(info.getAuthCreateTime(), "未授权时两个时间字段都置 null（即使库里 active_create 有值）");
        assertNull(info.getAuthExpireTime());
    }

    @Test
    void futureExpireIsAuthorized() {
        SoftwareAuthInfoVO info = only("code", System.currentTimeMillis() + 3600_000L);
        assertEquals(1, info.getStatus());
        assertEquals(11L, info.getAuthCreateTime(), "非空 activeCode 时时间字段原样取");
    }

    @Test
    void pastExpireIsExpired() {
        assertEquals(2, only("code", 1000L).getStatus());
    }

    /** 反直觉但实测如此：`expire = 0` → **2**（第一条分支先命中），不是 0。 */
    @Test
    void zeroExpireFallsIntoExpiredBranch() {
        assertEquals(2, only("code", 0L).getStatus(), "0 != null && now > 0 ⇒ 已过期分支先命中");
    }
}
