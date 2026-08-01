package com.ascend.monitor.client;

public class SourceApiException extends RuntimeException {

    public SourceApiException(String message) {
        super(message);
    }

    public SourceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
