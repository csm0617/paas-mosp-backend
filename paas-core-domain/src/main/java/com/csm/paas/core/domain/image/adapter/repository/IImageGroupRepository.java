package com.csm.paas.core.domain.image.adapter.repository;

import com.csm.paas.core.domain.image.model.valobj.ImageGroupVO;

import java.util.List;

/**
 * 镜像分组仓储（表 `g_group`，厂商 `K3sImageServiceImpl` 用的 `imageGroupMapper`）。
 * <p>注意：**没有 Harbor**——参考环境里生效的是 k3s 实现（见 `docs/findings/79`）。
 */
public interface IImageGroupRepository {

    /** 按 name 模糊查询，`create_date` 倒序，分页。 */
    List<ImageGroupVO> queryPage(String likeName, int offset, int size);

    /** 按 name **精确**查（厂商用 `isLikeWhenPresent(name)` 且不带通配符 ⇒ 等价于等值）。 */
    List<ImageGroupVO> queryByName(String name);

    /** 插入一行（厂商 `createProjectNoException` 在查不到时插入，`createDate = System.currentTimeMillis()`）。 */
    ImageGroupVO insert(String name, long createDate);

    ImageGroupVO queryById(int id);

    /** 全表、无排序（厂商 `imageGroupMapper.select(SelectDSLCompleter.allRows())`，`getAllProjectImageList` 用）。 */
    List<ImageGroupVO> queryAll();

    /** 按主键删除（厂商 `deleteByPrimaryKey`；行不存在时静默）。 */
    void deleteById(int id);
}
