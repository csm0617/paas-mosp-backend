package com.csm.paas.core.domain.software.adapter.port;

/**
 * CS 程序包"上传到软件所在远端节点并解压"的端口（厂商 `CsSoftwareUploadService.uploadCsSoftwareProgramToRemoteServer:110-142`
 * 里 `new Ssh(...)` + `createFolderPath` + `scp` + `unzip` + `delFile` 那一段）。
 *
 * <p>与既有 `ISshPort` 的区别：厂商这里是**用软件自己的 `remote_name`/`remote_password`/`remote_ip`
 * 新建一个 Ssh**，而 `ISshPort` 只连配置里那台 `gaop.ssh.host`，所以单开一个端口。
 * 端口号仍取厂商配置 `ssh.port`（`gaop.ssh.port`）。
 *
 * <p><b>类 C：本环境不可验证</b>——没有可 SSH 的远端节点。
 */
public interface ICsRemoteUploadPort {

    /**
     * 把 zip 传到 `&lt;parentDir&gt;/&lt;fileName&gt;`，解压到 `&lt;parentDir&gt;/&lt;fileName 去扩展名&gt;`，
     * 最后删掉上传的 zip。
     *
     * <p>厂商步骤（`Ssh.uploadCsSoftwareProgramToRemoteServer`）：先 `createFolderPath(parentDir)`，
     * 再 `scp(name, stream, size, parentDir + "/" + name)`，再 `connectSession()` 后
     * `unzip(parentDir, name)`（`unzip -o -O CP936 &lt;parentDir&gt;/&lt;name&gt; -d &lt;parentDir&gt;/&lt;base&gt;`），
     * 最后 `delFile(parentDir, name)`。
     *
     * @return 解压是否成功（厂商在"session 未连接"时返回 false）
     */
    boolean uploadZipAndUnzip(String host, String username, String password,
                              String parentDir, String fileName, byte[] content);
}
