package com.leo.erp.common.web.service;

import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.dto.MetaCodeResponse;
import com.leo.erp.common.web.dto.MetaErrorCodeResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MetaService {

    public MetaCodeResponse codes() {
        return new MetaCodeResponse(
                errorCodes()
        );
    }

    private List<MetaErrorCodeResponse> errorCodes() {
        return Arrays.stream(ErrorCode.values())
                .filter(errorCode -> errorCode != ErrorCode.SUCCESS)
                .map(errorCode -> new MetaErrorCodeResponse(
                        errorCode.name(),
                        errorCode.getCode(),
                        errorCode.getMessage()
                ))
                .toList();
    }
}
