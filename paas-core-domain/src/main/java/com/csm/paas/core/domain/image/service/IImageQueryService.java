package com.csm.paas.core.domain.image.service;

import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;

import java.util.List;

/** 镜像管理查询（厂商 `/v1/image/**`，数据来自 Harbor）。 */
public interface IImageQueryService {

    /**
     * Harbor 项目列表（厂商 `K8sImageServiceImpl.projectList:107-119`）。
     *
     * <p>三步照抄：
     * <ol>
     *   <li>`harbor.allPageProjects(keyword)` 取全量；</li>
     *   <li>过滤掉 `system_containers` 与 `system_store` 两个内置项目；</li>
     *   <li>按 `creation_time` **字符串倒序**，再 `skip((page-1)*size).limit(size)`。</li>
     * </ol>
     * 返回 `{list, total}`，其中 `total` 是**过滤后、分页前**的总数。
     */
    List<ProjectItemVO> queryProjectList(int page, int size, String keyword);

    /** 过滤后的总数（与 `queryProjectList` 同一套过滤）。 */
    long countProjectList(String keyword);
}
