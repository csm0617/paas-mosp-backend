package com.csm.paas.core.infrastructure.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * `g_service_auth` 的直写网关（复刻厂商 `AuthService.authing:210-230` 的 upsert）。
 *
 * <p>为什么不用 MyBatis：既有 `dao/IServiceAuthDao` 没有 insert/update 全套，而本次任务的白名单
 * **不放行** `infrastructure/dao/**`（只放行新建 `ICicdDao`/`Cicd*`）与 `domain/software/**`。
 * 于是按 `DatabaseProcessGateway` 的既有做法直接用 `DataSource`。
 *
 * <p>列名出处：厂商 `dao/ServiceAuthDynamicSqlSupport.java:24-31`；DDL 见 vendor `gaop-9.sql:771-781`
 * （`updated_at ... ON UPDATE CURRENT_TIMESTAMP`，所以**不显式写 updated_at**）。
 *
 * <p>照抄点：判断存在的 `serviceAuth1 != null` 在厂商那里是多余的（MyBatis-dynamic-sql 的
 * `selectByPrimaryKey` 返回 `Optional`，永不为 null），实际语义就是"存在就 update、否则 insert"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubServiceAuthGateway {

    private static final String SELECT_EXIST =
            "SELECT service_id FROM g_service_auth WHERE service_id = ?";

    /** 厂商 `updateByPrimaryKeySelective`：这里四个值都非 null，所以等价于全量写这四列。 */
    private static final String UPDATE_AUTH =
            "UPDATE g_service_auth SET auth = ?, auth_type = ?, auth_time = ?, auth_code = ? "
                    + "WHERE service_id = ?";

    /** 厂商 `insertSelective`：写 service_id/auth/auth_type/auth_time/auth_code/created_at。 */
    private static final String INSERT_AUTH =
            "INSERT INTO g_service_auth (service_id, auth, auth_type, auth_time, auth_code, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    /**
     * @return 影响行数是否为 1（厂商 `if (updateResult == 1)` / `if (insertResult == 1)`）
     */
    public boolean upsertServiceAuth(String serviceId, Integer auth, Integer authType, Long authTime,
                                     String authCode) {
        try (Connection connection = dataSource.getConnection()) {
            boolean exist = exists(connection, serviceId);
            if (exist) {
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_AUTH)) {
                    ps.setObject(1, auth);
                    ps.setObject(2, authType);
                    ps.setObject(3, authTime);
                    ps.setObject(4, authCode);
                    ps.setString(5, serviceId);
                    int updated = ps.executeUpdate();
                    log.info("g_service_auth 更新 service_id={} 影响 {} 行", serviceId, updated);
                    return updated == 1;
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(INSERT_AUTH)) {
                ps.setString(1, serviceId);
                ps.setObject(2, auth);
                ps.setObject(3, authType);
                ps.setObject(4, authTime);
                ps.setObject(5, authCode);
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                int inserted = ps.executeUpdate();
                log.info("g_service_auth 新增 service_id={} 影响 {} 行", serviceId, inserted);
                return inserted == 1;
            }
        } catch (Exception e) {
            log.error("写 g_service_auth 失败: service_id={}", serviceId, e);
            throw new com.csm.paas.core.types.exception.SystemException("auth.service.save.failed");
        }
    }

    private static boolean exists(Connection connection, String serviceId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_EXIST)) {
            ps.setString(1, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
