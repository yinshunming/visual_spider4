package com.visualspider.dto;

public record HealthResponse(String status, String database, String timestamp, String message) {
}
