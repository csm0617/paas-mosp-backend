package com.csm.paas.core.domain.database.service.impl;

import com.csm.paas.core.domain.database.adapter.repository.IDatabaseRepository;
import com.csm.paas.core.domain.database.model.entity.DatabaseRecordListEntity;
import com.csm.paas.core.domain.database.service.IDatabaseQueryService;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 数据库域查询实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseQueryServiceImpl implements IDatabaseQueryService {

    private final IDatabaseRepository databaseRepository;

    @Override
    public DatabaseRecordListEntity queryRecords(int page, int size) {
        var list = databaseRepository.queryRecordPage(PageUtils.offset(page, size), size);
        long total = databaseRepository.countRecords();
        return DatabaseRecordListEntity.builder().list(list).total(total).build();
    }
}
