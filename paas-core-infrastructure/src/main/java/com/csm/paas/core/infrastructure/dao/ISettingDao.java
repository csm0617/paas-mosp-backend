package com.csm.paas.core.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ISettingDao {

    /** 厂商表结构是 key/val（历史库里有 `value` 的说法，实测列名是 val）。 */
    @Select("SELECT val FROM g_setting WHERE `key` = #{key}")
    String selectValByKey(@Param("key") String key);
}
