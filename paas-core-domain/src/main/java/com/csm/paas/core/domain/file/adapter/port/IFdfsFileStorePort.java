package com.csm.paas.core.domain.file.adapter.port;

import com.csm.paas.core.domain.file.model.valobj.FdfsStorageNodeVO;

import java.util.List;

/**
 * FastDFS 端口（厂商的 {@code FastDFSClientUtil}）。
 *
 * <p>厂商出处：{@code recovered/gaop-fdfs/src/main/java/com/glriverside/gaopfdfs/utils/FastDFSClientUtil.java}
 * 与 {@code recovered/gaop-be/.../utils/FastDFSClientUtil.java}。
 *
 * <p><b>类 C：本环境无 FastDFS 对端，代码齐全但不做在线验证；且下面这一段的私有协议没有实现。</b>
 *
 * <p>缺的是哪一段（见实现层 {@code FdfsFileStorePort}）：
 * <ul>
 *   <li>上传：厂商走 {@code org.csource.fastdfs.StorageClient.upload_file}（tracker 22122 选组 →
 *       storage 23000 发 {@code upload_file} 二进制包）与 {@code com.github.tobato.fastdfs}
 *       的 {@code FastFileStorageClient.uploadFile}；两者都是**私有二进制协议**，
 *       FastDFS 官方**没有 HTTP 上传接口**（只有 nginx/fastdfs-nginx-module 的只读下载模块），
 *       所以这里无法"照抄 HTTP"。</li>
 *   <li>状态：厂商 {@code DefaultTrackerClient.listGroups/listStorages} 同样是私有协议；
 *       拿不到时厂商返回 null/空，本端口据此返回 null，由领域层照抄"回落成 tracker 占位列表"的分支。</li>
 * </ul>
 *
 * <p>因此 {@link #upload} 直接抛错（等价于厂商"上传文件到服务器上时出现异常"那条路径），
 * 而 {@link #listStorageInfo} 返回 null 让领域层走占位分支——这样
 * {@code GET /v1/pub/fdfs/status} 在无对端时仍与厂商同形（每条 tracker 一个 status=99 的占位）。
 */
public interface IFdfsFileStorePort {

    /**
     * 上传字节，返回 {@code group/path} 形式的完整路径。
     *
     * <p>厂商 {@code FastDFSClientUtil.newUploadFile:119-146}：建临时文件 →
     * {@code upload_file} → {@code groupName + "/" + filePath}；失败抛
     * {@code WrongArgumentException("在上传文件到服务器上时出现异常")}。
     * 本仓未实现该协议，实现层直接抛同文案的错误。
     */
    String upload(byte[] content, String originalFilename);

    /**
     * 存储集群状态；拿不到返回 **null**（照抄 {@code FastDFSClientUtil.StorageInfo:311-340}
     * 在 {@code listGroups}/{@code listStorages} 失败时 {@code return null}）。
     */
    List<FdfsStorageNodeVO> listStorageInfo();
}
