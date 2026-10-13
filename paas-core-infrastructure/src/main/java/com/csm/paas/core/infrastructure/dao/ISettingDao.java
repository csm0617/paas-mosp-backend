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
}
