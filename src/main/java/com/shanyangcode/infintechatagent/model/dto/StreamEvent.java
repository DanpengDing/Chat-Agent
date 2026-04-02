package com.shanyangcode.infintechatagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StreamEvent {

    private String type;

    private Object data;
}
