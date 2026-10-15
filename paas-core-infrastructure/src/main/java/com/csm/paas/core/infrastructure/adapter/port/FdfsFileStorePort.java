package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.file.adapter.port.IFdfsFileStorePort;
import com.csm.paas.core.domain.file.model.valobj.FdfsStorageNodeVO;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link IFdfsFileStorePort} 的实现。
 *
 * <p><b>类 C：本环境无 FastDFS 对端；并且这里的私有协议**没有实现**。</b>
 *
 * <h3>缺的是哪一段（写清楚，便于后续补齐）</h3>
 * <p>厂商 {@code FastDFSClientUtil} 同时用了两套客户端，都是 FastDFS 的**私有二进制协议**
 * （tracker 默认 22122、storage 默认 23000）：
 * <ol>
 *   <li>{@code org.csource.fastdfs.StorageClient.upload_file(localFilename, ext, meta)}
 *       ——{@code recovered/gaop-fdfs/.../utils/FastDFSClientUtil.java:119-146} 的
 *       {@code newUploadFile}；报文是定长头（16 字节）+ 文件体的 {@code STORAGE_PROTO_CMD_UPLOAD_FILE}；</li>
 *   <li>{@code com.github.tobato.fastdfs.service.FastFileStorageClient.uploadFile(...)}
 *       ——同文件 {@code :148-163} 的 {@code uploadFile}。</li>
 * </ol>
 * <p>要补齐，需要实现三段：① 连 tracker 发 {@code TRACKER_PROTO_CMD_SERVICE_QUERY_STORE_WITHOUT_GROUP_ONE}
 * 拿一个 storage 地址（含 group 名）；② 连 storage 发 {@code UPLOAD_FILE} 上传并收
 * {@code group + "/" + path}；③ 状态查询再发 {@code SERVICE_QUERY_GROUP_LIST}/{@code SERVICE_QUERY_STORAGE_LIST}。
 * 离线仓库里既没有 {@code fastdfs-client-java}（csource）也没有 {@code fastdfs-client}（tobato），
 * 且不允许改 pom，所以这里到此为止。
 *
 * <p><b>不采用 HTTP 代替的原因</b>：FastDFS 官方没有上传 HTTP 接口
 * （nginx 的 {@code fastdfs-nginx-module} 只提供下载/只读访问），所以"照抄 HTTP 拼法"这条路不成立。
 *
 * <h3>本实现的行为</h3>
 * <ul>
 *   <li>{@link #upload}：直接抛厂商同文案的 400（等价于厂商"上传文件到服务器上时出现异常"那条失败路径）；</li>
 *   <li>{@link #listStorageInfo}：返回 null。厂商 {@code FastDFSClientUtil.StorageInfo:311-340}
 *       在 {@code listGroups}/{@code listStorages} 失败时也是 {@code return null}，
 *       于是领域层会照抄厂商的"回落成每个 tracker 一条占位记录"分支
 *       ——{@code GET /v1/pub/fdfs/status} 因此在无对端时仍与厂商同形（HTTP 200、status=99、大小 0）。</li>
 * </ul>
 */
@Slf4j
@Component
public class FdfsFileStorePort implements IFdfsFileStorePort {

    /**
     * 厂商 {@code FastDFSClientUtil.newUploadFile:130-141} 失败时抛的 {@code WrongArgumentException}
     * 文案（{@code "在上传文件到服务器上时出现异常"}）。本仓 {@code MessageResolver} 解析不到该键会原样返回，
     * 与厂商一致（同 {@code ImageCatalogQueryServiceImpl:261} 的处理）。
     */
    private static final String UPLOAD_FAILED_MESSAGE = "在上传文件到服务器上时出现异常";

    @Override
    public String upload(byte[] content, String originalFilename) {
        // 未实现 FastDFS 私有协议（见类 javadoc）；这里等价于厂商的"上传到服务器异常"分支
        log.warn("FastDFS 私有协议未实现，无法上传文件：{}（{} 字节）",
                originalFilename, content == null ? 0 : content.length);
        throw new WrongArgumentException(UPLOAD_FAILED_MESSAGE);
    }

    @Override
    public List<FdfsStorageNodeVO> listStorageInfo() {
        // 照抄 FastDFSClientUtil.StorageInfo 拿不到组/节点信息时的返回：null
        return null;
    }
}
