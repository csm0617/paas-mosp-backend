package com.csm.paas.core.domain.mode.service.impl;

import com.csm.paas.core.domain.mode.adapter.port.IModeChangedNotifyPort;
import com.csm.paas.core.domain.mode.adapter.repository.IModeRepository;
import com.csm.paas.core.domain.mode.model.entity.ModeEntity;
import com.csm.paas.core.domain.mode.service.IModeCommandService;
import com.csm.paas.core.domain.mode.service.IModeQueryService;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.model.valobj.DeploymentEnvVO;
import com.csm.paas.core.domain.software.model.valobj.EnvVarVO;
import com.csm.paas.core.types.exception.K8sApiException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作战模式写操作（规格：厂商 `ModeServiceImpl.startModeType:113-143` +
 * `changeModeTypeDB:145-149` + `updateServiceEnv:158-214`）。
 *
 * <p><b>与厂商的一处实现差异（已记录）</b>：厂商 `getListWithOutGaop` 的数据源是
 * `TenxCloud.allPageService(null)`（腾讯云 API，类 C 不可验证），我们用**同集群 Deployment 直读**
 * 等价复刻其过滤（见 `IServiceRuntimePort.listBusinessServiceDeploymentEnvs`）；
 * 该方法的"填充 restartCount / imageInfo / mappingInfo"部分对 `startModeType` **完全无用**
 * （`startModeType` 只读 `deployment.metadata.name` 与 `containers[0].env`），故不搬。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModeCommandServiceImpl implements IModeCommandService {

    private final IModeRepository modeRepository;
    private final IModeQueryService modeQueryService;
    private final IServiceRuntimePort serviceRuntimePort;
    private final IModeChangedNotifyPort modeChangedNotifyPort;

    /** 厂商 `tenxOpenApiConfig.getTeamSpace()`。 */
    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @Override
    public int startModeType(Long id) {
        log.info("----切换业务软件模式标签开始----");
        List<DeploymentEnvVO> listWithOutGaop =
                serviceRuntimePort.listBusinessServiceDeploymentEnvs(namespace);
        // 厂商 :117 `modeMapper.selectByPrimaryKey(id).get()`：不存在 ⇒ NoSuchElementException ⇒ 500（照抄）
        ModeEntity mode = modeRepository.queryById(id).get();
        for (DeploymentEnvVO servicesItem : listWithOutGaop) {
            List<EnvVarVO> env = servicesItem.getEnv();
            EnvVarVO modeEnvVar = EnvVarVO.builder().name("MODE").build();
            boolean changeFlag = false;
            if (env == null) {
                continue;
            }
            // 厂商 :125-131：**边遍历边 remove 的索引 bug** 照抄（remove 后 i 仍自增，会跳过下一个元素）
            for (int i = 0; i < env.size(); ++i) {
                EnvVarVO envVar = env.get(i);
                if (!"MODE".equals(envVar.getName()) || mode.getMode().equals(envVar.getValue())) {
                    continue;
                }
                modeEnvVar = envVar;
                env.remove(i);
                changeFlag = true;
            }
            if (!changeFlag) {
                continue;
            }
            log.info("修改应用服务模式,应用名：{},环境变量key:{},vaule:mode: ---> {}",
                    servicesItem.getServiceName(), "MODE", mode.getMode());
            modeEnvVar.setValue(mode.getMode());
            env.add(modeEnvVar);
            updateServiceEnv(servicesItem.getServiceName(), env);
            log.info("应用：{}服务模式切换部署命令完成", servicesItem.getServiceName());
        }
        changeModeTypeDB(id);
        ModeEntity currentMode = modeQueryService.queryCurrent()
                .orElseGet(() -> ModeEntity.builder().build());
        modeChangedNotifyPort.modeChanged(currentMode);
        return 1;
    }

    /** 厂商 `changeModeTypeDB:145-149`：全局清 0 再置 1（返回值被 `startModeType` 忽略）。 */
    public int changeModeTypeDB(Long id) {
        modeRepository.resetUsed();
        return modeRepository.setUsedById(id);
    }

    /**
     * 厂商 `updateServiceEnv:158-214` 的合并语义（K8s 调用在端口里，合并逻辑留在域层，
     * 以便保留厂商那段 catch/return false 的行为）：
     * <ol>
     *   <li>读 Deployment（读不到 ⇒ 400 `service.get.service.info.not.found`）；</li>
     *   <li>`env` 转 `name→value` map —— **重复 name 抛异常 ⇒ catch 后 return false**
     *       （调用方 `startModeType` **忽略**这个返回值，继续下一个服务，照抄）；</li>
     *   <li>live env 里同名的项 `setValue` 并从 map 移除；map 里剩下的**追加**到 live env 末尾
     *       （live env 为 null ⇒ `addAll` NPE ⇒ 500，照抄）；</li>
     *   <li>整份 patch：失败 ⇒ 400 `k8s.call.api.failed`（K8s 异常）/ `service.update.service.env.failed`（其它）。</li>
     * </ol>
     */
    public boolean updateServiceEnv(String serviceName, List<EnvVarVO> env) {
        DeploymentEnvVO live;
        try {
            live = serviceRuntimePort.readDeploymentEnv(namespace, serviceName);
        } catch (K8sApiException e) {
            log.info("获取服务deployment失败", e);
            throw new WrongArgumentException("service.get.service.info.not.found");
        }
        if (live == null) {
            // 厂商 readNamespacedDeployment 抛 ApiException 的分支
            throw new WrongArgumentException("service.get.service.info.not.found");
        }
        Map<String, String> map;
        try {
            map = env.stream().collect(Collectors.toMap(EnvVarVO::getName, EnvVarVO::getValue));
        } catch (Exception e) {
            log.error("遍历" + serviceName + "服务环境变量失败,异常:", e);
            return false;
        }
        List<EnvVarVO> envVars = live.getEnv();
        if (envVars != null && !envVars.isEmpty()) {
            for (EnvVarVO envVar : envVars) {
                if (!map.containsKey(envVar.getName())) {
                    continue;
                }
                envVar.setValue(map.get(envVar.getName()));
                map.remove(envVar.getName());
            }
        }
        List<EnvVarVO> envVarList = new ArrayList<>();
        if (!map.isEmpty()) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                envVarList.add(EnvVarVO.builder().name(entry.getKey()).value(entry.getValue()).build());
            }
        }
        // 厂商 :196 `envVars.addAll(envVarList)`：envVars 为 null 时 NPE ⇒ 500（照抄）
        envVars.addAll(envVarList);
        try {
            serviceRuntimePort.replaceDeploymentEnv(namespace, serviceName, envVars);
        } catch (K8sApiException e) {
            log.error("调用API失败: {}", e.getMessage());
            throw new WrongArgumentException("k8s.call.api.failed");
        } catch (Exception e) {
            log.error("更新服务环境变量失败", e);
            throw new WrongArgumentException("service.update.service.env.failed");
        }
        return true;
    }
}
