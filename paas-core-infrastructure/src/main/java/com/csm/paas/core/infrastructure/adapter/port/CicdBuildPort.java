package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.cicd.adapter.port.ICicdBuildPort;
import com.csm.paas.core.domain.cicd.model.valobj.CicdBuildJobVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobPodVO;
import com.csm.paas.core.domain.cicd.model.valobj.CicdJobStatusVO;
import com.csm.paas.core.infrastructure.gateway.CicdBuildGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Port 实现：只调 {@link CicdBuildGateway}。
 *
 * <p><b>类 C：本环境不可验证</b>（见 {@link ICicdBuildPort} 说明）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CicdBuildPort implements ICicdBuildPort {

    private final CicdBuildGateway cicdBuildGateway;

    @Override
    public Map<String, Object> createBuildJob(String namespace, CicdBuildJobVO job) {
        return cicdBuildGateway.createBuildJob(namespace, job);
    }

    @Override
    public CicdJobStatusVO readJobStatus(String namespace, String jobName) {
        CicdJobStatusVO status = cicdBuildGateway.readJobStatus(namespace, jobName);
        if (status == null) {
            log.error("获取job信息失败: {}", jobName);
        }
        return status;
    }

    @Override
    public List<CicdJobPodVO> listBuildPods() {
        return cicdBuildGateway.listBuildPods();
    }

    @Override
    public String readJobLog(String namespace, String jobName) {
        return cicdBuildGateway.readJobLog(namespace, jobName);
    }
}
