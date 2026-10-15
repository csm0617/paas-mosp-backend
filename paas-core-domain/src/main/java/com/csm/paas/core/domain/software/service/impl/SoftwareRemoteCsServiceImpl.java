package com.csm.paas.core.domain.software.service.impl;

import com.csm.paas.core.domain.software.adapter.port.ICsRemoteUploadPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.service.ISoftwareRemoteCsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CS 程序包远端上传实现（规格：厂商 `CsSoftwareUploadService.uploadCsSoftwareProgramToRemoteServer:110-142`）。
 *
 * <p><b>类 C：本环境不可验证</b>（SSH 到远端节点）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareRemoteCsServiceImpl implements ISoftwareRemoteCsService {

    /** 厂商写死的默认软件 id（`uploadCsSoftwareProgramToRemoteServer:111-113`）。 */
    private static final String DEFAULT_SOFTWARE_ID = "abe38548-6965-412c-a732-0e06e28ceae7";

    private final ISoftwareRepository softwareRepository;
    private final ICsRemoteUploadPort csRemoteUploadPort;

    @Override
    public boolean uploadCsSoftwareToRemoteServer(byte[] content, String originalFilename, String softwareId) {
        String id = softwareId == null ? DEFAULT_SOFTWARE_ID : softwareId;
        // 厂商 `softwareMapper.selectByPrimaryKey(id).get()`：不存在就是 NoSuchElementException
        SoftwareEntity software = softwareRepository.queryById(id)
                .orElseThrow(java.util.NoSuchElementException::new);
        String execName = software.getExecName();
        String[] rootDir = execName.split("/");
        log.info("rootDir{}", java.util.Arrays.toString(rootDir));
        if ("local".equals(software.getLocation())) {
            return false;
        }
        // 厂商：parentDir = "/" + rootDir[1]（取第 2 段；execName 为空时越界 500）
        String parentDir = "/" + rootDir[1];
        return csRemoteUploadPort.uploadZipAndUnzip(software.getRemoteIp(), software.getRemoteName(),
                software.getRemotePassword(), parentDir, originalFilename, content);
    }
}
