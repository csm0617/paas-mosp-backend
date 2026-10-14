package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.image.adapter.repository.IImageGroupRepository;
import com.csm.paas.core.domain.image.model.valobj.ImageGroupVO;
import com.csm.paas.core.infrastructure.dao.IImageGroupDao;
import com.csm.paas.core.infrastructure.dao.po.ImageGroupPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ImageGroupRepository implements IImageGroupRepository {

    private final IImageGroupDao imageGroupDao;

    @Override
    public List<ImageGroupVO> queryPage(String likeName, int offset, int size) {
        return imageGroupDao.selectPage(likeName, offset, size).stream().map(this::toVO).toList();
    }

    @Override
    public List<ImageGroupVO> queryByName(String name) {
        ImageGroupPO po = imageGroupDao.selectByName(name);
        return po == null ? List.of() : List.of(toVO(po));
    }

    @Override
    public ImageGroupVO insert(String name, long createDate) {
        ImageGroupPO po = new ImageGroupPO();
        po.setName(name);
        po.setCreateDate(createDate);
        imageGroupDao.insert(po);
        ImageGroupPO saved = imageGroupDao.selectById(po.getId());   // 厂商：插入后再按主键查一次
        return saved == null ? null : toVO(saved);
    }

    @Override
    public ImageGroupVO queryById(int id) {
        ImageGroupPO po = imageGroupDao.selectById(id);
        return po == null ? null : toVO(po);
    }

    @Override
    public List<ImageGroupVO> queryAll() {
        return imageGroupDao.selectAll().stream().map(this::toVO).toList();
    }

    @Override
    public void deleteById(int id) {
        imageGroupDao.deleteById(id);
    }

    private ImageGroupVO toVO(ImageGroupPO po) {
        return ImageGroupVO.builder().id(po.getId()).createDate(po.getCreateDate()).name(po.getName()).build();
    }
}
