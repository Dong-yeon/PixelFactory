package com.pixelfactory.oee.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pixelfactory.oee.domain.OeeWindow;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OeeWindowResponse(LocalDateTime from, LocalDateTime to, String shift) {

    public static OeeWindowResponse from(OeeWindow window) {
        return new OeeWindowResponse(window.from(), window.to(), window.shift());
    }
}
