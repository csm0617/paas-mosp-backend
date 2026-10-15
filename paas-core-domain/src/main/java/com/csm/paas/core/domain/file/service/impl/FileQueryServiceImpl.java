package com.csm.paas.core.domain.file.service.impl;

import com.csm.paas.core.domain.file.adapter.port.IFdfsFileStorePort;
import com.csm.paas.core.domain.file.adapter.port.IFileStorePort;
import com.csm.paas.core.domain.file.adapter.repository.IFileProjectRepository;
import com.csm.paas.core.domain.file.adapter.repository.IFileRepository;
import com.csm.paas.core.domain.file.model.entity.FileDownloadEntity;
import com.csm.paas.core.domain.file.model.entity.FileEntity;
import com.csm.paas.core.domain.file.model.entity.FileItemEntity;
import com.csm.paas.core.domain.file.model.entity.FileListEntity;
import com.csm.paas.core.domain.file.model.valobj.FdfsStorageNodeVO;
import com.csm.paas.core.domain.file.model.valobj.FileObjectStatVO;
import com.csm.paas.core.domain.file.model.valobj.FileRangeVO;
import com.csm.paas.core.domain.file.model.valobj.MinioStorageVO;
import com.csm.paas.core.domain.file.service.IFileQueryService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件域查询实现（MinIO 分支照抄 {@code FileMinioService}，FDFS 状态照抄 gaop-be {@code FileService}）。
 *
 * <p><b>类 C：本环境无 MinIO/FastDFS 对端，代码齐全但不做在线验证。</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileQueryServiceImpl implements IFileQueryService {

    /** 厂商 {@code gaop-be/.../service/FileService.java:138} 的 {@code DEFAULT_GROUP}。 */
    private static final String DEFAULT_GROUP = "group1";

    /** 厂商 {@code Storage.FDFS_STORAGE_STATUS_NONE = 99}。 */
    private static final int FDFS_STATUS_NONE = 99;

    private final IFileRepository fileRepository;
    private final IFileProjectRepository fileProjectRepository;
    private final IFileStorePort fileStorePort;
    private final IFdfsFileStorePort fdfsFileStorePort;

    /**
     * tracker 列表，照抄厂商 {@code @Value("${fdfs.trackers:...}")} 的形态
     * （{@code gaop-be/.../service/FileService.java:168-169}）。
     *
     * <p>厂商默认值来自 {@code recovered/gaop-fdfs/src/main/resources/application.yml:27}
     * （{@code FDFS_TRACKERS:192.168.1.172:22122,192.168.1.173:22122}）。
     * 本仓不允许改 application.yml，所以把默认值写进注解。
     */
    @Value("${gaop.fdfs.trackers:192.168.1.172:22122,192.168.1.173:22122}")
    private String trackers;

    // ================================================================ 列表

    /** 厂商 {@code FileMinioService.list:87-105} / {@code gaop-fdfs FileService.list:351-370}。 */
    @Override
    public FileListEntity queryFileList(Long start, Long end, String keyword, String projectId,
                                        int page, int size) {
        int startNumber = PageUtils.offset(page, size);
        // 厂商用 commons-lang3 的 StringUtils.isBlank：null/空串/纯空白都当"不过滤"，命中时先 trim
        String likeKeyword = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.trim() + "%";
        List<FileEntity> rows = fileRepository.queryFilePage(projectId, start, end, likeKeyword,
                startNumber, size);
        List<FileItemEntity> items = rows.stream()
                .map(f -> {
                    FileItemEntity item = FileItemEntity.builder()
                            .id(f.getId())
                            .name(f.getName())
                            .currentSize(f.getCurrentSize())
                            .size(f.getSize())
                            .md5(f.getMd5())
                            .fileProjectId(f.getFileProjectId())
                            .path(f.getPath())
                            .createdAt(f.getCreatedAt())
                            .updatedAt(f.getUpdatedAt())
                            .build();
                    // 厂商 selectByPrimaryKey（**不过滤 deleted**），查不到保持 projectName = null
                    fileProjectRepository.queryById(f.getFileProjectId())
                            .ifPresent(p -> item.setProjectName(p.getName()));
                    return item;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        // 厂商 countFileSize 只按 projectId 计数（忽略时间与关键字）
        Long total = fileRepository.countFilesByProjectId(projectId);
        return FileListEntity.builder().fileItems(items).total(total).build();
    }

    // ================================================================ 下载

    /** 厂商 {@code FileMinioService.download:141-175} 的前半段（选行 + 算 Range + stat）。 */
    @Override
    public FileDownloadEntity prepareDownload(String fileId, String range) {
        FileEntity file = fileRepository.queryByIdAndNotDeleted(fileId)
                .orElseThrow(() -> new WrongArgumentException("下载的文件不存在"));
        String fileName = file.getName();
        String objectName = file.getPath();
        Long size = file.getSize();

        FileRangeVO full = FileRangeVO.builder()
                .start(0L).end(size - 1L).fullLength(size).total(size).build();
        String finalRange = range;
        if (finalRange != null) {
            finalRange = "bytes=" + finalRange;
        }
        FileRangeVO downloadRange = getRange(finalRange, size, full);

        FileObjectStatVO stat = fileStorePort.statObject(objectName);
        long lastModified = file.getUpdatedAt() != null ? file.getUpdatedAt() : file.getCreatedAt();
        return FileDownloadEntity.builder()
                .objectName(objectName)
                .attachmentName(fileName)
                .contentType(stat.getContentType())
                .md5(file.getMd5())
                .lastModified(lastModified)
                .start(downloadRange.getStart())
                .end(downloadRange.getEnd())
                .fullLength(downloadRange.getFullLength())
                .total(downloadRange.getTotal())
                .rangeHeader(finalRange)
                .build();
    }

    /** 厂商 {@code FileMinioService.getRange:300-322}（与 {@code FileService.getRange} 同构）。 */
    private FileRangeVO getRange(String range, Long size, FileRangeVO full) {
        if (range == null) {
            return full;
        }
        if (!range.matches("^bytes=\\d*-\\d*$")) {
            throw new WrongArgumentException("fdfs.file.download.range.format.error", range);
        }
        String part = range.substring(6);
        int delimiterIndex = part.indexOf("-");
        long start = FileRangeVO.subLong(part, 0, delimiterIndex);
        long end = FileRangeVO.subLong(part, delimiterIndex + 1, part.length());
        if (start == -1L) {
            start = size - end;
            end = size - 1L;
        } else if (end == -1L || end > size - 1L) {
            end = size - 1L;
        }
        return FileRangeVO.builder()
                .start(start).end(end).fullLength(end - start + 1L).total(size).build();
    }

    // ================================================================ 存储状态

    /** 厂商 {@code FileMinioService.getStorageInfo:404-429}（MinIO Admin API，类 C）。 */
    @Override
    public List<MinioStorageVO> queryMinioStorage() {
        return fileStorePort.serverStorageInfo();
    }

    /** 厂商 {@code gaop-be/.../service/FileService.getFdfsStatusInfos:323-366}。 */
    @Override
    public List<FdfsStorageNodeVO> queryFdfsStatus() {
        List<FdfsStorageNodeVO> storageInfo = fdfsFileStorePort.listStorageInfo();
        if (storageInfo == null || storageInfo.isEmpty()) {
            // 厂商在这里先 initFastDfs()；私有协议侧效果本仓未实现（类 C，见 IFdfsFileStorePort）
            for (int i = 0; i < 3; i++) {
                if (i > 1) {
                    // 厂商：i > 1 时再 initFastDfs() 一次
                    log.info("进行重试请求获取fdfs存储状态信息,重试次数为:{}", i);
                }
                storageInfo = fdfsFileStorePort.listStorageInfo();
                if (storageInfo != null) {
                    // 照抄厂商：只判 null，空列表也算"成功"直接跳出
                    log.info("重试获取成功，跳出重复获取");
                    break;
                }
                log.info("重试获取失败，继续重新获取");
            }
        }
        if (storageInfo == null || storageInfo.isEmpty()) {
            // 厂商回落：给每个配置的 tracker 造一条 status=99 / 大小 0 / group1 的占位记录
            List<FdfsStorageNodeVO> storageList = new ArrayList<>();
            for (String ip : trackerList()) {
                String[] parts = ip.split(":", 2);
                storageList.add(FdfsStorageNodeVO.builder()
                        .ip(parts[0])
                        .status(FDFS_STATUS_NONE)
                        .group(DEFAULT_GROUP)
                        .freeSize(0L)
                        .totalSize(0L)
                        .percentMemoryLeft(0.0)
                        .build());
            }
            return storageList;
        }
        return storageInfo;
    }

    /** 厂商 {@code @Value("#{'${fdfs.trackers}'.split(',')}")}：只按逗号切，不 trim、不过滤空项。 */
    private List<String> trackerList() {
        if (trackers == null) {
            return List.of();
        }
        return Arrays.asList(trackers.split(","));
    }
}
