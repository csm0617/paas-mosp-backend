package com.csm.paas.core.domain.node.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.csm.paas.core.domain.cluster.adapter.port.IPubClusterPort;
import com.csm.paas.core.domain.dynamicloading.adapter.repository.IAuthInfoRepository;
import com.csm.paas.core.domain.dynamicloading.model.entity.AuthInfoEntity;
import com.csm.paas.core.domain.node.adapter.port.IPubAuthWritePort;
import com.csm.paas.core.domain.node.model.valobj.PubAuthingResultVO;
import com.csm.paas.core.domain.node.service.IPubAuthingService;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * `POST /v1/pub/authing`（厂商 `PublicController.authing:1961-2027` + `AuthService.authing:210-230` +
 * `AuthService.getPublicKeyAlgorithm:313-325`）。
 *
 * <p>照抄点：
 * <ol>
 *   <li>验签算法固定 **RSA512**（`Algorithm.RSA512(pubKey, null)`，`AuthService:319`），issuer 固定 `"gaop"`；</li>
 *   <li>`serviceIdList` 为空 ⇒ 400 `auth.missing.service.id.signature`；</li>
 *   <li>upsert 的"存在就 update、否则 insert"里，厂商那句 `serviceAuth1 != null` 是**多余的**
 *       （MyBatis-dynamic-sql 的 `selectByPrimaryKey` 从不返回 null），但语义照抄：未命中就 insert；</li>
 *   <li>`if (authingResultList != null || !authingResultList.isEmpty())` 的 **`||` 恒真**（`PublicController:1998`），
 *       所以重启逻辑总是执行；</li>
 *   <li>"服务在跑"的判定是**五个 replica 读数不全是 null**（`PublicController:2008`）——
 *       全是 null 就 `continue`（不加进重启清单，也不算错）；</li>
 *   <li>重启异常统一转成 400 `service.restart.failed`（`PublicController:2019-2022`）。</li>
 * </ol>
 *
 * <p><b>类 C：本环境不可验证</b>——重启依赖集群工作负载；在线只验证"坏 token 的 400 文案"，
 * 不拿真实服务做重启实验。
 */
@Slf4j
@Service
public class PubAuthingServiceImpl implements IPubAuthingService {

    /** 厂商 `PublicController:1966` `defaultAuth = "default_auth"`。 */
    private static final String DEFAULT_AUTH = "default_auth";

    /** 厂商 `PublicController:292` `SERVICE_ID_LIST = "serviceIdList"`。 */
    private static final String SERVICE_ID_LIST = "serviceIdList";

    /** 厂商 `PublicController:1973` `.withIssuer("gaop")`。 */
    private static final String JWT_ISSUER = "gaop";

    /** 厂商 `PublicController:1981` `jwt.getClaim("gaop")`。 */
    private static final String CLAIM_GAOP = "gaop";

    /** 厂商 `AuthType.onceAuth.type()`（`constant/AuthType.java`）。 */
    private static final Integer AUTH_TYPE_ONCE = 1;

    /** 厂商 `AuthServiceType.TOBEAUTHORIZED`（`constant/AuthServiceType.java`）。 */
    private static final Integer AUTH_TOBEAUTHORIZED = 3;

    /** 厂商 `K8s.NAMESPACE`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @Resource
    private IAuthInfoRepository authInfoRepository;

    @Resource
    private IPubAuthWritePort pubAuthWritePort;

    @Resource
    private IPubClusterPort pubClusterPort;

    /** 厂商 `tenxCloud.restartService` 的替代实现（同集群滚动重启）。 */
    @Resource
    private IServiceQueryService serviceQueryService;

    @Override
    public List<PubAuthingResultVO> authing(String authToken) {
        String cleanToken = authToken.replaceAll("\\s*|\t|\r|\n", "");
        Optional<AuthInfoEntity> select = authInfoRepository.queryLatestByJarName(DEFAULT_AUTH);
        if (select.isEmpty()) {
            throw new WrongArgumentException("auth.invalid.public.key.auth.code");
        }
        AuthInfoEntity authInfo = select.get();
        Algorithm algorithm = getPublicKeyAlgorithm(authInfo.getPublicKey());
        DecodedJWT jwt;
        try {
            JWTVerifier verifier = JWT.require(algorithm).withIssuer(JWT_ISSUER).build();
            jwt = verifier.verify(cleanToken);
        } catch (Exception e) {
            log.error("解析授权码出现异常:", e);
            throw new WrongArgumentException("auth.parse.authorization.code.error");
        }
        Date expiresAt = jwt.getExpiresAt();
        String authCode = jwt.getClaim(CLAIM_GAOP).asString();
        List<String> serviceIdList = jwt.getClaim(SERVICE_ID_LIST).asList(String.class);
        if (serviceIdList == null || serviceIdList.isEmpty()) {
            throw new WrongArgumentException("auth.missing.service.id.signature");
        }

        List<PubAuthingResultVO> authingResultList = new ArrayList<>();
        for (String serviceId : serviceIdList) {
            PubAuthingResultVO authingResult = new PubAuthingResultVO();
            authingResult.setServiceId(serviceId);
            boolean result = pubAuthWritePort.upsertServiceAuth(serviceId, AUTH_TOBEAUTHORIZED, AUTH_TYPE_ONCE,
                    expiresAt.getTime(), authCode);
            authingResult.setResult(result);
            authingResultList.add(authingResult);
        }

        // 照抄：`if (authingResultList != null || !authingResultList.isEmpty())` —— 左边恒真
        List<String> collect = authingResultList.stream().map(PubAuthingResultVO::getServiceId).toList();
        List<String> runningServiceNames = new ArrayList<>();
        for (String name : collect) {
            Map<String, Object> deployment = pubClusterPort.readDeploymentSnapshot(namespace, name);
            if (deployment == null) {
                log.error("获取该Deployment失败: {}", name);
                continue;
            }
            Map<String, Object> status = asMap(deployment.get("status"));
            // 照抄：五个体读数全为 null ⇒ 视为未运行（继续下一条）
            if (status.get("replicas") == null && status.get("updatedReplicas") == null
                    && status.get("readyReplicas") == null && status.get("availableReplicas") == null
                    && status.get("unavailableReplicas") == null) {
                continue;
            }
            runningServiceNames.add(name);
        }
        try {
            serviceQueryService.batchRestartServices(namespace, runningServiceNames);
        } catch (Exception e) {
            log.error("{}", e.getMessage());
            throw new WrongArgumentException("service.restart.failed");
        }
        return authingResultList;
    }

    /** 厂商 `AuthService.getPublicKeyAlgorithm:313-325`：base64 X.509 公钥 ⇒ `Algorithm.RSA512`。 */
    private static Algorithm getPublicKeyAlgorithm(String publicKey) {
        try {
            log.info("获取到的授权服务的公钥为：" + publicKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey));
            RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpecX509);
            return Algorithm.RSA512(pubKey, null);
        } catch (Exception e) {
            log.error("解密授权服务公钥失败:", e);
            throw new WrongArgumentException("auth.get.auth.activate.public.key.failed");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }
}
