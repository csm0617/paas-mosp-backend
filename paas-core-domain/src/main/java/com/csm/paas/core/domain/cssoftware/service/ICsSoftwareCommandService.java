package com.csm.paas.core.domain.cssoftware.service;

import java.io.IOException;
import java.util.List;

/**
 * CS 程序包上传与关联（厂商 `CsSoftwareUploadService` + `CsSoftwareController` 的两个端点）。
 */
public interface ICsSoftwareCommandService {

    /**
     * 上传 CS 程序 zip 包（厂商 `CsSoftwareUploadService.uploadCsSoftwareProgram:93-108`）。
     *
     * <p>流程：`realStoreFileName = now + "_" + originalFilename` → 落到本地 `${file.upload}`
     * → `ssh.createFolderPath(${path.save})` → 插一条上传记录
     * （`id=UUID`、`real_store_filename=now_name`、`exec_file_name=name`、`updated_at=now`、
     * `status=1`（`DatabaseRecordStatus.status(true)`）、`exec_fault_message=""`、
     * `relation_software_id=null`）→ 返回 true。
     *
     * <p>MIME 守卫（非 `application/zip` ⇒ 400 `global.upload.supported.file.type`）在 trigger
     * （Tika 不在 domain classpath 上）。
     *
     * @param content          zip 文件内容
     * @param originalFilename 原始文件名（厂商 `file.getOriginalFilename()`）
     */
    boolean uploadCsSoftwareProgram(byte[] content, String originalFilename) throws IOException;

    /**
     * 回滚/切换软件关联的 CS 程序包版本（厂商 `rollBackVersion:252-281`）。
     *
     * <p>照抄点：
     * <ol>
     *   <li>软件不存在 ⇒ 400 `software.not.found`；上传记录不存在 ⇒ 400 `client.missing.cs.upload.record`；</li>
     *   <li>在**本地** `${file.upload}` 目录里找 `realStoreFilename` 那个文件——厂商 `Ssh.getFile` 是
     *       **本地 File 查找**：目录不存在 ⇒ 返回 null ⇒ 400 `user.relate.software.not.exist`；
     *       目录存在但文件不在 ⇒ `list.get(0)` **越界 500**（照抄）；</li>
     *   <li>删远端 `<pathSave><software.key>` 目录 → 写 `version=uploadStoreRecordId` 并整行更新软件
     *       → 建目录 → 远端 unzip（`FilenameUtils.getBaseName` 语义）→ 返回 unzip 结果。</li>
     * </ol>
     */
    boolean rollBackVersion(String softwareId, String uploadStoreRecordId) throws IOException;

    /**
     * 把软件 `version` 指向该上传记录（厂商 `relateCsProgram:238-250`）。
     * <p>软件不存在 ⇒ **直接 return**（不报错）；上传记录不存在 ⇒ 400 `client.missing.cs.upload.file`。
     */
    void relateCsProgram(String softwareId, String uploadStoreRecordId);

    /**
     * 该软件关联的战位 id（厂商 `getRoleIdBySoftwareId:381-389`）：按 `created_at DESC`；
     * **无关联时返回空列表**（厂商那个 `list.isEmpty() || list == null` 是恒真判断，照抄注释）。
     */
    List<String> queryRoleIdsBySoftwareId(String softwareId);
}
