package com.csm.paas.core.domain.software.service;

/**
 * 把 CS 程序包上传到软件所在远端节点（厂商 `CsSoftwareUploadService.uploadCsSoftwareProgramToRemoteServer:110-142`，
 * 由 `SoftwareController.uploadCsSoftwareToRemoteServer:154-158` 调起）。
 */
public interface ISoftwareRemoteCsService {

    /**
     * 上传 CS 程序 zip 到远端并解压。
     *
     * <p>厂商顺序照抄：
     * <ol>
     *   <li>`softwareId == null` ⇒ 用厂商那个写死的 UUID
     *       `abe38548-6965-412c-a732-0e06e28ceae7`；</li>
     *   <li>`softwareMapper.selectByPrimaryKey(id).get()`——**软件不存在直接
     *       NoSuchElementException（500）**，没有 400；</li>
     *   <li>`rootDir = execName.split("/")`，`parentDir = "/" + rootDir[1]`
     *       （**取第 2 段**，照抄；`execName` 为空时越界 500）；</li>
     *   <li>`location == "local"` ⇒ 直接返回 false（不上传）；</li>
     *   <li>`ssh.createFolderPath(parentDir)` → `scp` 到 `parentDir/&lt;原文件名&gt;` →
     *       `unzip` 到 `parentDir/&lt;去扩展名的文件名&gt;` → `delFile` 删掉 zip；</li>
     *   <li>解压失败返回 false。</li>
     * </ol>
     *
     * <p>MIME 守卫（Tika 必须 `application/zip`，否则 400 `global.upload.supported.file.type`）
     * 在 trigger（Tika 不在 domain classpath 上）。
     *
     * <p><b>类 C：本环境不可验证</b>（SSH 到远端节点）。
     *
     * @param content          zip 文件内容
     * @param originalFilename 原始文件名（厂商 `file.getOriginalFilename()`）
     * @param softwareId       路径变量（可为 null）
     */
    boolean uploadCsSoftwareToRemoteServer(byte[] content, String originalFilename, String softwareId);
}
