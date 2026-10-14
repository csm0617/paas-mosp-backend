package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.SettingPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ISettingDao {

    /** 厂商表结构是 key/val（历史库里有 `value` 的说法，实测列名是 val）。 */
    @Select("SELECT val FROM g_setting WHERE `key` = #{key}")
    String selectValByKey(@Param("key") String key);

    /** 主键是 `key`（varchar）→ InnoDB 自然按主键序返回，与厂商一致。 */
    @Select("SELECT `key`, val, `desc`, updated_at, created_at FROM g_setting")
    List<SettingPO> selectAll();

    @Select("SELECT COUNT(*) FROM g_setting WHERE `key` = #{key}")
    int countByKey(@Param("key") String key);

    @org.apache.ibatis.annotations.Update("UPDATE g_setting SET val = #{val}, `desc` = #{desc}, updated_at = #{updatedAt} WHERE `key` = #{key}")
    int updateSetting(@Param("key") String key, @Param("val") String val,
                      @Param("desc") String desc, @Param("updatedAt") long updatedAt);

    @org.apache.ibatis.annotations.Delete("DELETE FROM g_setting WHERE `key` = #{key}")
    int deleteSetting(@Param("key") String key);

    @org.apache.ibatis.annotations.Insert("INSERT INTO g_setting(`key`, val, `desc`, updated_at, created_at) "
            + "VALUES(#{key}, #{val}, #{desc}, #{now}, #{now})")
    int insertSetting(@Param("key") String key, @Param("val") String val,
                      @Param("desc") String desc, @Param("now") long now);

    /**
     * 厂商 `SettingService.updateOrInsert` 的插入分支：`desc=""`、**`updated_at` 留 NULL**、
     * `created_at=now`（与 {@link #insertSetting} 不同，后者两个时间戳都写 now）。
     */
    @org.apache.ibatis.annotations.Insert("INSERT INTO g_setting(`key`, val, `desc`, updated_at, created_at) "
            + "VALUES(#{key}, #{val}, '', NULL, #{now})")
    int insertSettingWithNullUpdatedAt(@Param("key") String key, @Param("val") String val,
                                       @Param("now") long now);

    /** 厂商 `updateOrInsert` 的更新分支：`updateByPrimaryKeySelective` 只带 val 与 updated_at。 */
    @org.apache.ibatis.annotations.Update("UPDATE g_setting SET val = #{val}, updated_at = #{updatedAt} "
            + "WHERE `key` = #{key}")
    int updateSettingValue(@Param("key") String key, @Param("val") String val,
                           @Param("updatedAt") long updatedAt);
}
