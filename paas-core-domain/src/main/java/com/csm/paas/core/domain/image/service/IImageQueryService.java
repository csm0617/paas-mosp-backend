package com.csm.paas.core.domain.image.service;

import com.csm.paas.core.domain.image.model.entity.ProjectListEntity;

/**
 * 镜像管理查询（厂商 `/v1/image/**`）。
 *
 * <p><b>数据源是数据库（`g_group`），不是 Harbor</b>——厂商 `NodeVersionServiceImpl.getImageServiceInstance()`
 * **无条件返回 k3s 实现**，实际生效的是 `K3sImageServiceImpl`（`docs/findings/79`）。
 */
public interface IImageQueryService {

    /**
     * 项目列表（厂商 `K3sImageServiceImpl.projectList:200-250`，逐行照抄）。
     *
     * <ol>
     *   <li>按 `name like ?` 查 `g_group`、`create_date` 倒序、分页（`PageHelper`）；</li>
     *   <li>**若查询结果为空** → 依次取/建三个内置项目 `system_containers`/`gaop`/`library`
     *       （查不到就**插入**，`create_date = now` 毫秒），并把它们放进列表；</li>
     *   <li>把数据库查到的行**追加**到内置项目之后；</li>
     *   <li>过滤掉 **只有** `system_containers`（注意：没有 `system_store`）；</li>
     *   <li>映射成 `ProjectItem`：**只设** `projectId=id`、`name`、`creationTime`，
     *       其余 8 个字段保持 null；`creationTime = ISO8601(Instant.ofEpochSecond(createDate), +08:00)`；</li>
     *   <li>`total = 过滤后的条数`。</li>
     * </ol>
     *
     * <p><b>副作用</b>：表为空时这个端点会**写库**（创建 3 个内置项目）——这是厂商行为，照抄。
     */
    ProjectListEntity queryProjectList(int page, int size, String keyword);
}
