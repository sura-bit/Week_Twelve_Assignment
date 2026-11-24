package org.example.weekttwelveassignment;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
}
