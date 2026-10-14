package com.csm.paas.core.domain.licence.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.csm.paas.core.domain.licence.LicenceSets;
import com.csm.paas.core.domain.licence.model.valobj.MachineCodeVO;
import com.csm.paas.core.types.exception.WrongArgumentException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;
import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 授权信息。
 * <p>照抄厂商 `LicenceService.getLicenceInfo()` 的**未激活分支**：没有激活码时给试用期，
 * 首次调用会把 `g_setting['system.active.trial_period']` 落库为 now + 30 天，之后一直复用它。
 * <p>激活码校验（RSA、机器码派生）属另一域（gaop-auth），本实现只做 shape 与试用语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenceQueryServiceImpl implements ILicenceQueryService {

    static final String ACTIVE_CODE_KEY = "system.active.code";
    static final String TRIAL_PERIOD_KEY = "system.active.trial_period";
    static final long TRIAL_DAYS = 30L;

    private final ILicenceRepository licenceRepository;

    @Override
    public LicenceInfoEntity queryInfo() {
        long now = System.currentTimeMillis();
        String machineCode = machineCode();
        String activeCode = licenceRepository.queryActiveCode().orElse(null);

        // 厂商 getLicenceInfo:945-1020：有激活码就校验；**校验抛错也算 null**（catch 后继续走试用期那套）
        com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity licenceInfo = null;
        if (activeCode != null) {
            try {
                licenceInfo = checkActiveCode(machineCode, activeCode);
            } catch (Exception e) {
                log.error("检查激活信息出现异常:{}", e.getMessage(), e);
            }
        }

        long trialPeriod = resolveTrialPeriod(now);

        // 分支 1/2：没有激活码，或有激活码但校验没能拿到结果 → 试用期语义（authStatus=false）
        if (licenceInfo == null) {
            return LicenceInfoEntity.builder()
                    .end(-1L)
                    .endIso8601(null)
                    .code(machineCode)
                    .expired(true)
                    .authStatus(false)
                    .currentTime(now)
                    .trialPeriod(trialPeriod)
                    .build();
        }

        // 分支 3：激活码校验通过（本环境 tenxEnd 恒为空——TenxCloudImpl.licences() 返回的 merged 是 null，
        // 厂商那行 `merged.getLicenseUid()` 会 NPE 被空 catch 吞掉，所以永远走"用授权工具的时间"这一支）
        String endIso8601 = licenceInfo.getEndIso8601();
        int plus = endIso8601 == null ? -1 : endIso8601.indexOf('+');
        if (plus != -1) {
            endIso8601 = endIso8601.substring(0, plus);
        }
        long endTime = licenceInfo.getEnd();
        return LicenceInfoEntity.builder()
                .end(endTime)
                .endIso8601(endIso8601)
                .code(machineCode)
                .expired(licenceInfo.getExpired())
                // 厂商：`timeMillis > endTime` → authStatus=false，否则 true
                .authStatus(now <= endTime)
                .currentTime(now)
                .trialPeriod(trialPeriod)
                .build();
    }

    /** 厂商：已有 `g_setting['system.active.trial_period']` 就复用，否则建一条 now+30 天。 */
    private long resolveTrialPeriod(long now) {
        return licenceRepository.querySettingValue(TRIAL_PERIOD_KEY)
                .map(Long::parseLong)
                .orElseGet(() -> {
                    long expireAt = now + TRIAL_DAYS * 24 * 3600 * 1000L;
                    licenceRepository.saveSettingValue(TRIAL_PERIOD_KEY, String.valueOf(expireAt), "试用时间");
                    return expireAt;
                });
    }

    @Override
    public LicenceInfoEntity activate(String activeCode) {
        String stripped = strip(activeCode);
        // 厂商 LicenceController:54-58：**先校验，抛错就不落库**
        checkActiveCode(machineCode(), stripped);
        licenceRepository.saveActiveCode(stripped);
        // 未实现：厂商随后还会 activeTenxLicence（TenX 本地桩）/ activeGisLicence（GIS 未授权）
        return queryInfo();
    }

    @Override
    public LicenceInfoEntity deactivate() {
        licenceRepository.clearActiveCode();
        return queryInfo();
    }

    @Override
    public boolean checkAndActiveGis() {
        // 厂商在本环境返回 false（无 GIS 授权）；GIS 在线激活属 gaop-auth 域，未实现
        return false;
    }

    /** 厂商 `DEFAULT_GAOP = "gaop"`（`LicenceService.java:99`），以及平台 id 硬编码 `gaop`
     *  （`TenxCloudImpl.licencePlatform():539-543`）。 */
    private static final String DEFAULT_GAOP = "gaop";

    /** 厂商 `LicenceKey.ISSUER`。 */
    private static final String ISSUER = "gaop";

    @Override
    public String machineCode() {
        // 厂商 getMachineCodeBase64()：原始机器码 + "," + "gaop" + ","（**尾部多一个逗号**）
        return Base64.getEncoder().encodeToString(
                (licenceRepository.rawMachineCode() + "," + DEFAULT_GAOP + ",").getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String machineCodeForSoftware() {
        // 厂商 getMachineCodeBase64ByService()：原始机器码 + "," + "gaop"（**没有尾逗号**）
        return Base64.getEncoder().encodeToString(
                (licenceRepository.rawMachineCode() + "," + DEFAULT_GAOP).getBytes(StandardCharsets.US_ASCII));
    }

    // ---------- 激活码校验（K3 核心）----------

    @Override
    public LicenceInfoEntity checkActiveCode(String machineCodeBase64, String activeCode) {
        MachineCodeVO machine = MachineCodeVO.parse(machineCodeBase64);
        Set<String> gaopCodeSet = LicenceSets.parse(machine.getGaopCode());

        String publicKey = licenceRepository.queryGaopActivatePublicKey()
                .orElseThrow(() -> new WrongArgumentException("auth.get.activate.public.key.failed"));
        try {
            JWTVerifier verifier = JWT.require(rsa512(publicKey)).withIssuer(ISSUER).build();
            DecodedJWT jwt = verifier.verify(strip(activeCode));
            Set<String> machineSet = LicenceSets.parse(jwt.getClaim("machineCode").asString());
            if (!LicenceSets.hasIntersection(gaopCodeSet, machineSet)) {
                throw new WrongArgumentException("auth.invalid.auth.machine.id");
            }
            return toLicenceInfo(jwt.getExpiresAt(), machineCodeBase64);
        } catch (SignatureVerificationException e) {
            throw new WrongArgumentException("licence.invalid.key.signature");
        } catch (TokenExpiredException e) {
            throw new WrongArgumentException("licence.activation.code.expired");
        } catch (InvalidClaimException e) {
            throw new WrongArgumentException("licence.invalid.args", e.getMessage());
        } catch (WrongArgumentException e) {
            throw e;   // 上面自己抛的"无交集"，不要被下面的兜底吞掉
        } catch (Exception e) {
            log.error("激活码错误", e);
            throw new WrongArgumentException(e.getMessage());
        }
    }

    @Override
    public LicenceInfoEntity checkSoftwareActiveCode(String machineCodeBase64, String activeCode, String softwareKey) {
        String publicKey = licenceRepository.queryDefaultAuthPublicKey().orElse(null);
        if (publicKey == null) {
            // 厂商是 select.get(0) 直接 IndexOutOfBounds（缺陷）；我们按"授权软件公钥缺失"处理，交给上层记成未激活
            log.warn("g_auth_info 里没有 default_auth 行，软件授权码无法校验");
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(rsa512(publicKey)).withIssuer(ISSUER).build();
            DecodedJWT jwt = verifier.verify(strip(activeCode));
            List<String> serviceIdList = jwt.getClaim("serviceIdList").asList(String.class);
            if (serviceIdList == null || serviceIdList.isEmpty()) {
                log.info("软件授权码中未获取到软件标识列表");
                return expired(machineCodeBase64);
            }
            if (serviceIdList.contains(softwareKey)) {
                return toLicenceInfo(jwt.getExpiresAt(), machineCodeBase64);
            }
            log.info("未解析出当前软件标识:{}", softwareKey);
            return expired(machineCodeBase64);
        } catch (SignatureVerificationException e) {
            // 注意：软件版用的是 licence.invalid.signature（**不是**平台版的 licence.invalid.key.signature）
            throw new WrongArgumentException("licence.invalid.signature");
        } catch (TokenExpiredException e) {
            throw new WrongArgumentException("licence.activation.code.expired");
        } catch (InvalidClaimException e) {
            throw new WrongArgumentException("licence.invalid.args", e.getMessage());
        } catch (Exception e) {
            log.error("软件授权码错误，按未激活处理", e);
            return null;   // 厂商这里 return null，由调用方折算成 expired
        }
    }

    @Override
    public com.csm.paas.core.domain.licence.model.valobj.SoftwareActivationPayloadVO verifySoftwareActivationCode(
            String authToken) {
        String publicKey = licenceRepository.queryDefaultAuthPublicKey().orElse(null);
        if (publicKey == null) {
            throw new WrongArgumentException("auth.invalid.public.key.auth.code");
        }
        DecodedJWT jwt;
        try {
            JWTVerifier verifier = JWT.require(rsa512(publicKey)).withIssuer(ISSUER).build();
            jwt = verifier.verify(strip(authToken));
        } catch (Exception e) {
            // 厂商：**任何**异常都折算成这一个键（不像 checkActiveCode 那样细分）
            log.error("解析激活码失败，异常为:", e);
            throw new WrongArgumentException("software.parse.activation.code.failed");
        }
        String machineCode = jwt.getClaim("machineCode").asString();
        if (machineCode == null || machineCode.isEmpty()) {
            throw new WrongArgumentException("auth.missing.machine.id.signature");
        }
        List<String> serviceIdList = jwt.getClaim("serviceIdList").asList(String.class);
        if (serviceIdList == null || serviceIdList.isEmpty()) {
            throw new WrongArgumentException("auth.missing.software.key.signature");
        }
        return com.csm.paas.core.domain.licence.model.valobj.SoftwareActivationPayloadVO.builder()
                .machineCode(machineCode)
                .serviceIdList(serviceIdList)
                .expiresAt(jwt.getExpiresAt() == null ? null : jwt.getExpiresAt().getTime())
                .build();
    }

    /** 厂商 `LicenceInfo.expired(code)` = `{end:-1, code, expired:true}`（`endIso8601` 不设 ⇒ null）。 */
    private static LicenceInfoEntity expired(String code) {
        return LicenceInfoEntity.builder().end(-1L).code(code).expired(true).build();
    }

    /**
     * 成功时的 4 个字段（厂商 `checkActiveCode:651-657` / `checkSoftwareActiveCode:603-609`）：
     * `endIso8601` 用的是 **`ZoneId.systemDefault()`**，不是 UTC。
     */
    private static LicenceInfoEntity toLicenceInfo(java.util.Date expiresAt, String machineCodeBase64) {
        long now = System.currentTimeMillis();
        return LicenceInfoEntity.builder()
                .end(expiresAt.getTime())
                .code(machineCodeBase64)
                .expired(now > expiresAt.getTime())
                .endIso8601(DateTimeFormatter.ISO_DATE_TIME.format(
                        LocalDateTime.ofInstant(expiresAt.toInstant(), ZoneId.systemDefault())))
                .build();
    }

    /** 厂商 `checkActiveCode` 第一行：`activeCode.replaceAll("\\s*|\t|\r|\n", "")`。 */
    private static String strip(String activeCode) {
        return activeCode == null ? null : activeCode.replaceAll("\\s*|\t|\r|\n", "");
    }

    /** base64 X.509 公钥 → `Algorithm.RSA512`（厂商 `get()`/`getPublicKeyAlgorithm` 同款）。 */
    private static Algorithm rsa512(String publicKeyBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64));
            return Algorithm.RSA512((RSAPublicKey) factory.generatePublic(spec), null);
        } catch (Exception e) {
            throw new WrongArgumentException("auth.get.software.activate.public.key.failed");
        }
    }
}
