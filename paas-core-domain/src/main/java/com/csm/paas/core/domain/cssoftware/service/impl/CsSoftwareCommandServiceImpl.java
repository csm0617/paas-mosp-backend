package com.csm.paas.core.domain.cssoftware.service.impl;

import com.csm.paas.core.domain.cssoftware.service.ICsSoftwareCommandService;
import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import com.csm.paas.core.domain.software.adapter.repository.ISoftwareRepository;
import com.csm.paas.core.domain.software.model.entity.SoftwareEntity;
import com.csm.paas.core.domain.software.model.valobj.UploadStoreRecordVO;
import com.csm.paas.core.types.exception.SystemException;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CS 程序包上传/关联实现（规格：厂商 `CsSoftwareUploadService:93-108`、`:238-281`、`:381-389`）。
 *
 * <p><b>类 C：本环境不可验证</b>——SSH 相关路径（`createFolderPath` / `delSoftwareDir` / `unzip`）
 * 在本环境没有可连的节点；本地文件写入部分可离线验证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsSoftwareCommandServiceImpl implements ICsSoftwareCommandService {

    private final ISoftwareRepository softwareRepository;
    private final ISshPort sshPort;

    /** 厂商 `application.yml:108`：`file.upload: ${FILE_UPLOAD_DIR:/opt/gaop/upload/client}`。 */
    @Value("${file.upload:/opt/gaop/upload/client}")
    private String uploadDir;

    /** 厂商 `application.yml:112`：`path.save: ${PATH_SAVE:~/gaop-program/}`。 */
    @Value("${path.save:~/gaop-program/}")
    private String pathSave;

    @Override
    public boolean uploadCsSoftwareProgram(byte[] content, String originalFilename) throws IOException {
        long currentTime = System.currentTimeMillis();
        String realStoreFileName = currentTime + "_" + originalFilename;
        saveFile(content, realStoreFileName);
        // 厂商 getSsh() 是"本机"SSH（ssh.host/username/password），只建 pathSave 目录
        sshPort.mkdirs(pathSave);
        // 厂商 storeMangeService.addUploadRecord(name, currentTime, status(true), "", null)
        softwareRepository.insertUploadRecord(UUID.randomUUID().toString(), realStoreFileName,
                originalFilename, currentTime, 1, "", null);
        return true;
    }

    /**
     * 厂商 `CsSoftwareUploadService.saveFile:160-168`：`mkdirs` 失败 ⇒
     * 500 `global.save.file.failed`；然后用 `FileUtils.copyInputStreamToFile` 落盘。
     */
    private void saveFile(byte[] content, String realStoreFileName) throws IOException {
        File filePath = new File(uploadDir);
        if (!filePath.exists() && !filePath.mkdirs()) {
            throw new SystemException("global.save.file.failed");
        }
        File files = new File(filePath, realStoreFileName);
        Files.write(files.toPath(), content);
    }

    @Override
    public boolean rollBackVersion(String softwareId, String uploadStoreRecordId) throws IOException {
        SoftwareEntity software = softwareRepository.queryById(softwareId)
                .orElseThrow(() -> new WrongArgumentException("software.not.found"));
        UploadStoreRecordVO uploadStoreRecord = softwareRepository.queryUploadRecordById(uploadStoreRecordId)
                .orElseThrow(() -> new WrongArgumentException("client.missing.cs.upload.record"));
        String softwareDirPath = pathSave + software.getKey();
        String rollFile = uploadStoreRecord.getRealStoreFilename();
        // 厂商 Ssh.getFile 是**本地**查找（不是远端）：目录不存在 ⇒ null；文件缺失 ⇒ list.get(0) 越界
        File file = localGetFile(uploadDir, rollFile, uploadDir + File.separator + rollFile);
        if (file == null) {
            throw new WrongArgumentException("user.relate.software.not.exist");
        }
        sshPort.deleteDir(softwareDirPath);
        log.debug("删除成功");
        String localFile = file.getName();
        software.setVersion(uploadStoreRecordId);
        softwareRepository.update(software);
        sshPort.mkdirs(softwareDirPath);
        return sshPort.unzip(softwareDirPath, localFile);
    }

    /**
     * 厂商 `Ssh.getFile:364-379` 逐行照抄（**本地文件查找**）：
     * `<dirFile>` 不存在 ⇒ null；`<uploadDir>` 是目录时按名字过滤；最后 `list.get(0)`
     * ——**没有匹配项时越界异常**（厂商也没兜）。
     */
    private File localGetFile(String uploadDir, String fileName, String dirFile) {
        File file = new File(uploadDir);
        File dir = new File(dirFile);
        ArrayList<File> list = new ArrayList<>();
        if (!dir.exists()) {
            return null;
        }
        if (file.isDirectory()) {
            File[] flies = file.listFiles();
            for (File fly : flies) {
                if (!fly.getName().equals(fileName)) {
                    continue;
                }
                list.add(fly);
            }
        }
        return list.get(0);
    }

    @Override
    public void relateCsProgram(String softwareId, String uploadStoreRecordId) {
        var software = softwareRepository.queryById(softwareId);
        if (software.isEmpty()) {
            // 厂商：软件不存在直接 return（不报错）
            return;
        }
        if (softwareRepository.queryUploadRecordById(uploadStoreRecordId).isEmpty()) {
            log.error("uploadStoreRecordId:{}上传记录找不到", uploadStoreRecordId);
            throw new WrongArgumentException("client.missing.cs.upload.file");
        }
        SoftwareEntity entity = software.get();
        entity.setVersion(uploadStoreRecordId);
        softwareRepository.update(entity);
    }

    @Override
    public List<String> queryRoleIdsBySoftwareId(String softwareId) {
        // 厂商：`list.isEmpty() || list == null` 是恒真判断（列表非 null），这里保持"返回列表、可能为空"
        return softwareRepository.queryRoleIdsBySoftwareIdDesc(softwareId);
    }
}
