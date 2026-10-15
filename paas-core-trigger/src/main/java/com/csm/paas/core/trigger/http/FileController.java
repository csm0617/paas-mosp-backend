package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.FdfsStorageInfoDTO;
import com.csm.paas.core.api.dto.FileInfoDTO;
import com.csm.paas.core.api.dto.FileItemDTO;
import com.csm.paas.core.api.dto.FileListDTO;
import com.csm.paas.core.api.dto.MinioStorageInfoDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.file.adapter.port.IFileStorePort;
import com.csm.paas.core.domain.file.model.entity.FileDownloadEntity;
import com.csm.paas.core.domain.file.model.entity.FileEntity;
import com.csm.paas.core.domain.file.model.entity.FileItemEntity;
import com.csm.paas.core.domain.file.model.entity.FileListEntity;
import com.csm.paas.core.domain.file.model.valobj.FdfsStorageNodeVO;
import com.csm.paas.core.domain.file.model.valobj.MinioStorageVO;
import com.csm.paas.core.domain.file.service.IFileCommandService;
import com.csm.paas.core.domain.file.service.IFileQueryService;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 公共文件端点（厂商 {@code /v1/pub} 前缀下的文件簇）。
 *
 * <p>规格来源是厂商的**独立微服务**，不在 gaop-be：
 * <ul>
 *   <li>{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/controller/PublicMinioController.java}
 *       ——{@code /file/list}（:59-71）、{@code /file/upload/{projectId}}（:73-80）、
 *       {@code /file/download/{fileId}}（:82-86）、{@code /file/{fileId}}（:88-93）、
 *       {@code /file/delete}（:95-101）、{@code /file/storage}（:124-128）；</li>
 *   <li>{@code recovered/gaop-fdfs/.../PublicFdfsController.java} 的 {@code /fdfs/upload/{projectId}}（:61-67），
 *       以及 {@code recovered/gaop-be/.../controller/pub/PublicFdfsController.java} 的同名端点（:92-98）
 *       ——两边是同一套逻辑，gaop-be 那份是 {@code @Deprecated} 的副本；</li>
 *   <li>{@code recovered/gaop-be/.../controller/pub/PublicFdfsController.java} 的 {@code /fdfs/status}（:53-58）
 *       ——这一条只在 gaop-be 有。</li>
 * </ul>
 *
 * <p><b>不放进 PubController</b>：那个类覆盖的是节点/集群/运维簇，文件簇独立成类。
 *
 * <p>照抄点（逐条）：
 * <ul>
 *   <li>列表的 {@code end} 参数在 controller 里做"取当天本地时间再 +1 天"的换算
 *       （{@code PublicMinioController.java:64-68}），换算只在 start/end 都非空时发生；</li>
 *   <li>删除路径变量厂商命名 {@code fileId}（不是 id），URL 形状 {@code /v1/pub/file/{fileId}} 不变；</li>
 *   <li>{@code /file/delete} 的 body 是**裸字符串数组**（{@code List<String>}），不是对象；</li>
 *   <li>下载响应头的取值与顺序照抄 {@code FileMinioService.download:158-175} +
 *       {@code MinioClientUtil.handleFullDownload:81-86}/{@code handleRangeRequest:88-127}，
 *       包含"206 分支用 {@code fullLength} 当文件总长"这个怪点；</li>
 *   <li>{@code /file/storage} 与 {@code /fdfs/status} 的响应都是**数组**，字段名一致但大小字段类型不同
 *       （MinIO 是 Double，FDFS 是 long），所以是两个 DTO。</li>
 * </ul>
 *
 * <p><b>类 C：本环境无 MinIO/FastDFS 对端，代码齐全但不做在线验证。</b>
 * 其中 FastDFS 上传的私有协议未实现（见 {@code IFdfsFileStorePort} 的 javadoc）。
 * {@code /file/storage} 依赖 MinIO Admin API（不是 S3），取不到时返回空数组（厂商同形）。
 */
@Slf4j
@RestController
@RequestMapping("/v1/pub")
@RequiredArgsConstructor
public class FileController {

    /** 厂商 {@code ResponseUtils.attachment:16-22}：{@code attachment; filename*=UTF-8''<urlencoded>}。 */
    private static final String ATTACHMENT_PREFIX = "attachment; filename*=UTF-8''";

    /** 厂商 {@code FileMinioService.download:166}：{@code Expires} 是当前时间 + 7 天。 */
    private static final long EXPIRES_AFTER_MILLIS = 604800000L;

    private final IFileQueryService fileQueryService;
    private final IFileCommandService fileCommandService;

    /** 下载的字节流由 trigger 层直接向对象存储端口取（domain 不依赖 servlet）。 */
    private final IFileStorePort fileStorePort;

    // ================================================================ 列表

    /**
     * 厂商 {@code GET /v1/pub/file/list}（{@code PublicMinioController.java:59-71}）。
     *
     * <p>照抄点：{@code projectId} **必填**；{@code end} 先按 {@code +08:00} 还原成本地时间再 +1 天
     * 再变回毫秒；分页归一化用厂商的 {@code SqlUtils} 语义（{@code page=0} 原样返回、{@code size<=0} 变 100）。
     */
    @GetMapping("/file/list")
    public ResponseDTO<FileListDTO> getFileList(
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = true) String projectId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int normalizedPage = PageUtils.normalizePage(page);
        int normalizedSize = PageUtils.normalizeSize(size);
        if (start != null && end != null) {
            LocalDateTime endLocal = LocalDateTime.ofEpochSecond(end / 1000L, 0, ZoneOffset.ofHours(8));
            end = endLocal.plusDays(1L).toEpochSecond(ZoneOffset.ofHours(8)) * 1000L;
        }
        FileListEntity result = fileQueryService.queryFileList(
                start, end, keyword, projectId, normalizedPage, normalizedSize);
        List<FileItemDTO> items = result.getFileItems().stream()
                .map(FileController::toFileItemDTO)
                .toList();
        return ResponseDTO.ok(FileListDTO.builder().fileItems(items).total(result.getTotal()).build());
    }

    // ================================================================ 上传

    /**
     * 厂商 {@code POST /v1/pub/file/upload/{projectId}}（{@code PublicMinioController.java:73-80}，
     * multipart 表单，part 名 {@code file}）。
     *
     * <p>照抄点：这个方法链上**不校验 projectId 是否存在**（fdfs 分支才校验）。
     */
    @PostMapping(value = "/file/upload/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<FileInfoDTO> uploadFile(@PathVariable String projectId,
                                               @RequestParam("file") MultipartFile file)
            throws IOException {
        log.info("文件开始上传");
        FileEntity result = fileCommandService.uploadMinio(
                projectId, file.getBytes(), file.getOriginalFilename());
        log.info("文件上传完成");
        return ResponseDTO.ok(toFileInfoDTO(result));
    }

    /**
     * 厂商 {@code POST /v1/pub/fdfs/upload/{projectId}}（{@code PublicFdfsController.java:61-67}）。
     *
     * <p>照抄点：与 MinIO 分支共用响应形状，但**先校验 projectId**（见
     * {@code IFileCommandService.uploadFdfs}）。
     *
     * <p><b>类 C</b>：FastDFS 私有协议未实现，且本环境无对端——传入不存在的 projectId 时能拿到
     * 400 {@code fdfs.upload.software.not.exist}，传入存在的 projectId 时会在上传那一步报错。
     */
    @PostMapping(value = "/fdfs/upload/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<FileInfoDTO> uploadFdfsFile(@PathVariable String projectId,
                                                   @RequestParam("file") MultipartFile file)
            throws IOException {
        log.info("文件开始上传");
        FileEntity result = fileCommandService.uploadFdfs(
                projectId, file.getBytes(), file.getOriginalFilename());
        log.info("文件上传完成");
        return ResponseDTO.ok(toFileInfoDTO(result));
    }

    // ================================================================ 下载

    /**
     * 厂商 {@code GET /v1/pub/file/download/{fileId}}（{@code PublicMinioController.java:82-86}）。
     *
     * <p>{@code Range} 请求头形如 {@code 0-100}（**不带 {@code bytes=}**，由服务端补前缀）。
     * 响应头与字节流的写法照抄 {@code FileMinioService.download:158-175}：
     * 先写 Content-Type/Accept-Ranges/ETag/Last-Modified/Expires/Content-Range/
     * Access-Control-Expose-Headers/Content-Length/Content-Disposition，
     * 再按是否带 Range 走 206（{@code handleRangeRequest}）或 200（{@code handleFullDownload}）。
     *
     * <p><b>类 C</b>：依赖 MinIO 对端。
     */
    @GetMapping("/file/download/{fileId}")
    public void downloadFile(@PathVariable String fileId,
                             @RequestHeader(name = "Range", required = false) String range,
                             HttpServletResponse response) throws IOException {
        FileDownloadEntity download = fileQueryService.prepareDownload(fileId, range);
        log.info("当前请求range范围为:{}", range);
        writeDownload(download, response);
    }

    /**
     * 厂商 {@code FileMinioService.download:158-175} + {@code MinioClientUtil:81-127} 的响应写法，逐条照抄。
     *
     * <p>怪点（必须保留）：
     * <ol>
     *   <li>206 分支把 {@code fullLength}（本次要发的字节数）当"文件总长"传给 {@code parseRangeHeader}，
     *       于是 {@code Content-Range} 的 {@code /total} 是 fullLength、并且"上界超过总长"会误判成 416；</li>
     *   <li>{@code Range} 里省略上界（如 {@code 50-}）时，206 分支用 {@code fullLength-1} 当上界，
     *       不是对象真实总长；</li>
     *   <li>带 Range 时 {@code Content-Range} 被写两次（先 downloadRange 的值，再 206 分支覆盖）。</li>
     * </ol>
     */
    private void writeDownload(FileDownloadEntity download, HttpServletResponse response)
            throws IOException {
        response.setHeader("Content-Type", download.getContentType());
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", download.getMd5());
        response.setDateHeader("Last-Modified", download.getLastModified());
        response.setDateHeader("Expires", System.currentTimeMillis() + EXPIRES_AFTER_MILLIS);
        response.setHeader("Content-Range", "bytes " + download.getStart() + "-" + download.getEnd()
                + "/" + download.getTotal());
        response.addHeader("Access-Control-Expose-Headers", "Content-Range");
        response.setHeader("Content-Length", String.valueOf(download.getFullLength()));
        response.setHeader("Content-Disposition", ATTACHMENT_PREFIX
                + URLEncoder.encode(download.getAttachmentName(), StandardCharsets.UTF_8));

        String range = download.getRangeHeader();
        if (range != null) {
            // 厂商 MinioClientUtil.handleRangeRequest：注意第二个入参是 fullLength，不是 total
            long[] parsed = parseRangeHeader(range, download.getFullLength());
            long start = parsed[0];
            long end = parsed[1];
            if (start < 0L || end >= download.getFullLength() || start > end) {
                response.setHeader("Content-Range", "bytes */" + download.getFullLength());
                response.sendError(416);
                return;
            }
            response.setStatus(206);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/"
                    + download.getFullLength());
            response.setHeader("Content-Length", String.valueOf(end - start + 1L));
            try (InputStream in = fileStorePort.getObject(download.getObjectName(), start,
                    end - start + 1L)) {
                in.transferTo(response.getOutputStream());
            }
            response.flushBuffer();
            return;
        }
        // 厂商 handleFullDownload：200 + 整个对象
        response.setStatus(200);
        try (InputStream in = fileStorePort.getObject(download.getObjectName())) {
            in.transferTo(response.getOutputStream());
        }
        response.flushBuffer();
    }

    /**
     * 厂商 {@code MinioClientUtil.parseRangeHeader:181-187}：去掉 {@code bytes=} 前缀后按 {@code -} 切，
     * 只给一个数时下界取 {@code fileSize-1}。**照抄**：缺下界的写法（{@code bytes=-100}）
     * 会走到 {@code Long.parseLong("")} 抛 {@code NumberFormatException} ⇒ 500。
     */
    private static long[] parseRangeHeader(String rangeHeader, long fileSize) {
        String range = rangeHeader.substring(6);
        String[] parts = range.split("-");
        long start = Long.parseLong(parts[0]);
        long end = parts.length > 1 ? Long.parseLong(parts[1]) : fileSize - 1L;
        return new long[]{start, end};
    }

    // ================================================================ 删除

    /**
     * 厂商 {@code DELETE /v1/pub/file/{fileId}}（{@code PublicMinioController.java:88-93}）。
     *
     * <p>照抄点：对象删不掉（例如对象已不存在）时**返回 data:false 且不删库行**，不报错。
     */
    @DeleteMapping("/file/{fileId}")
    public ResponseDTO<Boolean> delFile(@PathVariable String fileId) {
        return ResponseDTO.ok(fileCommandService.deleteFile(fileId));
    }

    /**
     * 厂商 {@code DELETE /v1/pub/file/delete}（{@code PublicMinioController.java:95-101}）。
     *
     * <p>body 是裸数组，如 {@code ["id1","id2"]}；任何一个 id 查不到 ⇒ 400 且前面已删的不回滚。
     */
    @DeleteMapping("/file/delete")
    public ResponseDTO<Boolean> delFileList(@RequestBody List<String> fileIdList) {
        return ResponseDTO.ok(fileCommandService.deleteFiles(fileIdList));
    }

    // ================================================================ 存储状态

    /**
     * 厂商 {@code GET /v1/pub/file/storage}（{@code PublicMinioController.java:124-128}）。
     *
     * <p><b>类 C</b>：厂商走 MinIO Admin API（不是 S3），本环境取不到 ⇒ 返回空数组（厂商同形）。
     */
    @GetMapping("/file/storage")
    public ResponseDTO<List<MinioStorageInfoDTO>> getStorageInfo() {
        List<MinioStorageInfoDTO> data = fileQueryService.queryMinioStorage().stream()
                .map(FileController::toMinioStorageDTO)
                .toList();
        return ResponseDTO.ok(data);
    }

    /**
     * 厂商 {@code GET /v1/pub/fdfs/status}
     * （{@code recovered/gaop-be/.../controller/pub/PublicFdfsController.java:53-58}）。
     *
     * <p>照抄点：拿不到存储信息时返回**配置里每个 tracker 一条**的占位记录（status=99、大小 0、
     * group={@code group1}），HTTP 仍是 200。
     */
    @GetMapping("/fdfs/status")
    public ResponseDTO<List<FdfsStorageInfoDTO>> getFdfsStatusInfo() {
        List<FdfsStorageInfoDTO> data = fileQueryService.queryFdfsStatus().stream()
                .map(FileController::toFdfsStorageDTO)
                .toList();
        return ResponseDTO.ok(data);
    }

    // ================================================================ 映射

    private static FileItemDTO toFileItemDTO(FileItemEntity e) {
        return FileItemDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .currentSize(e.getCurrentSize())
                .size(e.getSize())
                .md5(e.getMd5())
                .fileProjectId(e.getFileProjectId())
                .projectName(e.getProjectName())
                .path(e.getPath())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private static FileInfoDTO toFileInfoDTO(FileEntity e) {
        return FileInfoDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .fileProjectId(e.getFileProjectId())
                .path(e.getPath())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .deleted(e.getDeleted())
                .currentSize(e.getCurrentSize())
                .size(e.getSize())
                .md5(e.getMd5())
                .build();
    }

    private static MinioStorageInfoDTO toMinioStorageDTO(MinioStorageVO vo) {
        return MinioStorageInfoDTO.builder()
                .ip(vo.getIp())
                .totalSize(vo.getTotalSize())
                .freeSize(vo.getFreeSize())
                .percentMemoryLeft(vo.getPercentMemoryLeft())
                .status(vo.getStatus())
                .group(vo.getGroup())
                .build();
    }

    private static FdfsStorageInfoDTO toFdfsStorageDTO(FdfsStorageNodeVO vo) {
        return FdfsStorageInfoDTO.builder()
                .ip(vo.getIp())
                .totalSize(vo.getTotalSize())
                .freeSize(vo.getFreeSize())
                .percentMemoryLeft(vo.getPercentMemoryLeft())
                .status(vo.getStatus())
                .group(vo.getGroup())
                .build();
    }
}
