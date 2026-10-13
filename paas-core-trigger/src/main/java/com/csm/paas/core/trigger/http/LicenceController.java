package com.csm.paas.core.trigger.http;

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

    /** 厂商 GET /v1/licence/checkAndActiveGis：GIS 授权检查（本环境为 false）。 */
    @GetMapping("/v1/licence/checkAndActiveGis")
    public ResponseDTO<Boolean> checkAndActiveGis() {
        return ResponseDTO.ok(licenceQueryService.checkAndActiveGis());
    }

    /** 厂商 GET /v1/licence/info：授权状态（未激活时给试用期）。 */
    @GetMapping("/v1/licence/info")
    public ResponseDTO<LicenceInfoDTO> info() {
        LicenceInfoEntity e = licenceQueryService.queryInfo();
        return ResponseDTO.ok(LicenceInfoDTO.builder()
                .end(e.getEnd()).endIso8601(e.getEndIso8601()).code(e.getCode())
                .expired(e.getExpired()).authStatus(e.getAuthStatus())
                .currentTime(e.getCurrentTime()).trialPeriod(e.getTrialPeriod())
                .build());
    }
}
