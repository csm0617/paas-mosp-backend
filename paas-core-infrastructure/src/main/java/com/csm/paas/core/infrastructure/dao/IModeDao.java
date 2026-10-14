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

    /** 厂商 `modeMapper.selectByPrimaryKey(id)`（`ModeServiceImpl.startModeType:117`）。 */
    @Select("SELECT id, type, mode, mode_name, used, create_time FROM g_mode WHERE id = #{id}")
    ModePO selectById(@org.apache.ibatis.annotations.Param("id") Long id);

    /** 厂商 `changeModeTypeDB:147`：全局把 `used=1` 清 0。 */
    @org.apache.ibatis.annotations.Update("UPDATE g_mode SET used = 0 WHERE used = 1")
    int resetUsed();

    /** 厂商 `changeModeTypeDB:148`：把该 id 置 `used=1`。 */
    @org.apache.ibatis.annotations.Update("UPDATE g_mode SET used = 1 WHERE id = #{id}")
    int setUsedById(@org.apache.ibatis.annotations.Param("id") Long id);
}
