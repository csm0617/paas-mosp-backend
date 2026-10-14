package com.csm.paas.core.domain.sync.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对端拉取的结果（把厂商 `getBeforeSync*` 的两条分支合并成一个值对象）：
 * <ul>
 *   <li>厂商在 **异常** 时 `setSuccess(false)/setSyncing(false)` 后抛对应 i18n 键；</li>
 *   <li>在 **非 2xx**（无异常）时返回空列表继续——但厂商用 `RestTemplate`，非 2xx 默认就抛异常，
 *       所以那条分支实际不可达。</li>
 * </ul>
 * 因此 `ok=false` 统一表示"这次拉取失败"，域层据此抛厂商那个键。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncFetchResultVO {

    /** 请求是否成功（2xx 且信封可解析）。 */
    private boolean ok;

    /** 信封里的 `data` 节点（可能是 List / Map / String / null）。 */
    private Object data;

    public static SyncFetchResultVO failed() {
        return SyncFetchResultVO.builder().ok(false).data(null).build();
    }

    public static SyncFetchResultVO of(Object data) {
        return SyncFetchResultVO.builder().ok(true).data(data).build();
    }
}
