package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.software.adapter.port.ICsRemoteUploadPort;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * CS 程序包远端上传端口实现（JSch；厂商 `com.glriverside.gaop.be.Ssh`）。
 *
 * <p>照抄厂商的几条：
 * <ul>
 *   <li>连接参数是软件自己的 `remote_ip/remote_name/remote_password` + 配置里的 `ssh.port`；</li>
 *   <li>`StrictHostKeyChecking=no`、关 `userauth.gssapi-with-mic`、30 秒连接超时；</li>
 *   <li>建目录命令 `if [ ! -d &lt;parentDir&gt; ] ; then mkdir -p &lt;parentDir&gt;; fi`（失败只记日志）；</li>
 *   <li>上传走 SFTP（厂商用 exec 版 `scp -t`，两者都写同一个远端路径）；</li>
 *   <li>解压 `unzip -o -O CP936 &lt;parentDir&gt;/&lt;fileName&gt; -d &lt;parentDir&gt;/&lt;baseName&gt;`；</li>
 *   <li>最后 `rm -r &lt;parentDir&gt;/&lt;fileName&gt;`。</li>
 * </ul>
 *
 * <p><b>类 C：本环境不可验证</b>——没有可 SSH 的远端节点；连不上时按厂商语义抛运行时异常
 * （厂商 `JSchException` 直接冒到 500）。
 */
@Slf4j
@Component
public class CsRemoteUploadPort implements ICsRemoteUploadPort {

    @Value("${gaop.ssh.port:22}")
    private int port;

    @Override
    public boolean uploadZipAndUnzip(String host, String username, String password,
                                     String parentDir, String fileName, byte[] content) {
        Session session = connect(host, username, password);
        try {
            // 厂商 `Ssh.createFolderPath(parentDir)`（失败只记日志、不抛）
            execQuietly(session, "if [ ! -d " + parentDir + " ] ; then    mkdir -p " + parentDir + "; fi");
            // 厂商 `Ssh.scp(name, inputStream, size, parentDir + "/" + name)`
            ChannelSftp sftp = null;
            try {
                sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
                    sftp.put(in, parentDir + "/" + fileName);
                }
            } finally {
                if (sftp != null) {
                    sftp.disconnect();
                }
            }
            // 厂商 `Ssh.unzip(session, parentDir, name)`：解到 `<parentDir>/<baseName>`
            String baseName = baseName(fileName);
            execQuietly(session, "unzip -o -O  CP936 " + parentDir + "/" + fileName
                    + " -d " + parentDir + "/" + baseName);
            // 厂商 `Ssh.delFile(parentDir, name)`：`rm -r <parentDir>/<name>`
            execQuietly(session, "rm -r " + parentDir + "/" + fileName);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("CS 程序包远端上传失败: " + e.getMessage(), e);
        } finally {
            session.disconnect();
        }
    }

    /** 厂商 `Ssh.connectSession`：`getSession(username, host, port)` + 密码 + 两个 config + 30 秒。 */
    private Session connect(String host, String username, String password) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("userauth.gssapi-with-mic", "no");
            session.connect(30000);
            return session;
        } catch (Exception e) {
            throw new IllegalStateException("SSH 连接远端节点失败: " + e.getMessage(), e);
        }
    }

    /** 跑一条命令并读掉输出；异常只记日志（厂商 `createFolderPath`/`delFile` 都是这个语义）。 */
    private void execQuietly(Session session, String command) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            ByteArrayOutputStreamHolder out = new ByteArrayOutputStreamHolder();
            channel.setOutputStream(out.stream());
            channel.connect();
            while (!channel.isClosed()) {
                Thread.sleep(50);
            }
            log.debug("远端命令:{} 输出:{}", command, out.text());
        } catch (Exception e) {
            log.warn("远端命令执行失败:{} 异常:{}", command, e.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    /** 厂商用 commons-io `FilenameUtils.getBaseName`：去掉最后一个 `.` 之后的部分。 */
    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /** 读 exec 输出的简单载体。 */
    private static final class ByteArrayOutputStreamHolder {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        java.io.OutputStream stream() {
            return out;
        }

        String text() {
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
