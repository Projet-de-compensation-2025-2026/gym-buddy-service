package fr.projetcompensation.gymbuddy.auth.http;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {}
