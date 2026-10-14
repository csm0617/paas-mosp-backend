package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.DatabaseRecordDTO;
import com.csm.paas.core.api.dto.DatabaseRecordListDTO;
import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.domain.database.model.entity.DatabaseRecordEntity;
import com.csm.paas.core.domain.database.service.IDatabaseQueryService;
import com.csm.paas.core.types.util.PageUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厂商 `DatabaseRecordController`（`/v1/database_record`）的重写版。
 *
 * <p>只有一个端点：`GET /v1/database_record?page&size` ⇒ `{list,total}`，`updated_at DESC`
 * （厂商 `DatabaseRecordService.getAllDatabaseRecord:25-33`）。
 */
@RestController
@RequestMapping("/v1/database_record")
public class DatabaseRecordController {

    @Resource
    private IDatabaseQueryService databaseQueryService;

    @GetMapping
    public ResponseDTO<DatabaseRecordListDTO> getDatabaseRecord(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = databaseQueryService.queryRecords(PageUtils.normalizePage(page), PageUtils.normalizeSize(size));
        return ResponseDTO.ok(DatabaseRecordListDTO.builder()
                .list(result.getList().stream().map(DatabaseRecordController::toDTO).toList())
                .total(result.getTotal())
                .build());
    }

    private static DatabaseRecordDTO toDTO(DatabaseRecordEntity e) {
        return DatabaseRecordDTO.builder()
                .id(e.getId()).realStoreFilename(e.getRealStoreFilename())
                .execFileName(e.getExecFileName()).databaseName(e.getDatabaseName())
                .updatedAt(e.getUpdatedAt()).execFaultMessage(e.getExecFaultMessage())
                .status(e.getStatus())
                .build();
    }
}
