package com.rahul.Lease.Advice;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ApiResponse<T> {

    private LocalDateTime dateTime;
    private ApiError error;
    private T data;

    public ApiResponse() {
        this.dateTime = LocalDateTime.now();
    }

    public ApiResponse(T data) {
        this();
        this.data = data;
    }

    public ApiResponse(ApiError error) {
        this();
        this.error = error;
    }

}
