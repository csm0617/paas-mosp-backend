package com.csm.paas.core.domain.software.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一个"业务软件"Deployment 的身份 + 第一个容器的 env
 * （厂商 `ModeServiceImpl.getListWithOutGaop` 过滤出的 `ServicesItem`，
 * `startModeType:119-120` 只用到 `deployment.metadata.name` 与
 * `deployment.spec.template.spec.containers[0].env`）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentEnvVO {

    private String serviceName;

    /** **null 表示容器没有 env 字段**（厂商 `if (env == null) continue;`）。 */
    private List<EnvVarVO> env;
}
