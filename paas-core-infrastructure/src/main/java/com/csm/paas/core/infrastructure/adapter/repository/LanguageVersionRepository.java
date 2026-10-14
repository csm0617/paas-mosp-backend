package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.language.adapter.repository.ILanguageVersionRepository;
import com.csm.paas.core.domain.language.model.entity.CurrentLanguageEntity;
import com.csm.paas.core.infrastructure.dao.ILanguageVersionDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LanguageVersionRepository implements ILanguageVersionRepository {

    private final ILanguageVersionDao languageVersionDao;

    @Override
    public Optional<CurrentLanguageEntity> queryCurrent() {
        Map<String, Object> row = languageVersionDao.selectUsed();
        if (row == null || row.get("lang") == null) {
            return Optional.empty();
        }
        return Optional.of(CurrentLanguageEntity.builder()
                .language(String.valueOf(row.get("lang")))
                .version(String.valueOf(row.get("lang_version")))
                .build());
    }

    @Override
    public java.util.List<com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity>
    queryLanguageVersionPage(String likeKeyword, int offset, int size) {
        return languageVersionDao.selectLanguageVersionPage(likeKeyword, offset, size).stream()
                .map(po -> com.csm.paas.core.domain.language.model.entity.LanguageVersionEntity.builder()
                        .id(po.getId()).lang(po.getLang()).langVersion(po.getLangVersion())
                        .used(po.getUsed()).createTime(po.getCreateTime())
                        .build())
                .toList();
    }

    @Override
    public long countLanguageVersions(String likeKeyword) {
        return languageVersionDao.countLanguageVersions(likeKeyword);
    }
}
