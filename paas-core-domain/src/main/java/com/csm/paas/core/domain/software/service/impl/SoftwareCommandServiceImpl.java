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

    @Override
    public boolean releaseSoftware(Integer type, java.util.List<String> softwareIds) {
        if (softwareIds == null || softwareIds.isEmpty()) {
            return true;   // 厂商：list 为空直接 true
        }
        if (type != null && type == 1) {
            long now = System.currentTimeMillis();
            for (String id : softwareIds) {
                SoftwareEntity s = softwareRepository.queryById(id).orElse(null);
                if (s == null) {
                    log.info("软件 id={} 不存在，跳过", id);
                    continue;
                }
                softwareRepository.insertWhiteListPath("/" + s.getKey() + "/**", now);
                log.info("将软件 {} 加入白名单放行", s.getName());
            }
        } else if (type != null && type == 0) {
            for (String id : softwareIds) {
                SoftwareEntity s = softwareRepository.queryById(id).orElse(null);
                if (s == null) {
                    log.info("软件 id={} 不存在，跳过", id);
                    continue;
                }
                String path = "/" + s.getKey() + "/**";
                int deleted = softwareRepository.deleteWhiteListPath(path);
                // 厂商在这里**第一次就 return**（只看第一个存在的软件）
                return deleted > 0;
            }
        }
        // 厂商随后会 notificationGateway() 通知网关重载；我们尚未接管网关，见接口 javadoc
        return true;
    }

    @Override
    public SoftwareEntity requireExisting(String softwareId) {
        return softwareRepository.queryById(softwareId)
                .orElseThrow(() -> new WrongArgumentException("software.not.exist.by.id"));
    }

    /**
     * 修改图标（厂商 `updateIcon:2577-2595`，规格见 `docs/findings/130`）。
     */
    @Override
    public String updateIcon(String softwareId, String icon) {
        SoftwareEntity existing = softwareRepository.queryById(softwareId)
                .orElseThrow(() -> new WrongArgumentException("software.not.found"));
        if (icon != null) {
            // 厂商：与库里完全相同 ⇒ 直接返回、不写库（也不推进 updated_at）
            if (icon.equals(existing.getIcon())) {
                return icon;
            }
            softwareRepository.updateSoftwareIcon(softwareId, icon, System.currentTimeMillis());
        } else {
            softwareRepository.updateSoftwareIcon(softwareId, null, System.currentTimeMillis());
        }
        return icon;
    }

    /**
     * 软件置顶（厂商 `orderSoftWare:3816-3832`，规格见 `docs/findings/131`）。
     */
    @Override
    public boolean orderSoftware(java.util.List<String> softwareIds, Integer orderNum) {
        if (softwareIds == null || softwareIds.isEmpty()) {
            return false;
        }
        for (String id : softwareIds) {
            // 厂商：读不到就抛（带 id 参数）；因为是边遍历边写，前面的 id 已经落库
            softwareRepository.queryById(id)
                    .orElseThrow(() -> new WrongArgumentException("software.id.not.found", id));
            softwareRepository.updateSoftwareOrderTop(id, orderNum, System.currentTimeMillis());
        }
        return true;
    }

    /**
     * 修改截图（厂商 `updateScreenshot:2556-2575`，规格见 `docs/findings/130`）。
     */
    @Override
    public java.util.List<String> updateScreenshot(String softwareId, java.util.List<String> screenShotList) {
        SoftwareEntity existing = softwareRepository.queryById(softwareId)
                .orElseThrow(() -> new WrongArgumentException("software.not.found"));
        // 厂商写的是 `!screenShotList.isEmpty()` ⇒ null 时 NPE；我们按空列表处理（B21）
        java.util.List<String> list = screenShotList == null ? java.util.List.of() : screenShotList;
        if (!list.isEmpty()) {
            String screenshot = String.join(",", list);
            if (screenshot.equals(existing.getScreenshot())) {
                return list;
            }
            softwareRepository.updateSoftwareScreenshot(softwareId, screenshot, System.currentTimeMillis());
        } else {
            softwareRepository.updateSoftwareScreenshot(softwareId, null, System.currentTimeMillis());
        }
        return list;
    }

    @Override
    public SoftwareEntity prepareUpdatedSoftware(String softwareId, SoftwareFormVO form) {
        SoftwareEntity existing = softwareRepository.queryById(softwareId)
                .orElseThrow(() -> new WrongArgumentException("software.not.exist.by.id"));

        if (form.getKey() != null && softwareRepository.existsByKeyExcludingId(form.getKey(), softwareId)) {
            throw new WrongArgumentException("software.key.identifier.existed", form.getKey());
        }
        if (form.getName() != null && softwareRepository.existsByNameExcludingId(form.getName(), softwareId)) {
            // 厂商 name 重复时用的**也是** software.key.identifier.existed（两个分支同一个键，照抄）
            throw new WrongArgumentException("software.key.identifier.existed", form.getName());
        }

        SoftwareFormVO checked = SoftwareFormVO.builder().key(form.getKey()).name(form.getName()).build();
        validateName(checked.getKey(), checked.getName() == null ? null : checked.getName());
        validateKey(checked.getKey());

        // 厂商 BeanUtils.copyProperties(form, sf)：**表单没带的字段会被置成 null**（含 routerKey）
        return SoftwareEntity.builder()
                .id(existing.getId())
                .key(form.getKey())
                .name(form.getName())
                .appName(existing.getAppName())
                .routerKey(form.getRouterKey())
                .type(form.getType())
                .version(existing.getVersion())
                .createdAt(existing.getCreatedAt())
                .updatedAt(existing.getUpdatedAt())
                .framework(form.getFramework())
                .execName(form.getExecName())
                .location(form.getLocation())
                .remoteIp(form.getRemoteIp())
                .remoteName(form.getRemoteName())
                .remotePassword(form.getRemotePassword())
                .activeCreate(existing.getActiveCreate())
                .activeExpire(existing.getActiveExpire())
                .orderTop(existing.getOrderTop())
                .orderTime(existing.getOrderTime())
                // 厂商 softwareDescLimit 在修改路径里把空白 desc 置成 **null**
                // （新增路径那次 null 被返回值覆盖成空串，两处恰好相反）
                .desc(isBlank(form.getDesc()) ? null : form.getDesc())
                .activeCode(existing.getActiveCode())
                .screenshot(existing.getScreenshot())
                .icon(form.getIcon())
                .build();
    }

    /** 厂商用 commons-lang 的 `StringUtils.isBlank`（null/空串/全空白都算空）。 */
    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    /** 与新增同一套 name 规则（厂商 `softwareNameLimit`）。 */
    private void validateName(String key, String name) {
        if (name == null || name.isEmpty()) {
            throw new WrongArgumentException("software.name.must.not.null");
        }
        if (name.length() > 100 || name.length() < 2) {
            throw new WrongArgumentException("software.name.illegal", name);
        }
    }

    /** 与新增同一套 key 规则（厂商 `softwareKeyLimit`）。 */
    private void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new WrongArgumentException("software.key.must.not.null");
        }
        char first = key.charAt(0);
        if (Character.isDigit(first)) {
            throw new WrongArgumentException("software.key.identifier.illegal.not.number.start", key);
        }
        if (first == '-') {
            throw new WrongArgumentException("software.key.identifier.illegal.dash.start".replace(".dash.start", ".not.dash.start"), key);
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new WrongArgumentException("software.key.identifier.illegal.format", key);
        }
        if (key.length() > 36 || key.length() < 2) {
            throw new WrongArgumentException("software.key.identifier.illegal.length", key);
        }
    }
}
