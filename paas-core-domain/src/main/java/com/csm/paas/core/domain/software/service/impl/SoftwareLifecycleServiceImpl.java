package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import com.csm.paas.core.domain.software.adapter.port.IServiceRuntimePort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareInfoEntity;
import com.csm.paas.core.domain.software.model.valobj.WorkloadStatus;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import com.csm.paas.core.domain.software.service.ISoftwareDetailService;
import com.csm.paas.core.domain.software.service.ISoftwareLifecycleService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 软件启停与状态（规格见 {@link ISoftwareLifecycleService}）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareLifecycleServiceImpl implements ISoftwareLifecycleService {

    private static final String CS = "CS";

    private final ISoftwareDetailService softwareDetailService;
    private final ISoftwareRepository softwareRepository;
    private final IServiceRuntimePort serviceRuntimePort;
    private final IServiceQueryService serviceQueryService;
    private final ISshPort sshPort;

    /** 厂商 `path.save`（`application.yml:111-112`，默认 `~/gaop-program/`）。 */
    @Value("${path.save:~/gaop-program/}")
    private String pathSave;

    @Override
    public String queryStatus(String namespace, String softwareId) {
        SoftwareInfoEntity info = softwareDetailService.queryDetail(softwareId);
        var software = info.getSoftware();
        if (CS.equals(software.getFramework())) {
            String execName = software.getExecName();
            if (!StringUtils.hasText(execName)) {
                log.warn("主程序文件名不能为空, software key = {}", software.getKey());
                return "STOP";
            }
            return sshPort.processRunning(execName.trim()) ? "RUNNING" : "STOP";
        }
        return softwareStatus(namespace, softwareId);
    }

    @Override
    public String start(String namespace, String softwareId) {
        SoftwareInfoEntity info = softwareDetailService.queryDetail(softwareId);
        var software = info.getSoftware();
        if (CS.equals(software.getFramework())) {
            if (info.getUploadStoreRecord() == null) {
                return "STOP";
            }
            String execName = software.getExecName();
            if (!StringUtils.hasText(execName)) {
                log.warn("主程序文件名不能为空, software key = {}", software.getKey());
                return "STOP";
            }
            String exec = execName.trim();
            if (sshPort.processRunning(exec)) {
                return "RUNNING";
            }
            String realStoreFilename = info.getUploadStoreRecord().getRealStoreFilename();
            String baseName = realStoreFilename == null ? "" : basename(realStoreFilename);
            sshPort.startProcess(pathSave + software.getKey() + "/" + baseName, exec);
            return sshPort.processRunning(exec) ? "RUNNING" : "STOP";
        }
        List<String> toStart = filterByStatus(namespace, softwareId, false);
        if (toStart.isEmpty()) {
            return "RUNNING";
        }
        try {
            serviceQueryService.batchStartServices(namespace, toStart);
        } catch (Exception e) {
            log.error("Start k8s resource failed：{}", e.toString());
            throw new WrongArgumentException("software.start.bs.service.failed");
        }
        return "RUNNING";
    }

    @Override
    public String stop(String namespace, String softwareId) {
        SoftwareInfoEntity info = softwareDetailService.queryDetail(softwareId);
        var software = info.getSoftware();
        if (CS.equals(software.getFramework())) {
            if (info.getUploadStoreRecord() == null) {
                return "STOP";
            }
            String execName = software.getExecName();
            if (!StringUtils.hasText(execName)) {
                throw new WrongArgumentException("software.cs.main.file.must.not.null");
            }
            String exec = execName.trim();
            if (!sshPort.processRunning(exec)) {
                return "STOP";
            }
            sshPort.stopProcess(exec);
            return sshPort.processRunning(exec) ? "RUNNING" : "STOP";
        }
        List<String> toStop = filterByStatus(namespace, softwareId, true);
        if (toStop.isEmpty()) {
            return "STOP";
        }
        serviceQueryService.batchStopServices(namespace, toStop);
        return "STOP";
    }

    /**
     * 厂商 `start`/`stop` 的 BS 分支：把该软件的服务按当前状态筛一遍
     * （`stopWhenRunning=true` ⇒ 取"非 STOP"的；`false` ⇒ 取"非 RUNNING"的）。
     */
    private List<String> filterByStatus(String namespace, String softwareId, boolean stopWhenRunning) {
        List<String> result = new ArrayList<>();
        for (String serviceName : softwareRepository.queryAppNamesBySoftwareId(softwareId)) {
            var state = serviceRuntimePort.readWorkloadState(namespace, serviceName);
            String status = WorkloadStatus.of(state);
            if (stopWhenRunning && !"STOP".equals(status)) {
                result.add(serviceName);
            } else if (!stopWhenRunning && !"RUNNING".equals(status)) {
                result.add(serviceName);
            }
        }
        return result;
    }

    /** 厂商 `getSoftwareStatus(list)`：无服务 ⇒ STOP；全 RUNNING ⇒ RUNNING；部分 ⇒ `PART_RUN`。 */
    private String softwareStatus(String namespace, String softwareId) {
        List<String> services = softwareRepository.queryAppNamesBySoftwareId(softwareId);
        if (services.isEmpty()) {
            return "STOP";
        }
        int running = 0;
        for (String serviceName : services) {
            var state = serviceRuntimePort.readWorkloadState(namespace, serviceName);
            if ("RUNNING".equals(WorkloadStatus.of(state))) {
                running++;
            }
        }
        if (running == 0) {
            return "STOP";
        }
        return running == services.size() ? "RUNNING" : "PART_RUN";
    }

    /** 厂商 `FilenameUtils.getBaseName` 的等价实现。 */
    private static String basename(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
