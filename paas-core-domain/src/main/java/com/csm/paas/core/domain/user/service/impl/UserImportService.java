package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserDefaultAvatarRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.ImportUserRowVO;
import com.csm.paas.core.domain.user.service.IUserImportService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户导入。逐行规则见 {@link IUserImportService} 的文档；此处只写"照抄时最容易写错"的地方。
 *
 * <p><b>不加 {@code @Transactional}</b>：厂商 {@code importExcel} 没有事务，而且覆盖更新在解析循环里
 * 立刻落库、新用户插入在循环之后——中途报错会留下"覆盖已生效、插入全无"的中间态。照抄它。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportService implements IUserImportService {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ROOT_USERNAME = "root";
    private static final String ADMIN_ROLE_KEY = "admin";
    private static final String DEV_ROLE_KEY = "dev";
    /** 非 admin/dev 的导入行**自动追加**的战位（厂商 `userToRoleList.add("user")`，`:1203`）。 */
    private static final String DEFAULT_ROLE_KEY = "user";
    /** 覆盖分支取不到 col7 时的回落口令（厂商 `:1248`）。 */
    private static final String DEFAULT_IMPORT_PASSWORD = "123456";
    /** BCrypt 摘要前缀——厂商据此判断"这已经是哈希"（`:1112`、`:1255`）。 */
    private static final String BCRYPT_PREFIX = "$2a$";

    private static final int USERNAME_MIN = 2;
    private static final int USERNAME_MAX = 36;
    private static final int PASSWORD_MIN = 6;
    private static final int DESC_MAX = 255;
    /** 厂商用 `Pattern.matches("[01]", statusStr)`。 */
    private static final Pattern STATUS_PATTERN = Pattern.compile("[01]");

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final ISubRoleRepository subRoleRepository;
    private final IUserDefaultAvatarRepository defaultAvatarRepository;

    @Override
    public void importUsers(List<ImportUserRowVO> rows, boolean isCover) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        // 第一阶段 = 厂商的解析循环：逐行校验；**已存在 + 覆盖**的行在这里就把库改了（照抄的"半途落库"）。
        List<NewUser> toInsert = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportUserRowVO row = rows.get(i);
            int lineNo = i + 1; // 厂商的 i 从 1 开始（第 0 行是表头）
            String username = row.getUsername();
            if (!StringUtils.hasLength(username)) {
                log.error("导入用户：第 {} 行，导入的用户名为空，跳过导入", lineNo);
                continue;
            }
            if (ADMIN_USERNAME.equals(username) || ROOT_USERNAME.equals(username)) {
                continue;
            }
            if (userRepository.queryByUsername(username).isPresent()) {
                if (!isCover) {
                    continue;
                }
                log.info("row: {}， 用户名已存在", lineNo);
                coverUpdateUser(currentTime, lineNo, row, username);
                continue;
            }
            if (username.length() > USERNAME_MAX || username.length() < USERNAME_MIN) {
                throw new WrongArgumentException("user.invalid.username.length");
            }

            // col6：**单元格不存在**才报错；空串是"不设状态"（厂商 `StringUtils.isEmpty` 判定）
            String statusText = row.getStatusText();
            if (statusText == null) {
                throw new WrongArgumentException("user.import.status.must.not.null", username);
            }
            Integer status = null;
            if (!statusText.isEmpty()) {
                if (!STATUS_PATTERN.matcher(statusText).matches()) {
                    throw new WrongArgumentException("user.import.status.error", username);
                }
                status = Integer.valueOf(statusText);
            }

            // col7：`/import` 传的 isImportFullBackUp=false ⇒ 缺失或空白都抛 400（厂商 `:1099-1107`）
            String password = row.getPassword();
            if (password == null || password.isBlank()) {
                throw new WrongArgumentException("user.password.empty", username);
            }
            if (password.length() < PASSWORD_MIN) {
                log.error("导入用户: 第 {} 行数据,导入的用户密码长度少于6位数,导入跳过", lineNo);
                continue;
            }
            if (!password.startsWith(BCRYPT_PREFIX)) {
                password = BCrypt.hashpw(password, BCrypt.gensalt());
            }

            // col1：缺失 ⇒ 400（厂商 `:1124`；`isImportFullBackUp=false` 时不会走 break）
            String nickname = row.getNickname();
            if (nickname == null) {
                throw new WrongArgumentException("user.nickname.empty", username);
            }

            String desc = row.getDesc();
            if (desc != null && desc.length() > DESC_MAX) {
                throw new WrongArgumentException("user.invalid.user.description.length");
            }

            toInsert.add(new NewUser(username, nickname, password, status, desc,
                    // 厂商：`isNotBlank(cell2)` 才 setRoleKey ⇒ 空串在这里被"抹成 null"
                    StringUtils.hasText(row.getRoleKey()) ? row.getRoleKey() : null,
                    row.getUserToRole(), row.getSubRoles()));
        }

        if (toInsert.isEmpty()) {
            return;
        }
        // 第二阶段 = 厂商的写入循环
        String defaultAvatar = defaultAvatarRepository.queryDefaultAvatar();
        for (NewUser row : toInsert) {
            UserEntity user = UserEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .username(row.username())
                    .nickname(row.nickname())
                    .password(row.password())
                    .status(row.status())
                    .email(null)
                    .birth(null)
                    .phone(null)
                    .desc(row.desc())
                    .avatar(defaultAvatar)
                    .uuid(null)
                    .lastLoginRole(null)
                    .createdAt(currentTime)
                    // 厂商 `insertSelective` 不会写 updated_at ⇒ 新建用户 updated_at 是 NULL
                    .updatedAt(null)
                    .build();
            userRepository.insert(user);

            boolean inserted = userRepository.queryByUsername(row.username()).isPresent();
            if (inserted) {
                // ⚠️ 厂商的 `continue`（`:1190`）会**连子角色一起跳过**
                if (row.roleKey() == null) {
                    continue;
                }
                for (String roleId : resolveRoleIds(row.roleKey(), row.userToRole())) {
                    userRepository.insertUserRoleRelation(user.getId(), roleId, currentTime);
                }
            }
            if (!inserted || row.subRoles() == null || row.subRoles().isEmpty()) {
                continue;
            }
            for (String subRoleKey : row.subRoles()) {
                subRoleRepository.queryByKey(subRoleKey).ifPresent(subRole ->
                        userRepository.insertUserSubRoleRelation(
                                UUID.randomUUID().toString(), user.getId(), subRole.getId(), currentTime));
            }
        }
    }

    /**
     * 覆盖分支（厂商 {@code existUpdateUser}，`UserService.java:1235-1351`）。
     *
     * <p>四处照抄点：① 取不到 col7 ⇒ 默认口令 `123456`；② 长度 &lt;6 ⇒ **整行跳过**（已删的角色关系也不回滚）；
     * ③ {@code desc}/{@code nickname} 为 {@code null} ⇒ **保持原值**（`equalToWhenPresent` 的 present 就是 `!= null`）；
     * ④ col2 为 {@code null} 才跳过角色——**空串会走 else 写 `user` 角色**。
     */
    private void coverUpdateUser(long currentTime, int lineNo, ImportUserRowVO row, String username) {
        String password = row.getPassword();
        if (password == null) {
            log.error("导入用户: 第 {} 行数据,导入的用户密码异常,设置默认密码为 123456", lineNo);
            password = DEFAULT_IMPORT_PASSWORD;
        }
        if (password.length() < PASSWORD_MIN) {
            log.error("导入用户: 第 {} 行数据,导入的用户密码长度少于6位数,导入跳过", lineNo);
            return;
        }
        if (!password.startsWith(BCRYPT_PREFIX)) {
            password = BCrypt.hashpw(password, BCrypt.gensalt());
        }

        UserEntity existing = userRepository.queryByUsername(username).orElseThrow();
        // 厂商在更新用户**之前**就按 username 删光了角色关系（`:1275`）
        userRepository.deleteUserRoleRelationsByUsername(username);
        userRepository.update(UserEntity.builder()
                .id(existing.getId())
                .username(existing.getUsername())
                .password(password)
                .status(existing.getStatus())
                .email(existing.getEmail())
                .birth(existing.getBirth())
                .phone(existing.getPhone())
                .lastLoginRole(existing.getLastLoginRole())
                .nickname(row.getNickname() != null ? row.getNickname() : existing.getNickname())
                .uuid(existing.getUuid())
                .desc(row.getDesc() != null ? row.getDesc() : existing.getDesc())
                .avatar(existing.getAvatar())
                .createdAt(existing.getCreatedAt())
                // 厂商最后还会再 update 一次只为写 updated_at（`:1349`）⇒ 净效果就是这个值
                .updatedAt(currentTime)
                .build());
        userRepository.deleteUserSubRoleRelationsByUserId(existing.getId());

        if (row.getSubRoles() != null && !row.getSubRoles().isEmpty()) {
            for (String subRoleKey : row.getSubRoles()) {
                subRoleRepository.queryByKey(subRoleKey).ifPresent(subRole ->
                        userRepository.insertUserSubRoleRelation(
                                UUID.randomUUID().toString(), existing.getId(), subRole.getId(), currentTime));
            }
        }

        // ⚠️ 与新用户分支不同：这里判的是 `roleKey == null`（单元格缺失），空串会走 else 写 `user`
        String roleKey = row.getRoleKey();
        if (roleKey == null) {
            return;
        }
        for (String roleId : resolveRoleIds(roleKey, row.getUserToRole())) {
            userRepository.insertUserRoleRelation(existing.getId(), roleId, currentTime);
        }
    }

    /** 角色 key 列表：`admin`/`dev` ⇒ 只写自身；否则 ⇒ `user` + col3 列表（厂商两处分支同一套规则）。 */
    private List<String> resolveRoleIds(String roleKey, List<String> userToRole) {
        List<String> keys = new ArrayList<>();
        if (ADMIN_ROLE_KEY.equals(roleKey) || DEV_ROLE_KEY.equals(roleKey)) {
            keys.add(roleKey);
        } else {
            keys.add(DEFAULT_ROLE_KEY);
            if (userToRole != null) {
                keys.addAll(userToRole);
            }
        }
        List<String> ids = new ArrayList<>();
        // 逐 key 查（**不去重**——厂商逐个 select 再逐条 insert，col3 里出现两次同一 key 就插两行）
        for (String key : keys) {
            roleRepository.queryByKey(key).ifPresent(role -> ids.add(role.getId()));
        }
        return ids;
    }

    /** 解析阶段校验通过的"新用户行"（写入阶段用）。 */
    private record NewUser(String username, String nickname, String password, Integer status, String desc,
                           String roleKey, List<String> userToRole, List<String> subRoles) {
    }
}
