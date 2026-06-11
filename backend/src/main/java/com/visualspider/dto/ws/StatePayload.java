package com.visualspider.dto.ws;

public record StatePayload(String state, String message) {

    public static StatePayload loaded() {
        return new StatePayload("LOADED", null);
    }

    public static StatePayload error(String message) {
        return new StatePayload("ERROR", message);
    }

    public static StatePayload closed() {
        return new StatePayload("CLOSED", null);
    }
}
