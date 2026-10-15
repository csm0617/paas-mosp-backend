package com.csm.paas.core.domain.file.service;

import com.csm.paas.core.domain.file.model.entity.FileProjectEntity;
import com.csm.paas.core.domain.file.model.entity.FileProjectListEntity;

/**
 * 业务软件（文件项目）服务。
 *
 * <p>厂商出处：{@code recovered/gaop-be/src/main/java/com/glriverside/gaop/be/service/FileProjectService.java}
 * 与 {@code recovered/gaop-be/.../controller/FileProjectController.java}，
 * 表 {@code g_file_project}。
 *
 * <p>照抄点：
 * <ul>
 *   <li>列表关键字**不 trim、不判空串**（对比文件列表会 trim + isBlank），只判 {@code == null}；</li>
 *   <li>新增/修改都要查重（{@code name = ? and deleted = false} 存在 ⇒ 400 {@code software.name.existed}
 *       并把 name 当参数带出去）；</li>
 *   <li>删除是**物理删除**，先按 {@code id + deleted=false} 找，找不到 ⇒ 400
 *       {@code 删除的业务软件名称不存在}。</li>
 * </ul>
 */
public interface IFileProjectService {

    /** 一页业务软件（{@code created_at DESC}）。 */
    FileProjectListEntity queryFileProjectList(int page, int size, String keyword);

    /**
     * 新增。重名 ⇒ 400 {@code software.name.existed}；插入后按 name 反查，
     * 反查不到 ⇒ 500 {@code global.add.failed}。
     */
    FileProjectEntity addFileProject(String name);

    /**
     * 修改。先按主键取（**不过滤 deleted**），取不到 ⇒ 400
     * {@code 该id指定的业务软件不存在}；重名（排除自身）⇒ 400 {@code software.name.existed}；
     * 更新后按新 name 反查，反查不到 ⇒ 500 {@code auth.updatea.auth.info.failed}。
     */
    FileProjectEntity updateFileProject(String projectId, String name);

    /** 删除；找不到（或已逻辑删除）⇒ 400 {@code 删除的业务软件名称不存在}。 */
    void removeFileProject(String projectId);
}
