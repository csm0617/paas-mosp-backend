package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.image.adapter.repository.IImageLogRepository;
import com.csm.paas.core.domain.image.model.valobj.ImageLogVO;
import com.csm.paas.core.infrastructure.dao.IImageLogDao;
import com.csm.paas.core.infrastructure.dao.po.ImageLogPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 镜像上传日志仓储（表 `g_image_log`；厂商 `dao/ImageLogMapper` + `service/DockerService`）。
 *
 * <p>一处**已知无害偏离**：厂商 `model/ImageLog` 的 setter 会 `trim()` 字符串，
 * 而 MyBatis 正是通过 setter 装载行 ⇒ 厂商读出来的 `log`/`name` 等已被去掉首尾空白
 * （`updateByPrimaryKeySelective` 再把 trim 后的值写回库）。paas-core 的 PO 是 Lombok 普通 setter，
 * 不做 trim。这里选择不复制这个副作用（复刻它会把日志的尾随换行反复吃掉）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageLogRepository implements IImageLogRepository {

    /** 厂商 `DockerService.DATE_TIME_FORMATTER`：`yyyy-MM-dd'T'HH:mm:ss.SSSXXX` + **系统时区**。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

    private final IImageLogDao imageLogDao;

    @Override
    public List<ImageLogVO> queryPage(String likeKeyword, int offset, int size) {
        return imageLogDao.selectPage(likeKeyword, offset, size).stream().map(this::toVO).toList();
    }

    @Override
    public long count(String likeKeyword) {
        return imageLogDao.count(likeKeyword);
    }

    @Override
    public ImageLogVO insert(ImageLogVO imageLog) {
        ImageLogPO po = toPO(imageLog);
        imageLogDao.insert(po);
        imageLog.setId(po.getId());
        return imageLog;
    }

    @Override
    public void appendLog(long id, String text) {
        // 厂商 `ImageLogMapper.appendLog`（default 方法）：查出来 + "\n" + 时间戳 + "  " + text 再整列写回
        ImageLogPO existing = imageLogDao.selectById(id);
        if (existing == null) {
            return;
        }
        ImageLogPO update = new ImageLogPO();
        update.setId(id);
        update.setLog(existing.getLog() + "\n" + DATE_TIME_FORMATTER.format(Instant.now()) + "  " + text);
        imageLogDao.updateSelective(update);
    }

    @Override
    public void updateSelective(ImageLogVO imageLog) {
        imageLogDao.updateSelective(toPO(imageLog));
    }

    @Override
    public List<ImageLogVO> querySuccessOrderByUpdatedAtDesc() {
        return imageLogDao.selectSuccessOrderByUpdatedAtDesc().stream().map(this::toVO).toList();
    }

    private ImageLogVO toVO(ImageLogPO po) {
        return ImageLogVO.builder()
                .id(po.getId())
                .fileName(po.getFileName())
                .name(po.getName())
                .version(po.getVersion())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .username(po.getUsername())
                .log(po.getLog())
                .build();
    }

    private ImageLogPO toPO(ImageLogVO vo) {
        ImageLogPO po = new ImageLogPO();
        po.setId(vo.getId());
        po.setFileName(vo.getFileName());
        po.setName(vo.getName());
        po.setVersion(vo.getVersion());
        po.setStatus(vo.getStatus());
        po.setCreatedAt(vo.getCreatedAt());
        po.setUpdatedAt(vo.getUpdatedAt());
        po.setUsername(vo.getUsername());
        po.setLog(vo.getLog());
        return po;
    }
}
