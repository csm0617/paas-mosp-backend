package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.remote.adapter.port.ISshPort;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SSH/SFTP 端口实现（JSch）。配置：`gaop.ssh.host/port/username/password`。
 *
 * <p>照抄厂商 `Ssh` 的几个点：
 * <ul>
 *   <li>`StrictHostKeyChecking=no` 且关掉 `userauth.gssapi-with-mic`；</li>
 *   <li>连接超时 30 秒；</li>
 *   <li>每条命令一个 `exec` channel，用完 `disconnect`（**不**复用 session —— 照抄，简单优先）；</li>
 *   <li>`processRunning` 判据是"`pgrep` 有输出"。 </li>
 * </ul>
 *
 * <p><b>类 C</b>：本环境没有可 SSH 的节点，这条链路在此处必然失败——代码齐全，不做在线验证。
 */
@Slf4j
@Component
public class SshPort implements ISshPort {

    @Value("${gaop.ssh.host:localhost}")
    private String host;

    @Value("${gaop.ssh.port:22}")
    private int port;

    @Value("${gaop.ssh.username:root}")
    private String username;

    @Value("${gaop.ssh.password:123456}")
    private String password;

    private Session connect() throws JSchException {
        return connect(host);
    }

    /** 厂商 `new SshUtil(username, password, <host>)` + `setPort` + `connectSession(client, 30)` 的等价物。 */
    private Session connect(String targetHost) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, targetHost, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("userauth.gssapi-with-mic", "no");
        session.connect(30000);
        return session;
    }

    @Override
    public String exec(String command) {
        return runOn(host, command);
    }

    @Override
    public String execOnHost(String targetHost, String command) {
        return runOn(targetHost, command);
    }

    /**
     * 在指定主机上跑一条命令（厂商 `SshUtil.getRunCommand(session, cmd)` 的位置：
     * exec channel + 读全量 stdout + `replaceAll("\n","")` 由调用方决定，这里保留原始输出）。
     */
    private String runOn(String targetHost, String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            session = connect(targetHost);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command + "\n");
            channel.setInputStream(null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.setOutputStream(out);
            channel.connect();
            while (!channel.isClosed()) {
                Thread.sleep(50);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (JSchException | InterruptedException e) {
            throw new IllegalStateException("SSH 执行失败: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public boolean processRunning(String execName) {
        return !exec("pgrep -f " + execName).trim().isEmpty();
    }

    @Override
    public boolean startProcess(String dir, String execName) {
        String base = execName.contains(".") ? execName.substring(0, execName.lastIndexOf('.')) : execName;
        exec("export DISPLAY=:0.0\ncd " + dir + "\nchmod +x " + execName
                + "\nnohup ./" + execName + " >> " + base + ".log 2>&1 &");
        return true;
    }

    @Override
    public boolean stopProcess(String execName) {
        exec("pkill -f " + execName);
        return true;
    }

    @Override
    public boolean unzip(String softwareDirPath, String file) {
        String base = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        exec("unzip -o -O CP936 " + softwareDirPath + "/" + file + " -d " + softwareDirPath + "/" + base);
        return true;
    }

    @Override
    public boolean upload(byte[] content, String remotePath) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = connect();
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
                sftp.put(in, remotePath);
            }
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("SFTP 上传失败: " + e.getMessage(), e);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public byte[] download(String remotePath) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = connect();
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sftp.get(remotePath, out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("SFTP 下载失败: " + e.getMessage(), e);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public boolean deleteFile(String remoteDir, String fileName) {
        exec("rm -f " + remoteDir + "/" + fileName);
        return true;
    }

    @Override
    public boolean exists(String remotePath) {
        return !exec("test -f " + remotePath + " && echo 1").trim().isEmpty();
    }

    /**
     * 厂商 `Ssh.createFolderPath:456-469`：`if [ ! -d <path> ] ; then mkdir -p <path>; fi`。
     * <p>厂商把 `JSchException` catch 掉只记日志；我们让 `exec` 的异常按既有语义冒出去
     * （该端口其它方法也是这样）——**注意这一处比厂商更严格**，记录在报告里。
     */
    @Override
    public void mkdirs(String path) {
        exec("if [ ! -d " + path + " ] ; then    mkdir -p " + path + "; fi");
    }

    /** 厂商 `Ssh.delSoftwareDir:396-409`：`if [ -d <path> ];then rm -r <path>; fi`。 */
    @Override
    public void deleteDir(String path) {
        exec("if [ -d " + path + " ];then rm -r " + path + "; fi");
    }
}
