package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.licence.adapter.port.IGisLicenseStatusPort;
import com.csm.paas.core.types.exception.K8sApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 厂商 `HttpClientUtil.sendGetData(url)` + `Gson.fromJson(status, GisStatus.class).getEVServer()` 的等价实现。
 * <p>URL 固定拼 `/earthview/license/getLicenseStatus`（厂商如此）；超时 10s。
 */
@Component
public class GisLicenseStatusPort implements IGisLicenseStatusPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PATH = "/earthview/license/getLicenseStatus";

    @Override
    public Integer queryEvServer(String baseUrl) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = MAPPER.readTree(response.body());
            JsonNode value = node.get("EVServer");
            return value == null || value.isNull() ? null : value.asInt();
        } catch (Exception e) {
            // 调用方（domain）据此返回 2
            throw new K8sApiException(e.toString());
        }
    }
}
