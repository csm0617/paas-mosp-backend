package com.csm.paas.core.domain.node.service;

import com.csm.paas.core.domain.node.model.valobj.PubAuthingResultVO;

import java.util.List;

/**
 * 公共 API - 进行授权认证（厂商 `controller/pub/PublicController.authing:1961-2027`）。
 *
 * <p>放在 `domain/node` 下同 {@code IPubServiceQueryService} 的理由（文件白名单）。
 *
 * <p><b>类 C：本环境不可验证</b>——流程末尾的"重启已授权服务"厂商走
 * `tenxCloud.restartService(clusterID, startServiceForm)`（TCE 开放 API，不可达）；
 * 本平台改用既有的 `IServiceQueryService.batchRestartServices`（同一批服务、同样的滚动重启语义）。
 */
public interface IPubAuthingService {

    /**
     * 厂商 `PublicController.authing:1964-2026` 的逐行语义：
     * <ol>
     *   <li>`authToken.replaceAll("\\s*|\t|\r|\n","")` 去空白；</li>
     *   <li>取 `g_auth` 里 `jar_name = 'default_auth'` 且 `create_at` 最大的一条；
     *       **取不到 ⇒ 400 `auth.invalid.public.key.auth.code`**；</li>
     *   <li>用该条的 `public_key`（base64 X.509）做 **RSA512** 验签，要求 `issuer = "gaop"`；
     *       验签失败 ⇒ 400 `auth.parse.authorization.code.error`；</li>
     *   <li>`expiresAt.getTime()` 当 `authTime`、claim `gaop` 当 `authCode`、
     *       claim `serviceIdList` 当服务清单；清单为空 ⇒ 400 `auth.missing.service.id.signature`；</li>
     *   <li>逐条 upsert `g_service_auth`（`auth=3` 待授权、`auth_type=1` 一次性、`created_at=now`），
     *       `result` 只在**影响行数 == 1** 时为 true；</li>
     *   <li>把"状态里 replica 读数不全是 null"的服务当成在跑，调重启；重启抛错 ⇒ 400 `service.restart.failed`。</li>
     * </ol>
     *
     * <p>照抄点：第 5 步后的判断是 `if (authingResultList != null || !authingResultList.isEmpty())`
     * ——**左边恒真**，所以重启那段永远会执行（`PublicController:1998`）。
     *
     * @param authToken 请求体里的 `authToken`
     */
    List<PubAuthingResultVO> authing(String authToken);
}
