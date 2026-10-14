package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.image.adapter.port.IHarborPort;
import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;
import com.csm.paas.core.infrastructure.gateway.HarborGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Port 实现：只调 gateway，把 Harbor 的 JSON 翻成领域对象（防腐层）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarborPort implements IHarborPort {

    private final HarborGateway harborGateway;

    @Override
    public List<ProjectItemVO> listAllProjects(String keyword) {
        return harborGateway.listAllProjects(keyword).stream().map(HarborPort::toVO).toList();
    }

    private static ProjectItemVO toVO(Map<String, Object> m) {
        return ProjectItemVO.builder()
                .creationTime(str(m.get("creation_time")))
                .updateTime(str(m.get("update_time")))
                .togglable(bool(m.get("togglable")))
                .currentUserRoleId(intVal(m.get("current_user_role_id")))
                .metadata(m.get("metadata"))
                .deleted(intVal(m.get("deleted")))
                .ownerName(str(m.get("owner_name")))
                .projectId(intVal(m.get("project_id")))
                .ownerId(intVal(m.get("owner_id")))
                .name(str(m.get("name")))
                .repoCount(intVal(m.get("repo_count")))
                .build();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer intVal(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Boolean bool(Object v) {
        return v instanceof Boolean b ? b : null;
    }
}
