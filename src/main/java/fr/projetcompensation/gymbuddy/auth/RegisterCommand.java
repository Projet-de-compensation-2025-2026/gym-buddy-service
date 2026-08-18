package fr.projetcompensation.gymbuddy.auth;

public record RegisterCommand(String email, String handle, String password, String displayName) {}
