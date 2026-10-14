package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.ValidationResultDTO;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 厂商 `CreateK8sServicesController`（`/v1/CreateK8sServices`）——只有一个端点 `POST /check`。
 *
 * <p><b>照抄厂商的全部逻辑</b>（`CreateK8sServicesController.java:28-66`）：厂商**没有 service**，
 * 直接在控制器里 `ProcessBuilder("kubectl","apply","-f","-","--dry-run=client")` 把请求体 YAML 喂进去，
 * 读合并输出（`redirectErrorStream(true)`），`waitFor(10, SECONDS)`，退出码 0 ⇒ `valid=true`。
 * 我们把这段原样搬进控制器（JDK 能力，不引依赖），只把 `ValidationResult` 换成对外 DTO。
 *
 * <p>厂商怪点，逐个照抄：
 * <ol>
 *   <li>body 空白 ⇒ 400 `service.yaml.not.null`（厂商用 `StringUtils.isBlank`，行为等价）；</li>
 *   <li>超时（10 秒）⇒ `destroyForcibly()` 后返回 `{valid:false, message:"Validation timed out."}`；</li>
 *   <li>任何异常（含 `kubectl` 不存在）⇒ `{valid:false, message:"Failed to execute kubectl command: " + e.getMessage()}`，
 *       **不抛错**；</li>
 *   <li>成功/失败的 `message` 都是 `kubectl` 的输出，且每行都补一个 `"\n"`（末尾多一个换行）。</li>
 * </ol>
 *
 * <p><b>类 C：本环境不可验证</b>——本机/容器里没有可用的 `kubectl`（或没有集群上下文），
 * 该端点在此处必然走 "Failed to execute kubectl command" 分支；代码齐全，不做在线验证。
 */
@RestController
@RequestMapping("/v1/CreateK8sServices")
public class CreateK8sServicesController {

    private static final Logger log = LoggerFactory.getLogger(CreateK8sServicesController.class);

    /**
     * 厂商 `POST /v1/CreateK8sServices/check`：把裸 YAML 字符串交给 `kubectl --dry-run=client` 校验。
     *
     * <p>请求体是**裸 YAML**（不是 JSON 信封）；响应是 `Response.ok(ValidationResult)`
     * ⇒ `{"msg":"OK","code":0,"data":{"valid":...,"message":"..."}}`。
     */
    @PostMapping("/check")
    public ResponseDTO<ValidationResultDTO> createCheck(@RequestBody(required = false) String validYaml) {
        if (validYaml == null || validYaml.isBlank()) {
            throw new WrongArgumentException("service.yaml.not.null");
        }
        return ResponseDTO.ok(validate(validYaml));
    }

    /**
     * 厂商 `CreateK8sServicesController.validate:37-66`，逐行照抄。
     *
     * <p>注意厂商在 try-with-resources 里**先关掉子进程 stdin**（`os.write` 后立刻 close），
     * 再读 stdout——否则 `kubectl apply -f -` 会一直等输入。顺序不能改。
     */
    public ValidationResultDTO validate(String yamlContent) {
        ProcessBuilder processBuilder = new ProcessBuilder("kubectl", "apply", "-f", "-", "--dry-run=client");
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            try (OutputStream os = process.getOutputStream()) {
                os.write(yamlContent.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            boolean finished = process.waitFor(10L, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ValidationResultDTO.builder().valid(false).message("Validation timed out.").build();
            }
            int exitCode = process.exitValue();
            // 厂商：退出码 0 ⇒ true，否则 false；两种情况的 message 都是 kubectl 的输出
            return ValidationResultDTO.builder()
                    .valid(exitCode == 0)
                    .message(output.toString())
                    .build();
        } catch (Exception e) {
            log.error("执行 kubectl 校验失败", e);
            return ValidationResultDTO.builder()
                    .valid(false)
                    .message("Failed to execute kubectl command: " + e.getMessage())
                    .build();
        }
    }
}
