package com.csm.paas.core.infrastructure.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * fabric8 7 的客户端装配。
 * <p>替换厂商的 io.kubernetes:client-java:18.0.1。
 * <p>注意：厂商网络页白屏的根因**不是** client-java，而是厂商自建的
 * {@code third/k8s/IoK8sApiCoreV1*} DTO 把 targetPort/port 声明成了 Integer，
 * 叠加 Gson→Jackson 往返与 catch(Exception)→置空（详见
 * docs/findings/named-port-fabric8-7.9.0.md）。fabric8 的模型用 IntOrString，
 * 这里保持回归测试以防再次踩坑。
 */
@Configuration
public class KubernetesClientConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
