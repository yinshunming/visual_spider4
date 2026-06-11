package com.visualspider.dto.ws;

public record WsMessage<T>(String type, T payload) {
}
