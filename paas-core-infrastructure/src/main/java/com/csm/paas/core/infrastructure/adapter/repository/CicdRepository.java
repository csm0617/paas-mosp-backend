package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.cicd.adapter.repository.ICicdRepository;
import com.csm.paas.core.domain.cicd.model.entity.CicdEntity;
import com.csm.paas.core.infrastructure.dao.ICicdDao;
import com.csm.paas.core.infrastructure.dao.po.CicdPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * `g_ci_cd` 仓储实现：只调 {@link ICicdDao}，并在**写库前**复刻厂商 setter 的 `trim()` 语义。
 *
 * <p>厂商出处：`recovered/gaop-be/.../model/Cicd.java:34-106`——每个字符串 setter 都写成
 * `x == null ? null : x.trim()`，所以 `"  abc  "` 入库是 `"abc"`。
 * 本仓 entity 用 Lombok 的 Data 注解（setter 不 trim），所以把 trim 集中放在这里。
 */
@Repository
@RequiredArgsConstructor
public class CicdRepository implements ICicdRepository {

    private final ICicdDao cicdDao;

    @Override
    public Optional<CicdEntity> queryById(Integer id) {
        return Optional.ofNullable(cicdDao.selectById(id)).map(CicdRepository::toEntity);
    }

    @Override
    public List<CicdEntity> queryByImageNameAndUserId(String imageName, String userId) {
        return cicdDao.selectByImageNameAndUserId(trim(imageName), trim(userId)).stream()
                .map(CicdRepository::toEntity).toList();
    }

    @Override
    public List<CicdEntity> queryPage(String userId, String likeKeyword, int limit, int offset) {
        return cicdDao.selectPage(trim(userId), likeKeyword, limit, offset).stream()
                .map(CicdRepository::toEntity).toList();
    }

    @Override
    public long countByUserAndKeyword(String userId, String likeKeyword) {
        return cicdDao.countByUserAndKeyword(trim(userId), likeKeyword);
    }

    @Override
    public int insertSelective(CicdEntity entity) {
        return cicdDao.insertSelective(toPo(entity));
    }

    @Override
    public int updateByPrimaryKey(CicdEntity entity) {
        return cicdDao.updateByPrimaryKey(toPo(entity));
    }

    @Override
    public int updateByPrimaryKeySelective(CicdEntity entity) {
        return cicdDao.updateByPrimaryKeySelective(toPo(entity));
    }

    @Override
    public int deleteById(Integer id) {
        return cicdDao.deleteById(id);
    }

    // ------------------------------------------------------------------ 映射

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static CicdPO toPo(CicdEntity e) {
        return CicdPO.builder()
                .id(e.getId())
                .userId(trim(e.getUserId()))
                .imageName(trim(e.getImageName()))
                .imageTag(trim(e.getImageTag()))
                .dockerfileId(trim(e.getDockerfileId()))
                .dockerfileContent(trim(e.getDockerfileContent()))
                .buildfileId(trim(e.getBuildfileId()))
                .jobName(trim(e.getJobName()))
                .jobStatus(e.getJobStatus())
                .description(trim(e.getDescription()))
                .createAt(e.getCreateAt())
                .updateAt(e.getUpdateAt())
                .imageStorage(trim(e.getImageStorage()))
                .build();
    }

    private static CicdEntity toEntity(CicdPO po) {
        return CicdEntity.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .imageName(po.getImageName())
                .imageTag(po.getImageTag())
                .dockerfileId(po.getDockerfileId())
                .dockerfileContent(po.getDockerfileContent())
                .buildfileId(po.getBuildfileId())
                .jobName(po.getJobName())
                .jobStatus(po.getJobStatus())
                .description(po.getDescription())
                .createAt(po.getCreateAt())
                .updateAt(po.getUpdateAt())
                .imageStorage(po.getImageStorage())
                .build();
    }
}
