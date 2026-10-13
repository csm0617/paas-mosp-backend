package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.ModePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IModeDao {

    @Select("SELECT id, type, mode, mode_name, used, create_time FROM g_mode WHERE used = 1 LIMIT 1")
    ModePO selectUsed();

    @Select("SELECT id, type, mode, mode_name, used, create_time FROM g_mode ORDER BY id")
    List<ModePO> selectAll();
}
