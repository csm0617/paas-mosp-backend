package com.csm.paas.core;

import com.csm.paas.core.domain.software.adapter.repository.IServiceAuthRepository;
import com.csm.paas.core.domain.software.model.entity.ServiceAuthEntity;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 离线冒烟：Spring 上下文 + fabric8（mock）+ MyBatis（H2）**不依赖集群**能起来并完成一次真实查询。
 * <p>对气隙目标是硬要求：门槛的自动化部分不能要求集群在场。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:paas_contract;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql"
        })
class PaasCoreContextTest {

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient kubernetesClient;

    @Autowired
    private IServiceAuthRepository serviceAuthRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextStartsAndMybatisRoundTripsAgainstSchema() {
        jdbcTemplate.update("DELETE FROM g_service_auth");
        jdbcTemplate.update("INSERT INTO g_service_auth (service_id, auth, auth_type) VALUES (?, ?, ?)",
                "svc-contract-test", 1, 2);

        List<ServiceAuthEntity> rows = serviceAuthRepository.queryAll();

        assertEquals(1, rows.size(), "MyBatis 应能读回刚写入的行");
        assertEquals("svc-contract-test", rows.get(0).getServiceId());
        assertEquals(1, rows.get(0).getAuth());
        assertEquals(2, rows.get(0).getAuthType());
    }

}
