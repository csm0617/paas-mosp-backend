package com.csm.paas.core.domain.remote.adapter.port;

/**
 * SSH/SFTP 远程执行端口（厂商 `com.glriverside.gaop.be.Ssh`，JSch 实现）。
 *
 * <p>厂商在每个用到的地方都 `new Ssh(username, password, host)` → `setPort` → `connectSession()` →
 * 若干 exec → `disconnect()`。这里把"连接/断开"收进实现，域层只表达"在某节点上做什么"。
 *
 * <p><b>连接参数来自配置</b>：`gaop.ssh.host/port/username/password`
 * （厂商 `ssh.host/username/password/port`，`application.yml:71-75`）。
 * 本环境没有可 SSH 的节点 ⇒ **依赖本端口的端点属类 C**（代码齐全、此处不可验证）。
 *
 * <p>失败语义照抄厂商：连接不上/命令失败 ⇒ 抛运行时异常（厂商是 `JSchException` 直接冒到 500）。
 */
public interface ISshPort {

    /** 执行一条命令，返回 stdout（厂商 `ChannelExec` + `StreamUtils.copyToString`）。 */
    String exec(String command);

    /** `pgrep -f <execName>`（厂商 `Ssh.status:145-160`）：有输出即"在跑"。 */
    boolean processRunning(String execName);

    /**
     * 启动主程序（厂商 `Ssh.start:129-143`）：
     * `export DISPLAY=:0.0; cd <dir>; chmod +x <execName>; nohup ./<execName> >> <base>.log 2>&1 &`。
     */
    boolean startProcess(String dir, String execName);

    /** `pkill -f <execName>`（厂商 `Ssh.stop:113-127`）。 */
    boolean stopProcess(String execName);

    /** `unzip -o -O CP936 <dir>/<file> -d <dir>/<baseName>`（厂商 `Ssh.unzip:97-111`）。 */
    boolean unzip(String softwareDirPath, String file);

    /** SFTP 上传（厂商 `Ssh.scp:63-71` 用 exec 版 `scp -t`；这里用 SFTP 等价实现）。 */
    boolean upload(byte[] content, String remotePath);

    /** SFTP 下载（厂商 `Ssh.downloadFile:471-...`）。 */
    byte[] download(String remotePath);

    /** 删远端文件（厂商 `Ssh.delFile:411-...`）。 */
    boolean deleteFile(String remoteDir, String fileName);

    /** 远端文件是否存在（厂商 `Ssh.existRemoteFile:426-...`）。 */
    boolean exists(String remotePath);
}
