package com.csm.paas.core.domain.licence;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.csm.paas.core.domain.licence.adapter.repository.ILicenceRepository;
import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;
import com.csm.paas.core.domain.licence.model.valobj.MachineCodeVO;
import com.csm.paas.core.domain.licence.service.impl.LicenceQueryServiceImpl;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * K3 激活码校验（**离线、真密码学**）。
 *
 * <p>这是本轮的关键验证手段：厂商的硬前提是"有一个用私钥签出来的激活码 JWT"，
 * 我们**不可能拿到厂商的私钥**——但可以**自己生成一对 RSA 密钥**、把公钥塞进仓储、
 * 用它签出结构完全相同的 JWT（issuer `gaop`），从而把"验签 + claim + 机器码交集"这段
 * 真正跑起来，而不是用 mock 假装验签通过。
 *
 * <p>规则来源：`LicenceService.checkActiveCode:633-680`、`checkSoftwareActiveCode:581-631`、
 * `LicenceKey.parseMachineCode:19-38`、`stringToHashSetStream/hasIntersectionStream:551-565`。
 */
class LicenceActivationVerifyTest {

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;
    private static String publicKeyB64;

    private final ILicenceRepository repository = mock(ILicenceRepository.class);

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
        publicKeyB64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private LicenceQueryServiceImpl service() {
        return new LicenceQueryServiceImpl(repository);
    }

    /** 机器码：`Base64(gaopCode,tenxCode)`，与厂商 `getMachineCodeBase64()` 同形。 */
    private static String machineCode(String gaopCode) {
        return Base64.getEncoder().encodeToString((gaopCode + ",gaop,").getBytes(StandardCharsets.US_ASCII));
    }

    private static String sign(KeyPair pair, String machineCodeClaim, List<String> serviceIdList, Instant expiresAt) {
        Algorithm algorithm = Algorithm.RSA512((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        var builder = JWT.create().withIssuer("gaop").withExpiresAt(Date.from(expiresAt));
        if (machineCodeClaim != null) {
            builder.withClaim("machineCode", machineCodeClaim);
        }
        if (serviceIdList != null) {
            builder.withClaim("serviceIdList", serviceIdList);
        }
        return builder.sign(algorithm);
    }

    // ---------- 机器码解析 ----------

    @Test
    void machineCodeParseFollowsVendorRules() {
        MachineCodeVO parsed = MachineCodeVO.parse(machineCode("code1;code2"));
        assertEquals("code1;code2", parsed.getGaopCode());
        assertEquals("gaop", parsed.getTenxCode());

        // 以 ey 开头一律返回空对象（厂商用这个前缀区分"传进来的是激活码"）
        MachineCodeVO jwtLike = MachineCodeVO.parse("eyJhbGciOiJSUzUxMiJ9");
        assertNull(jwtLike.getGaopCode());
        assertNull(jwtLike.getTenxCode());
    }

    @Test
    void machineCodeSetUsesSemicolonNotComma() {
        assertTrue(LicenceSets.parse("a;b;c").containsAll(List.of("a", "b", "c")));
        assertTrue(LicenceSets.parse(null).isEmpty());
        // 逗号不会被切开——这就是"机器码里的逗号"和"多个机器码之间的分号"两层分隔符的区别
        assertEquals(1, LicenceSets.parse("a,b").size());
    }

    // ---------- 平台激活码：checkActiveCode ----------

    @Test
    void platformCodeSucceedsWhenMachineCodeIntersects() {
        when(repository.queryGaopActivatePublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        Instant expiry = Instant.now().plusSeconds(3600);
        String mc = machineCode("A1;B2");
        String code = sign(keyPair, "X9;B2;Z3", null, expiry);

        LicenceInfoEntity info = service().checkActiveCode(mc, code);

        // JWT 的 exp 只到**秒**，所以 end 会被截断到整秒（厂商 setEnd(expiresAt.getTime()) 同样如此）
        assertEquals(expiry.getEpochSecond() * 1000, info.getEnd());
        assertEquals(mc, info.getCode());
        assertFalse(info.getExpired(), "未过期");
        assertNotNull(info.getEndIso8601(), "成功时会带 endIso8601");
    }

    @Test
    void platformCodeRejectsWhenNoIntersection() {
        when(repository.queryGaopActivatePublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        String code = sign(keyPair, "NOPE1;NOPE2", null, Instant.now().plusSeconds(3600));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().checkActiveCode(machineCode("A1;B2"), code));
        assertEquals("auth.invalid.auth.machine.id", e.getI18nKey());
    }

    @Test
    void platformCodeRejectsSignatureFromAnotherKey() {
        when(repository.queryGaopActivatePublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        String code = sign(otherKeyPair, "A1", null, Instant.now().plusSeconds(3600));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().checkActiveCode(machineCode("A1"), code));
        assertEquals("licence.invalid.key.signature", e.getI18nKey());
    }

    @Test
    void platformCodeRejectsExpiredToken() {
        when(repository.queryGaopActivatePublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        String code = sign(keyPair, "A1", null, Instant.now().minusSeconds(60));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().checkActiveCode(machineCode("A1"), code));
        assertEquals("licence.activation.code.expired", e.getI18nKey());
    }

    @Test
    void platformCodeRejectsWrongIssuer() {
        when(repository.queryGaopActivatePublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        Algorithm algorithm = Algorithm.RSA512((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        String code = JWT.create().withIssuer("not-gaop").withExpiresAt(Date.from(Instant.now().plusSeconds(600)))
                .withClaim("machineCode", "A1").sign(algorithm);

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().checkActiveCode(machineCode("A1"), code));
        assertEquals("licence.invalid.args", e.getI18nKey(), "issuer 不匹配在 java-jwt 里是 InvalidClaimException");
    }

    // ---------- 软件授权码：checkSoftwareActiveCode ----------

    @Test
    void softwareCodeSucceedsWhenSoftwareKeyIsInClaim() {
        when(repository.queryDefaultAuthPublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        Instant expiry = Instant.now().plusSeconds(3600);
        String code = sign(keyPair, null, List.of("other", "myapp"), expiry);

        LicenceInfoEntity info = service().checkSoftwareActiveCode(machineCode("A1"), code, "myapp");

        assertEquals(expiry.getEpochSecond() * 1000, info.getEnd(), "JWT 的 exp 只到秒");
        assertFalse(info.getExpired());
    }

    @Test
    void softwareCodeFallsBackToExpiredWhenSoftwareKeyMissing() {
        when(repository.queryDefaultAuthPublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        String code = sign(keyPair, null, List.of("other"), Instant.now().plusSeconds(3600));

        LicenceInfoEntity info = service().checkSoftwareActiveCode(machineCode("A1"), code, "myapp");

        assertEquals(-1L, info.getEnd(), "未命中软件标识 → LicenceInfo.expired（end=-1）");
        assertTrue(info.getExpired());
        assertNull(info.getEndIso8601(), "expired 分支不设 endIso8601");
    }

    @Test
    void softwareCodeFallsBackToExpiredWhenServiceIdListEmpty() {
        when(repository.queryDefaultAuthPublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        String code = sign(keyPair, null, List.of(), Instant.now().plusSeconds(3600));

        LicenceInfoEntity info = service().checkSoftwareActiveCode(machineCode("A1"), code, "myapp");

        assertEquals(-1L, info.getEnd());
        assertTrue(info.getExpired());
    }

    @Test
    void softwareCodeUsesItsOwnSignatureErrorKey() {
        when(repository.queryDefaultAuthPublicKey()).thenReturn(java.util.Optional.of(publicKeyB64));
        String code = sign(otherKeyPair, null, List.of("myapp"), Instant.now().plusSeconds(3600));

        WrongArgumentException e = assertThrows(WrongArgumentException.class,
                () -> service().checkSoftwareActiveCode(machineCode("A1"), code, "myapp"));
        assertEquals("licence.invalid.signature", e.getI18nKey(),
                "软件版是 licence.invalid.signature；平台版是 licence.invalid.key.signature——两个不同的键");
    }

    @Test
    void softwareCodeReturnsNullWhenNoPublicKeyConfigured() {
        when(repository.queryDefaultAuthPublicKey()).thenReturn(java.util.Optional.empty());

        assertNull(service().checkSoftwareActiveCode(machineCode("A1"), "whatever", "myapp"),
                "g_auth 里没有 default_auth → 厂商会 IndexOutOfBounds；我们返回 null，由调用方折算成 expired（记录为收紧）");
    }
}
