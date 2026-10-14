package com.csm.paas.core.domain.whitelist.service.impl;

import com.csm.paas.core.domain.software.adapter.port.IGatewayRefreshPort;
import com.csm.paas.core.domain.whitelist.adapter.repository.IWhiteListRepository;
import com.csm.paas.core.domain.whitelist.model.entity.WhiteListEntity;
import com.csm.paas.core.domain.whitelist.model.entity.WhiteListInfoEntity;
import com.csm.paas.core.domain.whitelist.service.IWhiteListService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import com.csm.paas.core.types.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 白名单实现（规则见 {@link IWhiteListService} 的文档）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhiteListServiceImpl implements IWhiteListService {

    private final IWhiteListRepository whiteListRepository;
    private final IGatewayRefreshPort gatewayRefreshPort;

    @Override
    public WhiteListInfoEntity list(int page, int size, String keyword) {
        String likeKeyword = keyword == null ? null : "%" + keyword + "%";
        List<WhiteListEntity> list = whiteListRepository.queryPage(likeKeyword, PageUtils.offset(page, size), size);
        long total = whiteListRepository.count(likeKeyword);
        return WhiteListInfoEntity.builder().list(list).total(total).build();
    }

    @Override
    public WhiteListEntity addPath(String path) {
        if (whiteListRepository.queryByPath(path).isPresent()) {
            throw new WrongArgumentException("software.path.already.exists");
        }
        whiteListRepository.insert(path == null ? null : path.trim(), System.currentTimeMillis());
        gatewayRefreshPort.refresh();
        // 厂商：插入后再按**原样 path** 回查（不是 trim 后的）⇒ 带首尾空格时返回 null
        return whiteListRepository.queryByPath(path).orElse(null);
    }

    @Override
    public void deletePaths(List<Long> pathIds) {
        if (pathIds == null) {
            return;
        }
        for (Long id : pathIds) {
            if (whiteListRepository.queryById(id).isEmpty()) {
                throw new WrongArgumentException("white.list.delete.path.not.exist", id);
            }
            whiteListRepository.deleteById(id);
        }
        gatewayRefreshPort.refresh();
    }
}
