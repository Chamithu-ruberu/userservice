package com.greensphere.userservice.utils;

import com.greensphere.userservice.constants.LogMessage;
import com.greensphere.userservice.dto.response.BaseResponse;
import com.greensphere.userservice.dto.response.DefaultResponse;
import com.greensphere.userservice.enums.ResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReturnResponseUtil {
    public static ResponseEntity<DefaultResponse> returnResponse(BaseResponse commonResponse) {
        if (commonResponse != null) {
            if (commonResponse.getCode().equals(ResponseCodeUtil.SUCCESS_CODE)) {
                log.info(LogMessage.RETURN_RESPONSE_UTIL, LogMessage.SUCCESS_RESPONSE);
                return ResponseEntity.ok(DefaultResponse.builder()
                        .code(commonResponse.getCode())
                        .title(commonResponse.getTitle())
                        .message(commonResponse.getMessage())
                        .data(commonResponse.getData())
                        .build());
            } else if (commonResponse.getCode().equals(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)) {
                log.info(LogMessage.RETURN_RESPONSE_UTIL, LogMessage.INTERNAL_SERVER_ERROR_RESPONSE);
                return ResponseEntity.internalServerError().body(DefaultResponse.builder()
                        .code(commonResponse.getCode())
                        .title(commonResponse.getTitle())
                        .message(commonResponse.getMessage())
                        .data(commonResponse.getData())
                        .build());
            } else {
                log.info(LogMessage.RETURN_RESPONSE_UTIL, LogMessage.FAILED_RESPONSE);
                return ResponseEntity.badRequest().body(DefaultResponse.builder()
                        .code(commonResponse.getCode())
                        .title(commonResponse.getTitle())
                        .message(commonResponse.getMessage())
                        .data(commonResponse.getData())
                        .build());
            }
        }
        log.info(LogMessage.RETURN_RESPONSE_UTIL, LogMessage.FAILED_RESPONSE);
        return ResponseEntity.badRequest().body(DefaultResponse.builder()
                .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                .title(ResponseStatus.ERROR.name())
                .message("Internal sever error.")
                .data(null)
                .build());
    }
}
