package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.mode.service.IModeCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `ModeController`（`/v1/mode`）的**写端点**。
 *
 * <p>读端点（`GET /current`、`GET /list`）已经在 {@link ShellController} 里实现，本类只补
 * `POST /updateModeType`（厂商 `ModeController.java:42-46`）。类级前缀相同但方法路径不重叠，
 * Spring 不会冲突。
 *
 * <p><b>破坏性</b>：会改各业务软件 Deployment 的 `MODE` 环境变量 + 改库 `used` 标记 +（厂商侧）发 Kafka。
 */
@RestController
@RequestMapping("/v1/mode")
@RequiredArgsConstructor
public class ModeController {

    private final IModeCommandService modeCommandService;

    /**
     * 厂商 `POST /v1/mode/updateModeType?id=`：切换作战模式。
     *
     * <p>返回 `Response.ok(int)`；厂商 `startModeType` **恒返回 1**。规则见
     * {@code IModeCommandService.startModeType}。
     */
    @PostMapping("/updateModeType")
    public ResponseDTO<Integer> updateModeType(@RequestParam Long id) {
        return ResponseDTO.ok(modeCommandService.startModeType(id));
    }
}
