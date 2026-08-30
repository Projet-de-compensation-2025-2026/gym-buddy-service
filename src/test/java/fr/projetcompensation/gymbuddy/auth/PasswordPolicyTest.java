package fr.projetcompensation.gymbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void fsAcct03_passwordShorterThan10IsRejected() {
        assertThat(policy.validate("short", "alex@example.com", "alex"))
                .contains(new FieldIssue("password", "minLength"));
    }

    @Test
    void fsAcct03_passwordEqualToEmailIsRejected() {
        assertThat(policy.validate("Alex@Example.com", "alex@example.com", "alex"))
                .contains(new FieldIssue("password", "mustNotMatchIdentity"));
    }

    @Test
    void fsAcct03_passwordEqualToHandleIsRejected() {
        assertThat(policy.validate("AlexanderX", "alex@example.com", "alexanderx"))
                .contains(new FieldIssue("password", "mustNotMatchIdentity"));
    }

    @Test
    void fsAcct03_validPasswordIsAccepted() {
        assertThat(policy.validate("correct-horse", "alex@example.com", "alex")).isEmpty();
    }
}
