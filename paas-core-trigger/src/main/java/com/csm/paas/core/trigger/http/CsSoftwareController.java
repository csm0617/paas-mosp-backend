package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.SoftwareInfoDTO;
import com.csm.paas.core.domain.cssoftware.service.ICsSoftwareCommandService;
import com.csm.paas.core.domain.software.service.ISoftwareDetailService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商 `CsSoftwareController`（`/v1/cs_software`）——本次只实现两个端点。
 *
 * <p>已实现：`POST /upload`（上传 CS 程序 zip 包）、`POST /{id}/relate`（软件关联 CS 程序，含版本回滚）。
 * 未实现（不在本轮范围）：`DELETE /`（批量删）、`GET /download`、`GET /upload`（列表）、
 * `POST /relation`（导出关联）、`POST /import`、`POST /backUp`。
 *
 * <p><b>类 C：本环境不可验证</b>——两个端点都会走 SSH（建目录 / 删目录 / 解压）。
 */
@Slf4j
@RestController
@RequestMapping("/v1/cs_software")
@RequiredArgsConstructor
public class CsSoftwareController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ICsSoftwareCommandService csSoftwareCommandService;
    private final ISoftwareDetailService softwareDetailService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 厂商 `POST /v1/cs_software/upload`（multipart `file`）：上传 CS 程序 zip 包。
     *
     * <p>厂商 `CsSoftwareController.java:84-90` → `CsSoftwareUploadService.uploadCsSoftwareProgram:93-108`。
     * MIME 守卫照抄：Tika 探测结果必须等于 `application/zip`，否则 400
     * `global.upload.supported.file.type`（Tika 只在 trigger 有依赖，所以这一段留在控制器，
     * 与厂商把它放在 service 里等价）。
     */
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<Boolean> uploadCsSoftwareProgram(@RequestPart("file") MultipartFile file)
            throws IOException {
        String contentType = new Tika().detect(file.getInputStream());
        if (!"application/zip".equals(contentType)) {
            throw new WrongArgumentException("global.upload.supported.file.type");
        }
        return ResponseDTO.ok(csSoftwareCommandService.uploadCsSoftwareProgram(
                file.getBytes(), file.getOriginalFilename()));
    }

    /**
     * 厂商 `POST /v1/cs_software/{id}/relate?uploadStoreRecordId=`：软件关联 CS 程序（含版本回滚）。
     *
     * <p>厂商 `CsSoftwareController.java:134-152`，顺序照抄：
     * ① `rollBackVersion` 返回 false ⇒ 400 `service.rollback.cs.program.failed`；
     * ② `relateCsProgram`；③ 查该软件关联的战位 id，**非 null 就推** `/topic/software`
     * （`{event:"MODIFY", roleList:"id1,id2"}`——`getRoleIdBySoftwareId` 恒返回非 null，
     * 所以没有关联时也会推一条 `roleList:""`，照抄）；④ 返回**软件详情**（23 键）。
     */
    @PostMapping("/{id}/relate")
    public ResponseDTO<SoftwareInfoDTO> softwareRelateCsProgram(
            @PathVariable String id,
            @RequestParam String uploadStoreRecordId) throws IOException {
        boolean relate = csSoftwareCommandService.rollBackVersion(id, uploadStoreRecordId);
        if (!relate) {
            throw new WrongArgumentException("service.rollback.cs.program.failed");
        }
        csSoftwareCommandService.relateCsProgram(id, uploadStoreRecordId);
        List<String> roleIdList = csSoftwareCommandService.queryRoleIdsBySoftwareId(id);
        if (roleIdList != null) {
            pushSoftwareModified(String.join(",", roleIdList));
            log.info("cs软件切换版本");
        }
        return ResponseDTO.ok(com.csm.paas.core.trigger.assembler.SoftwareInfoAssembler.toResponse(
                softwareDetailService.queryDetail(id)));
    }

    /**
     * 厂商 `CsSoftwareController.java:145-148`：把 `RoleService.softwareStatus`
     * （`{event:"MODIFY", roleList:"<逗号连接的 roleId>"}`）推到 `/topic/software`。
     * 载荷形状与既有 {@code SoftwareRelationBroadcaster} 一致。
     */
    private void pushSoftwareModified(String roleList) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "MODIFY");
            payload.put("roleList", roleList);
            messagingTemplate.convertAndSend("/topic/software", MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("推送 /topic/software 失败（不影响响应）: {}", e.toString());
        }
    }
}
