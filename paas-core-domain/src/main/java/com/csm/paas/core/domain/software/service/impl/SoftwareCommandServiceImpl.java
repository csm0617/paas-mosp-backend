package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.SoftwareFormVO;
import com.csm.paas.core.domain.software.service.ISoftwareCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 新增软件的校验与构造（规则见 {@link ISoftwareCommandService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareCommandServiceImpl implements ISoftwareCommandService {

    /** 厂商 `SoftwareController.RESERVED_WORD_API`（`:113`）。 */
    private static final String RESERVED_WORD_API = "api";
    /** 厂商 `SoftwareService.regEx`（`:285`）。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private final ISoftwareRepository softwareRepository;

    @Override
    public SoftwareEntity prepareNewSoftware(SoftwareFormVO form) {
        String key = form.getKey();
        String name = form.getName();

        if (RESERVED_WORD_API.equals(key)) {
            throw new WrongArgumentException("software.key.identifier.reserved.word", key);
        }
        if (softwareRepository.existsKey(key)) {
            throw new WrongArgumentException("software.key.identifier.existed", key);
        }
        if (softwareRepository.existsName(name)) {
            throw new WrongArgumentException("software.name.existed", name);
        }

        // ---- name ----
        if (name == null || name.isEmpty()) {
            throw new WrongArgumentException("software.name.must.not.null");
        }
        if (name.length() > 100 || name.length() < 2) {
            throw new WrongArgumentException("software.name.illegal", name);
        }

        // ---- key ----
        if (key == null || key.isEmpty()) {
            throw new WrongArgumentException("software.key.must.not.null");
        }
        char first = key.charAt(0);
        if (Character.isDigit(first)) {
            throw new WrongArgumentException("software.key.identifier.illegal.not.number.start", key);
        }
        if (first == '-') {
            throw new WrongArgumentException("software.key.identifier.illegal.not.dash.start", key);
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new WrongArgumentException("software.key.identifier.illegal.format", key);
        }
        if (key.length() > 36 || key.length() < 2) {
            throw new WrongArgumentException("software.key.identifier.illegal.length", key);
        }

        if (form.getDesc() != null && form.getDesc().length() > 255) {
            throw new WrongArgumentException("software.desc.length.invalid");
        }

        List<String> shots = form.getScreenShotList();
        long now = System.currentTimeMillis();
        return SoftwareEntity.builder()
                .id(UUID.randomUUID().toString())
                .key(key)
                .name(name)
                // 厂商 SoftwareForm **没有 appName 字段**（BeanUtils 拷不到）⇒ 新建软件的 appName 是 null
                .routerKey("/" + key + "/")
                .type(form.getType())
                .desc(form.getDesc())
                .location(form.getLocation())
                .remoteIp(form.getRemoteIp())
                .remoteName(form.getRemoteName())
                .remotePassword(form.getRemotePassword())
                .framework(form.getFramework())
                .execName(form.getExecName())
                .icon(form.getIcon())
                .screenshot(shots == null || shots.isEmpty() ? null : String.join(",", shots))
                .orderTop(0)
                .createdAt(now)
                // 厂商 addSoftWare **不设 updatedAt**（只设 createdAt），与用户/战位新增一致
                .updatedAt(null)
                .build();
    }
}
