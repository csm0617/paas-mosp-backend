package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.service.IRoleQueryService;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.user.service.IUserAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 访问权限判定（规则见 {@link IUserAccessService}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccessService implements IUserAccessService {

    private final IRoleRepository roleRepository;
    private final ISoftwareRepository softwareRepository;
    private final IRoleQueryService roleQueryService;

    @Override
    public boolean checkAccessPermission(String username, String path) {
        // 厂商：for 循环里"后覆盖前" ⇒ 取**最后一个**战位
        String roleId = null;
        String roleName = null;
        for (var role : roleQueryService.queryRoleList(username, 1, Integer.MAX_VALUE).getList()) {
            roleId = role.getId();
            roleName = role.getKey();
        }

        List<String> allPathName = new ArrayList<>();
        if (roleId != null || "admin".equals(roleName)) {
            for (var rs : roleRepository.querySoftwareByRoleId(roleId)) {
                var software = softwareRepository.queryById(rs.getAppId());
                if (software.isEmpty()) {
                    continue;
                }
                String key = software.get().getKey();
                var pathNames = softwareRepository.queryServicePathsBySoftware(rs.getAppId());
                if (pathNames == null) {
                    continue;
                }
                for (String urlPath : pathNames) {
                    allPathName.add("/api/" + key + "/" + urlPath);
                }
            }
        }
        log.info("访问权限判定 username={} path={} 允许路径 {} 条", username, path, allPathName.size());
        return findPath(allPathName, path, roleName);
    }

    /**
     * 厂商 `UserService.findPath:210-228`。三个正则**照抄**：
     * `[tenx]`/`[role]`/`[gaop]` 都是**字符类**（匹配单个字符），不是单词——厂商原样，不要"顺手修正"。
     */
    private static boolean findPath(List<String> pathNameList, String path, String roleName) {
        String loginPath = "^[\\/]*[tenx]*[\\/]*[api]*[\\/]*[v2]*[\\/]*[a-z0-9]*$";
        String rolePath = "^[\\/]*[v1]*[\\/]*[role]*[\\/]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*$";
        String res = "^[\\/]*[api]?[\\/]*[gaop]*[\\/]*[v1]*[\\/]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[\\/]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*$";
        boolean loginResult = Pattern.compile(loginPath).matcher(path).matches();
        boolean roleResult = Pattern.compile(rolePath).matcher(path).matches();
        boolean resResult = Pattern.compile(res).matcher(path).matches();
        if ("admin".equals(roleName) && (loginResult || roleResult || resResult)) {
            return true;
        }
        if (path.isEmpty() || "/".equals(path)) {
            return true;
        }
        for (String pathName : pathNameList) {
            if (path.equals(pathName)) {
                return true;
            }
        }
        return false;
    }
}
