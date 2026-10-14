package com.csm.paas.core.domain.image.adapter.port;

import com.csm.paas.core.domain.image.model.valobj.ProjectItemVO;

import java.util.List;

/**
 * Harbor 的只读入口（实现在 infrastructure/adapter/port，只调 gateway/）。
 * <p>厂商出处：`third/Harbor.allPageProjects(keyword)`——分页请求 `GET /api/v2.0/projects`
 * （`page_size=100`，keyword 非空时带 `name=`），直到某一页不足一页为止。
 */
public interface IHarborPort {

    /** 全量项目（厂商 `allPageProjects`：内部翻页取全，不分页返回）。 */
    List<ProjectItemVO> listAllProjects(String keyword);
}
