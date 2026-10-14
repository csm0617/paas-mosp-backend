package com.csm.paas.core.domain.subrole.service.impl;

import com.csm.paas.core.domain.language.adapter.port.ILanguageAutoNamePort;
import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.subrole.adapter.repository.ISubRoleRepository;
import com.csm.paas.core.domain.subrole.model.valobj.ImportSubRoleRowVO;
import com.csm.paas.core.domain.subrole.model.valobj.SubRoleFormVO;
import com.csm.paas.core.domain.subrole.service.ISubRoleCommandService;
import com.csm.paas.core.domain.subrole.service.ISubRoleImportService;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 子角色导入的落库实现（规则见 {@link ISubRoleImportService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubRoleImportServiceImpl implements ISubRoleImportService {

    private static final int MAX_NAME = 128;
    private static final int MAX_KEY = 36;

    private final ISubRoleRepository subRoleRepository;
    private final ISubRoleCommandService subRoleCommandService;
    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final ILanguageAutoNamePort autoNamePort;

    @Override
    public void importSubRoles(List<ImportSubRoleRowVO> rows, boolean isCover) {
        if (rows == null || rows.isEmpty()) {
            log.info("子角色表格无内容，或内容不匹配,直接返回成功");
            return;
        }
        for (ImportSubRoleRowVO row : rows) {
            String name = row.getName();
            if (name == null) {
                throw new WrongArgumentException("subrole.name.is.null.or.file.format.error");
            }
            if (name.length() > MAX_NAME || name.isEmpty()) {
                throw new WrongArgumentException("subrole.name.is.illegal", name);
            }
            String key = row.getKey();
            if (key == null) {
                throw new WrongArgumentException("subrole.key.is.null.or.format.error");
            }
            if (key.length() > MAX_KEY || key.length() < 1) {
                throw new WrongArgumentException("subrole.key.is.illegal", key);
            }
            // 厂商这里还有一句判空（subrole.import.key.not.valid），但上面两条长度检查已经把空串拦掉了
            if (key.isEmpty() || name.isEmpty()) {
                throw new WrongArgumentException("subrole.import.key.not.valid", row.getRowIndex());
            }
            String desc = row.getDesc();

            if (subRoleRepository.existsInRoleByKeyOrName(key, name)) {
                // 厂商：命中时记日志；isCover=false ⇒ 跳过整行
                if (!isCover) {
                    continue;
                }
            } else {
                SubRoleFormVO form = SubRoleFormVO.builder()
                        .key(key).name(name).desc(desc).userList(new ArrayList<>())
                        .build();
                subRoleCommandService.addSubRole(form);
            }

            var subRole = subRoleRepository.queryByKey(key)
                    .orElseThrow(() -> new WrongArgumentException(
                            "subrole.import.key.not.valid", row.getRowIndex()));
            // 厂商 `subrole.setDesc(desc); updateByPrimaryKey(subrole)`：整行 update，desc 可以是 null
            subRoleRepository.updateDesc(subRole.getId(), desc);

            replaceUsers(subRole.getId(), row.getUsernames());
            replaceRoles(subRole.getId(), row.getRoleKeys());
            saveAutoNames(key, row);
        }
    }

    /** cell2：用户名 → 用户 id（不存在的跳过），替换该子角色的用户关联。 */
    private void replaceUsers(String subRoleId, List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        List<String> userIds = new ArrayList<>();
        for (String username : usernames) {
            userRepository.queryByUsername(username).ifPresent(u -> userIds.add(u.getId()));
        }
        subRoleRepository.replaceUserSubRoleRelations(subRoleId, userIds);
    }

    /**
     * cell3：战位 key → id（不存在的跳过），替换"子角色-战位"关联；
     * 厂商还会把每个战位的 `updated_at` 覆盖成 now（照抄）。
     */
    private void replaceRoles(String subRoleId, List<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return;
        }
        List<String> roleIds = roleRepository.queryRoleIdsByKeys(roleKeys);
        subRoleRepository.replaceSubRoleRoleRelations(subRoleId, roleIds);
        long now = System.currentTimeMillis();
        for (String roleId : roleIds) {
            roleRepository.touchUpdatedAt(roleId, now);
        }
    }

    /** 写两个自动名（厂商 `saveAutoNameMessage`：值为 null ⇒ 不写）。 */
    private void saveAutoNames(String key, ImportSubRoleRowVO row) {
        if (row.getNameUs() != null) {
            autoNamePort.saveAutoName("sub_role", "en_US", key, row.getNameUs());
        }
        if (row.getNameFr() != null) {
            autoNamePort.saveAutoName("sub_role", "fr_FR", key, row.getNameFr());
        }
    }
}
