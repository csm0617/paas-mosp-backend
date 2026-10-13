package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ActiveLicenceDTO;
import com.csm.paas.core.api.dto.LicenceInfoDTO;
import com.csm.paas.core.api.dto.MachineInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.licence.model.entity.LicenceInfoEntity;
import com.csm.paas.core.domain.licence.service.ILicenceQueryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权端点。前端在**登录页**就会调它们（`docs/findings/13-frontend-e2e-first-run.md`），
 * 因此都是公开的（实测厂商不带 cookie 也 200）。
 */
@RestController
public class LicenceController {

    @Resource
    private ILicenceQueryService licenceQueryService;

    /** 厂商 GET /v1/licence：机器码 + 是否有 GIS 授权。 */
    @GetMapping("/v1/licence")
    public ResponseDTO<MachineInfoDTO> machineCode() {
        return ResponseDTO.ok(MachineInfoDTO.builder()
                .code(licenceQueryService.machineCode())
                .existGis(false)
                .build());
    }

    /**
     * 厂商 `POST /v1/licence`：**平台激活**。
     * <p>顺序照抄 `LicenceController:54-63`：先 strip 激活码 → `checkActiveCode` 校验
     * （**抛错就直接 400，不落库**）→ 落库 → 返回 `/v1/licence/info` 的形状（7 键）。
     * <p>未实现：厂商随后还会通知 TenX、激活 GIS、把码写到授权文件。
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/licence")
    public ResponseDTO<LicenceInfoDTO> activate(
            @org.springframework.web.bind.annotation.RequestBody ActiveLicenceDTO param) {
        return toInfo(licenceQueryService.activate(param.getActiveCode()));
    }

    /** 厂商 `DELETE /v1/licence`：清空授权信息，返回 info 形状。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/v1/licence")
    public ResponseDTO<LicenceInfoDTO> deactivate() {
        return toInfo(licenceQueryService.deactivate());
    }

    /** 厂商 GET /v1/licence/checkAndActiveGis：GIS 授权检查（本环境为 false）。 */
    @GetMapping("/v1/licence/checkAndActiveGis")
    public ResponseDTO<Boolean> checkAndActiveGis() {
        return ResponseDTO.ok(licenceQueryService.checkAndActiveGis());
    }

    /** 厂商 GET /v1/licence/info：授权状态（未激活时给试用期）。 */
    @GetMapping("/v1/licence/info")
    public ResponseDTO<LicenceInfoDTO> info() {
        return toInfo(licenceQueryService.queryInfo());
    }

    /** 三个端点（`GET /info`、`POST /v1/licence`、`DELETE /v1/licence`）返回的都是这个 7 键形状。 */
    private static ResponseDTO<LicenceInfoDTO> toInfo(LicenceInfoEntity e) {
        return ResponseDTO.ok(LicenceInfoDTO.builder()
                .end(e.getEnd()).endIso8601(e.getEndIso8601()).code(e.getCode())
                .expired(e.getExpired()).authStatus(e.getAuthStatus())
                .currentTime(e.getCurrentTime()).trialPeriod(e.getTrialPeriod())
                .build());
    }
}
