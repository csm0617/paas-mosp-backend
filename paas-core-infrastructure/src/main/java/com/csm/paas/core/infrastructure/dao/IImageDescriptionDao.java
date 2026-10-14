package com.csm.paas.core.infrastructure.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 镜像描述用的 `g_setting` 语句（键 `image.desc`；厂商 `K3sImageServiceImpl.updateImageDesc` /
 * `getRepositories`）。
 *
 * <p>单独一个 Dao 是为了**不碰** paas-core 既有的 `ISettingDao`（那些方法对应厂商
 * `SettingService.updateOrInsert` 的语义，与这里的 `insertSelective`/`updateByPrimaryKeySelective` 不同）。
 */
@Mapper
public interface IImageDescriptionDao {

    @Select("SELECT val FROM g_setting WHERE `key` = 'image.desc' LIMIT 1")
    String selectImageDesc();

    /** 厂商首次写入：`insertSelective` 只带 key/val/created_at（`desc`、`updated_at` 留 NULL）。 */
    @Insert("INSERT INTO g_setting(`key`, val, created_at) VALUES ('image.desc', #{val}, #{createdAt})")
    int insertImageDesc(@Param("val") String val, @Param("createdAt") long createdAt);

    /** 厂商更新：`updateByPrimaryKeySelective` 只带 val 与 **created_at**（怪点，不是 updated_at）。 */
    @Update("UPDATE g_setting SET val = #{val}, created_at = #{createdAt} WHERE `key` = 'image.desc'")
    int updateImageDesc(@Param("val") String val, @Param("createdAt") long createdAt);

    /** 只为断言"行是否存在"（厂商用 `select` 的结果是否为空判断）。 */
    @Select("SELECT COUNT(*) FROM g_setting WHERE `key` = 'image.desc'")
    int countImageDesc();
}
