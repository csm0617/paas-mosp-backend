package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.user.adapter.repository.IUserDefaultAvatarRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.DeleteUserOutcome;
import com.csm.paas.core.domain.user.model.valobj.UserCallerVO;
import com.csm.paas.core.domain.user.model.valobj.UserFormVO;
import com.csm.paas.core.domain.user.service.IUserCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 用户写操作（删除 + 新增的构造与校验）。规则见 {@link IUserCommandService} 的文档。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements IUserCommandService {

    /** 内置超级管理员的用户名——厂商多处硬编码（`UserController.java:325,377`）。 */
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE_KEY = "admin";

    /**
     * 用户名"非法字符"黑名单——**逐字照抄厂商** `UserController.regEx`（`UserController.java:92`）。
     * <p><b>注意厂商这里有个 bug，我们照抄不修</b>：他用的是 `matcher.matches()`，而这是个**字符类**，
     * `matches()` 要求**整个串**匹配 → 实际只有"长度为 1 且该字符在黑名单里"才会命中；
     * 而长度校验（2~36）已经把长度为 1 的情况拒掉了 ⇒ **这个字符检查在实践上是死代码**，
     * 带 `@`/`#` 的用户名照样能建。改成 `find()` 会让行为与厂商不同（那才是"修好"），
     * 要改必须先挑战判断。已用 `UserCommandServiceImplTest` 锁住该行为。
     */
    private static final Pattern USERNAME_BLACKLIST = Pattern.compile(
            "[\n`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。， 、？]");

    private static final int USERNAME_MIN = 2;
    private static final int USERNAME_MAX = 36;
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 36;
    private static final int EMAIL_MAX = 36;
    private static final int DESC_MAX = 255;
    /** 厂商的 uuid 扫描上界：`for (int i = 0; i < 255; i++)`（`UserService.java:488`）。 */
    private static final int UUID_SCAN_LIMIT = 255;
    private static final int UUID_USER_LIMIT = 256;

    private final IUserRepository userRepository;
    private final IUserDefaultAvatarRepository defaultAvatarRepository;

    @Override
    public DeleteUserOutcome deleteUser(String userId, UserCallerVO caller) {
        // **实测对拉纠正**：厂商控制器在删之前先调 `UserService.getById`，而 `getById` 抛的是
        // `user.id.not.found`（"查无此id的用户"，`UserService.java:1365`）——不是 `UserService.getDeleteUser`
        // 里那句 `user.not.found`（"用户不存在"）。后者在控制器路径上**是死代码**（只有服务层内部调用才到得了）。
        // 证据：2026-10-14 对拉 `DELETE /v1/user/nope`，厂商 msg = "查无此id的用户"。
        UserEntity target = userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.id.not.found"));

        if (ADMIN_USERNAME.equals(caller.getUsername())) {
            // 分支 1：admin 用户删 admin 用户 —— 厂商在这里才抛（也就是"admin 删自己"）
            if (ADMIN_USERNAME.equals(target.getUsername())) {
                throw new WrongArgumentException("role.can.not.delete.admin.key");
            }
            deleteCascade(target);
            return DeleteUserOutcome.DELETED;
        }

        if (caller.isAdminRole()) {
            // 分支 2：有 admin 战位的人不能删"带 admin 战位"的目标
            List<String> targetRoleKeys = userRepository.queryRoleKeysByUsername(target.getUsername());
            if (targetRoleKeys != null && targetRoleKeys.contains(ADMIN_ROLE_KEY)) {
                throw new WrongArgumentException("role.cannot.delete.op.admin");
            }
            deleteCascade(target);
            return DeleteUserOutcome.DELETED;
        }

        // 分支 3：**厂商返回 200 但不删**（UserController.java:357 返回 null，控制器照样 ok(true)）
        log.info("调用者 {} 既非 admin 用户也无 admin 战位：按厂商语义**不执行删除**，返回 200", caller.getUsername());
        return DeleteUserOutcome.NOOP_NOT_PERMITTED;
    }

    private void deleteCascade(UserEntity target) {
        userRepository.deleteUserCascade(target.getId());
        log.info("已删除用户 id={} username={}", target.getId(), target.getUsername());
    }

    @Override
    public UserEntity prepareNewUser(UserFormVO form) {
        String username = strip(form.getUsername());
        if (!StringUtils.hasText(username)) {
            throw new WrongArgumentException("user.username.not.empty");
        }
        if (USERNAME_BLACKLIST.matcher(username).matches()) {
            throw new WrongArgumentException("user.invalid.username.characters");
        }
        if (username.length() > USERNAME_MAX || username.length() < USERNAME_MIN) {
            throw new WrongArgumentException("user.username.invalid.length");
        }

        String password = strip(form.getPassword());
        if (!StringUtils.hasText(password)) {
            // 厂商控制器先判空（user.password.empty）；这里兜底成同一个键，避免走到 replaceAll 的 NPE
            throw new WrongArgumentException("user.password.empty");
        }
        if (password.length() > PASSWORD_MAX || password.length() < PASSWORD_MIN) {
            throw new WrongArgumentException("user.password.invalid.length");
        }

        if (form.getEmail() != null && form.getEmail().length() > EMAIL_MAX) {
            throw new WrongArgumentException("user.document.link.invalid.length");
        }
        if (form.getDesc() != null && form.getDesc().length() > DESC_MAX) {
            throw new WrongArgumentException("user.description.invalid.length");
        }

        Integer uuid = allocateUuid();
        String avatar = StringUtils.hasText(form.getAvatar())
                ? form.getAvatar()
                : defaultAvatarRepository.queryDefaultAvatar();

        long now = System.currentTimeMillis();
        return UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .nickname(form.getNickname())
                .password(BCrypt.hashpw(password, BCrypt.gensalt()))
                .status(form.getStatus())
                .email(form.getEmail())
                .birth(com.csm.paas.core.types.util.DateTimes.toLocalDate(form.getBirth()))
                .phone(form.getPhone())
                // 厂商：desc 为空白时置 **null**（新增）——注意修改时置的是空串 ""，见 UserService.java:630
                .desc(StringUtils.hasText(form.getDesc()) ? form.getDesc() : null)
                .avatar(avatar)
                .uuid(uuid)
                .createdAt(now)
                // 厂商 add 只设 createdAt、**不设 updatedAt**（UserService.java:507-513）⇒ 新建用户的
                // updatedAt 是 null（2026-10-14 对拉实测：厂商 POST/GET 两个响应里都是 null）。
                // 页面的"更新时间"列渲染 `updatedAt || createdAt`，所以显示不受影响，但**线值必须一致**。
                .updatedAt(null)
                .build();
    }

    /**
     * `uuid` 分配——逐字照抄厂商 `UserService.java:483-501` 的算法（含两个古怪之处）：
     * <ol>
     *   <li>无任何用户时结果是 **-1**（不是 0）；</li>
     *   <li>扫描 `i = 0..254`，取"排序后的 uuid 列表里第 i 位不等于 i"的最小 i；
     *       若 0..254 全被占满，循环走完**不设值**，结果仍是 **-1**。</li>
     * </ol>
     * 用户里带 uuid 的数量 &gt; 256 → `user.data.limit.exceeded`。
     */
    private Integer allocateUuid() {
        List<UserEntity> all = userRepository.queryAll();
        Integer defaultUuid = -1;
        if (all == null || all.isEmpty()) {
            return defaultUuid;
        }
        List<Integer> uuidList = all.stream()
                .map(UserEntity::getUuid)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (uuidList.size() > UUID_USER_LIMIT) {
            throw new WrongArgumentException("user.data.limit.exceeded");
        }
        for (int i = 0; i < UUID_SCAN_LIMIT; i++) {
            if (i > uuidList.size() - 1) {
                defaultUuid = i;
            } else if (uuidList.get(i) == i) {
                continue;
            } else {
                defaultUuid = i;
            }
            break;
        }
        return defaultUuid;
    }

    /** 厂商：`form.getXxx().replaceAll(" +", "")`——只去空格，不去其它空白。 */
    private static String strip(String value) {
        return value == null ? null : value.replaceAll(" +", "");
    }

    @Override
    public UserEntity prepareUpdatedUser(String userId, UserFormVO form) {
        UserEntity existing = userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.not.found"));

        // ---- 口令：空则保持，否则校验长度并重新哈希 ----
        String password = existing.getPassword();
        if (StringUtils.hasText(form.getPassword())) {
            String raw = form.getPassword();
            if (raw.length() > PASSWORD_MAX || raw.length() < PASSWORD_MIN) {
                throw new WrongArgumentException("user.change.password.invalid");
            }
            password = BCrypt.hashpw(raw, BCrypt.gensalt());
        }

        // ---- 用户名：空则保持，否则查重 + 长度 ----
        String username = existing.getUsername();
        if (StringUtils.hasText(form.getUsername())) {
            if (!existing.getUsername().equals(form.getUsername())
                    && userRepository.queryByUsername(form.getUsername()).isPresent()) {
                throw new WrongArgumentException("user.change.username.exist");
            }
            if (form.getUsername().length() > USERNAME_MAX || form.getUsername().length() < USERNAME_MIN) {
                throw new WrongArgumentException("user.change.username.invalid");
            }
            username = form.getUsername();
        }

        if (form.getEmail() != null && form.getEmail().length() > EMAIL_MAX) {
            throw new WrongArgumentException("user.mail.invalid");
        }
        if (form.getDesc() != null && form.getDesc().length() > DESC_MAX) {
            throw new WrongArgumentException("user.change.desc.invalid");
        }

        return UserEntity.builder()
                .id(existing.getId())
                .username(username)
                .nickname(form.getNickname() != null ? form.getNickname() : existing.getNickname())
                .password(password)
                .status(form.getStatus() != null ? form.getStatus() : existing.getStatus())
                // 厂商：修改时 desc 为空白置 **空串**（新增时置 null）——两处不同，照抄
                .desc(StringUtils.hasText(form.getDesc()) ? form.getDesc() : "")
                // 厂商：email/birth/phone **强制写回原值**（:716-718），所以这三个字段改不动
                .email(existing.getEmail())
                .birth(existing.getBirth())
                .phone(existing.getPhone())
                .avatar(StringUtils.hasText(form.getAvatar()) ? form.getAvatar() : existing.getAvatar())
                .uuid(existing.getUuid())
                .lastLoginRole(existing.getLastLoginRole())
                .createdAt(existing.getCreatedAt())
                .updatedAt(System.currentTimeMillis())
                .build();
    }
}
