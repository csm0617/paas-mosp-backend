package com.csm.paas.core.domain.mode.service.impl;

import com.csm.paas.core.domain.mode.adapter.repository.IModeRepository;
import com.csm.paas.core.domain.mode.model.entity.ModeEntity;
import com.csm.paas.core.domain.mode.service.IModeQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeQueryServiceImpl implements IModeQueryService {

    private final IModeRepository modeRepository;

    @Override
    public Optional<ModeEntity> queryCurrent() {
        return modeRepository.queryCurrent();
    }

    @Override
    public List<ModeEntity> queryAll() {
        return modeRepository.queryAll();
    }
}
