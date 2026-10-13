package com.csm.paas.core.domain.software.service;

import com.csm.paas.core.domain.software.model.entity.SoftwareListEntity;

/** 软件目录查询服务（K2：`GET /v1/software`）。 */
public interface ISoftwareQueryService {

    /**
     * 软件列表（厂商 `GET /v1/software?page&size&name&type&order&sort`）。
     *
     * @param host 用于拼 CS 类型软件的 `gaop://cs?...` 入口地址（取自请求头 `x-forwarded-host`）
     */
    SoftwareListEntity querySoftwareList(int page, int size, String name, String type,
                                        String order, String sort, String host);
}
