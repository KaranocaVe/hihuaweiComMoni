package com.ascend.monitor.client;

public record ApiResponse<T>(Integer code, String msg, T data, Boolean success) {

    public boolean isSuccessful() {
        return Integer.valueOf(200).equals(code) && Boolean.TRUE.equals(success) && data != null;
    }
}
