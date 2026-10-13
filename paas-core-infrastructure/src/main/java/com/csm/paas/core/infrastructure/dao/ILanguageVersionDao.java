package com.csm.paas.core.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ILanguageVersionDao {

    /** 当前语言包版本：`used=1`（厂商 LanguageServiceImpl:383 同款）。 */
    @Select("SELECT lang, lang_version FROM g_language_version WHERE used = 1 LIMIT 1")
    java.util.Map<String, Object> selectUsed();
}
