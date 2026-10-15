package com.csm.paas.core.domain.file.service.impl;

import com.csm.paas.core.domain.file.adapter.port.IFdfsFileStorePort;
import com.csm.paas.core.domain.file.adapter.port.IFileStorePort;
import com.csm.paas.core.domain.file.adapter.repository.IFileProjectRepository;
import com.csm.paas.core.domain.file.adapter.repository.IFileRepository;
import com.csm.paas.core.domain.file.model.entity.FileEntity;
import com.csm.paas.core.domain.file.service.IFileCommandService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件域写实现：MinIO 上传/删除照抄 {@code FileMinioService}，FastDFS 上传照抄 gaop-fdfs {@code FileService}。
 *
 * <p><b>类 C：本环境无 MinIO/FastDFS 对端，代码齐全但不做在线验证。</b>
 * 其中 FastDFS 上传的私有协议**未实现**（见 {@code IFdfsFileStorePort} 的 javadoc）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCommandServiceImpl implements IFileCommandService {

    private final IFileRepository fileRepository;
    private final IFileProjectRepository fileProjectRepository;
    private final IFileStorePort fileStorePort;
    private final IFdfsFileStorePort fdfsFileStorePort;

    // ================================================================ 上传

    /** 厂商 {@code FileMinioService.uploadFile:107-139}（MinIO 分支）。 */
    @Override
    public FileEntity uploadMinio(String projectId, byte[] content, String originalFilename) {
        long start = System.currentTimeMillis();
        long size = content.length;
        // 照抄怪点：MinIO 分支**不校验** projectId 指向的业务软件是否存在（fdfs 分支才校验）
        log.info("开始获取文件md5值");
        String md5Hex = md5Hex(content);
        log.info("获取文件md5值完成");
        String uploadFileUrl = fileStorePort.putObject(content, originalFilename);
        if (uploadFileUrl == null) {
            throw new WrongArgumentException("上传文件失败");
        }
        log.info("上传文件minio服务器完成，开始将文件信息插入数据库中");
        FileEntity file = FileEntity.builder()
                .id(UUID.randomUUID().toString())
                .fileProjectId(projectId)
                .path(uploadFileUrl)
                .name(originalFilename)
                .currentSize(size)
                .size(size)
                .md5(md5Hex)
                .createdAt(System.currentTimeMillis())
                .build();
        fileRepository.insertFileSelective(file);
        log.info("文件信息插入数据库完成");
        // 厂商此处是 Optional.get()：查不到直接抛 NoSuchElementException（⇒ 500 系统错误）
        FileEntity addFile = fileRepository.queryByPath(file.getPath()).orElseThrow();
        log.info("MultipartFile文件进行上传花费时间为{}毫秒", System.currentTimeMillis() - start);
        return addFile;
    }

    /** 厂商 {@code gaop-fdfs/.../service/FileService.uploadFile:495-533}（FastDFS 分支）。 */
    @Override
    public FileEntity uploadFdfs(String projectId, byte[] content, String originalFilename) {
        // 照抄：fdfs 分支先校验业务软件存在（**带 deleted=false**），查不到 ⇒ 400
        fileProjectRepository.queryByIdAndNotDeleted(projectId)
                .orElseThrow(() -> new WrongArgumentException("fdfs.upload.software.not.exist"));
        long start = System.currentTimeMillis();
        long size = content.length;
        // 厂商此处 initFastDfs()：私有协议侧的就绪检查，本仓未实现（类 C，见 IFdfsFileStorePort）
        log.info("先获取文件md5值");
        String md5Hex = md5Hex(content);
        log.info("获取文件md5值完成，开始上传文件到fdfs服务器上");
        // 本仓未实现 FastDFS 私有协议 ⇒ 这里必然抛 WrongArgumentException（见端口 javadoc）
        String uploadFileUrl = fdfsFileStorePort.upload(content, originalFilename);
        if (uploadFileUrl == null) {
            throw new WrongArgumentException("上传文件失败");
        }
        log.info("上传文件fdfs服务器完成，开始将文件信息插入数据库中");
        FileEntity file = FileEntity.builder()
                .id(UUID.randomUUID().toString())
                .fileProjectId(projectId)
                .path(uploadFileUrl)
                .name(originalFilename)
                .currentSize(size)
                .size(size)
                .md5(md5Hex)
                .createdAt(System.currentTimeMillis())
                .build();
        fileRepository.insertFileSelective(file);
        log.info("文件信息插入数据库完成");
        FileEntity addFile = fileRepository.queryByPath(file.getPath()).orElseThrow();
        log.info("MultipartFile文件进行上传花费时间为{}毫秒", System.currentTimeMillis() - start);
        return addFile;
    }

    // ================================================================ 删除

    /** 厂商 {@code FileMinioService.delFile:182-194}。 */
    @Override
    public Boolean deleteFile(String fileId) {
        Optional<FileEntity> file = fileRepository.queryById(fileId);
        if (file.isPresent()) {
            Boolean result = deleteObjectThenRow(file.get());
            log.info("删除结果:{}", result);
            return result;
        }
        throw new WrongArgumentException("fdfs.delete.file.failed");
    }

    /** 厂商 {@code FileMinioService.delFileList:196-211}。 */
    @Override
    public Boolean deleteFiles(List<String> fileIdList) {
        for (String fileId : fileIdList) {
            Optional<FileEntity> file = fileRepository.queryById(fileId);
            if (file.isPresent()) {
                Boolean result = deleteObjectThenRow(file.get());
                log.info("删除结果:{}", result);
                continue;
            }
            // 照抄：任何一个 id 查不到就立刻抛 400（前面已删的不回滚）
            throw new WrongArgumentException("fdfs.delete.file.failed");
        }
        return true;
    }

    /**
     * 厂商两处删除共用的那段：先删对象，成功才删库行；**对象删不掉就返回 false 且保留库行**
     * （不抛异常，前端拿到 {@code data:false}）。
     */
    private Boolean deleteObjectThenRow(FileEntity file) {
        Boolean result = fileStorePort.deleteObject(file.getPath());
        if (result) {
            fileRepository.deleteById(file.getId());
        }
        return result;
    }

    // ================================================================ md5

    /**
     * 厂商 {@code FileMinioService.getMd5Hex:268-271} / {@code FileService.getMd5Hex:346-349}
     * 用的 {@code org.apache.commons.codec.digest.DigestUtils.md5Hex(InputStream)}；
     * domain 模块没有 commons-codec，用 JDK 的 {@link MessageDigest} 等价实现
     * （与 {@code BackupServiceImpl.md5Hex} 同一做法）。
     */
    private static String md5Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return toHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 算法不可用", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
