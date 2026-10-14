package com.csm.paas.core.infrastructure.dao;

import com.csm.paas.core.infrastructure.dao.po.DatabaseRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 数据库记录表（`g_database_record`）。 */
@Mapper
public interface IDatabaseDao {

    /** 厂商 `DatabaseRecordService.getAllDatabaseRecord`：`updated_at DESC` 分页。 */
    @Select("""
            SELECT id, real_store_filename, exec_file_name, database_name, updated_at,
                   exec_fault_message, status
            FROM g_database_record ORDER BY updated_at DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<DatabaseRecordPO> selectRecordPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM g_database_record")
    long selectRecordCount();
}
