package fr.projetcompensation.gymbuddy.auth.http;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 32) String handle,
        @NotBlank String password,
        @NotBlank @Size(max = 80) String displayName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=%s, handle=%s, displayName=%s]".formatted(email, handle, displayName);
    }
}
