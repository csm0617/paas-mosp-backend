package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.ResponseDTO;
import com.csm.paas.core.api.dto.MappingInfoDTO;
import com.csm.paas.core.api.dto.ServiceAuthDTO;
import com.csm.paas.core.api.dto.ServiceItemDTO;
import com.csm.paas.core.api.dto.ServiceListResponseDTO;
import com.csm.paas.core.domain.software.model.entity.ServiceListEntity;
import com.csm.paas.core.domain.software.model.entity.ServiceRuntimeEntity;
import com.csm.paas.core.domain.software.service.IServiceQueryService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 厂商 GET /v1/service 的重写版（纵向切片）。
 * <p>Trigger 只做路由与 DTO 转换，业务在 Domain。<b>原型尚未实现认证</b>（厂商侧需要 gateway-token cookie）。
 */
@RestController
public class ServiceController {

    @Resource
    private IServiceQueryService serviceQueryService;

    @Value("${gaop.k8s.namespace:gaop-namespace}")
    private String namespace;

    @GetMapping("/v1/service")
    public ResponseDTO<ServiceListResponseDTO> list() {
        ServiceListEntity entity = serviceQueryService.queryServiceList(namespace);

        List<ServiceItemDTO> items = new ArrayList<>();
        for (ServiceRuntimeEntity s : entity.getServices()) {
            ServiceAuthDTO authDTO = s.getServiceAuth() == null ? null : ServiceAuthDTO.builder()
                    .serviceId(s.getServiceAuth().getServiceId())
                    .auth(s.getServiceAuth().getAuth())
                    .authType(s.getServiceAuth().getAuthType())
                    .authTime(s.getServiceAuth().getAuthTime())
                    .authInterval(s.getServiceAuth().getAuthInterval())
                    .build();

            items.add(ServiceItemDTO.builder()
                    .service(s.getServiceSnapshot())
                    .serviceAuth(authDTO)
                    // 厂商运行态恒为该三键对象（值可为 null），前端据此开映射面板
                    .mappingInfo(MappingInfoDTO.builder().build())
                    .deployment(s.getDeploymentSnapshot())
                    .hostIP(s.getHostIP())
                    .restartCount(s.getRestartCount())
                    .fixedName(s.getName())
                    .build());
        }

        return ResponseDTO.ok(ServiceListResponseDTO.builder()
                .runningDeployment(entity.getRunningDeployment())
                .total(entity.getTotal())
                .count(entity.getCount())
                .services(items)
                .build());
    }
}
