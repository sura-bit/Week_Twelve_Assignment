package org.example.weektwelveassignment;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
}
