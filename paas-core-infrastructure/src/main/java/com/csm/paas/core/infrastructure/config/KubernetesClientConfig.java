package com.csm.paas.core.infrastructure.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * fabric8 7 的客户端装配。
 * <p>替换厂商的 io.kubernetes:client-java:18.0.1；具名端口
 * （ServicePort.targetPort / TCPSocketAction.port 为字符串）在新模型下可正常反序列化，
 * 这是厂商后端网络页白屏的根因。
 */
@Configuration
public class KubernetesClientConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
