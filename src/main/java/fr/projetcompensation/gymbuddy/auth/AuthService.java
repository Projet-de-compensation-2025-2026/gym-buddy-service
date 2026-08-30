package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.DuplicateUserException;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public final class AuthService {

    private static final String INVALID_CREDENTIALS = "invalid credentials";
    private static final String ACCOUNT_LOCKED = "account is locked";

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordHasher passwords;
    private final TokenService tokens;
    private final RefreshTokenStore refreshTokens;
    private final PasswordPolicy passwordPolicy;
    private final TransactionRunner transactions;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            ProfileRepository profiles,
            PasswordHasher passwords,
            TokenService tokens,
            RefreshTokenStore refreshTokens,
            PasswordPolicy passwordPolicy,
            TransactionRunner transactions,
            Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordPolicy = passwordPolicy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public RegisteredUser register(RegisterCommand command) {
        String email = required("email", command.email());
        String handle = required("handle", command.handle());
        String displayName = required("displayName", command.displayName());
        String password = command.password();
        passwordPolicy.validate(password, email, handle).ifPresent(issue -> {
            throw AuthException.validation("password does not meet requirements", issue);
        });
        return transactions.inTransaction(() -> persistRegistration(email, handle, displayName, password));
    }

    private RegisteredUser persistRegistration(String email, String handle, String displayName, String password) {
        if (users.findByEmail(email).isPresent()) {
            throw AuthException.conflict("email already registered", new FieldIssue("email", "duplicate"));
        }
        if (users.findByHandle(handle).isPresent()) {
            throw AuthException.conflict("handle already taken", new FieldIssue("handle", "duplicate"));
        }
        UserRole role = users.count() == 0 ? UserRole.ADMIN : UserRole.MEMBER;
        User user = new User(
                UUID.randomUUID(), email, handle, passwords.hash(password), role, UserStatus.ACTIVE, clock.instant());
        try {
            users.save(user);
            profiles.save(Profile.created(user.id(), displayName));
        } catch (DuplicateUserException ex) {
            throw AuthException.conflict("email or handle already registered", new FieldIssue("email", "duplicate"));
        }
        return new RegisteredUser(user.id(), user.email(), user.handle(), displayName, user.role());
    }

    public AuthSession login(LoginCommand command) {
        String email = required("email", command.email());
        String password = command.password();
        if (password == null || password.isBlank()) {
            throw AuthException.validation("password is required", new FieldIssue("password", "required"));
        }
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            throw AuthException.forbidden(INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.blockedFromAuth()) {
            passwords.matches(password, user.passwordHash());
            throw AuthException.forbidden(ACCOUNT_LOCKED);
        }
        if (!user.active() || !passwords.matches(password, user.passwordHash())) {
            throw AuthException.forbidden(INVALID_CREDENTIALS);
        }
        return sessionFor(user);
    }

    public AuthSession refresh(String refreshToken) {
        RefreshClaims claims = requireRefresh(refreshToken);
        User user =
                users.findById(claims.userId()).orElseThrow(() -> AuthException.unauthenticated(INVALID_CREDENTIALS));
        if (user.blockedFromAuth()) {
            throw AuthException.forbidden(ACCOUNT_LOCKED);
        }
        if (refreshTokens.findAllowedUserId(claims.jti()).isEmpty()) {
            throw AuthException.unauthenticated("refresh credential is not valid");
        }
        if (!user.active()) {
            throw AuthException.unauthenticated(INVALID_CREDENTIALS);
        }
        refreshTokens.revoke(claims.jti(), claims.expiresAt());
        return sessionFor(user);
    }

    public void logout(String refreshToken) {
        RefreshClaims claims = requireRefresh(refreshToken);
        refreshTokens.revoke(claims.jti(), claims.expiresAt());
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = requireActive(userId);
        if (currentPassword == null
                || currentPassword.isBlank()
                || !passwords.matches(currentPassword, user.passwordHash())) {
            throw AuthException.forbidden(INVALID_CREDENTIALS);
        }
        passwordPolicy
                .validate(newPassword, user.email(), user.handle(), "newPassword")
                .ifPresent(issue -> {
                    throw AuthException.validation("password does not meet requirements", issue);
                });
        users.update(user.withPasswordHash(passwords.hash(newPassword)));
        refreshTokens.revokeAll(user.id());
    }

    public void closeAccount(UUID userId, String password) {
        User user = requireActive(userId);
        if (password == null || password.isBlank()) {
            throw AuthException.validation("password is required", new FieldIssue("password", "required"));
        }
        if (!passwords.matches(password, user.passwordHash())) {
            throw AuthException.forbidden(INVALID_CREDENTIALS);
        }
        users.update(user.withStatus(UserStatus.CLOSED));
        refreshTokens.revokeAll(user.id());
    }

    private User requireActive(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AuthException.unauthenticated(INVALID_CREDENTIALS));
        if (user.blockedFromAuth() || !user.active()) {
            throw AuthException.unauthenticated(INVALID_CREDENTIALS);
        }
        return user;
    }

    private AuthSession sessionFor(User user) {
        IssuedTokens issued = tokens.issue(user);
        refreshTokens.store(issued.refreshJti(), user.id(), issued.refreshExpiresAt());
        return new AuthSession(issued);
    }

    private RefreshClaims requireRefresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AuthException.unauthenticated("refresh credential is missing");
        }
        return tokens.parseRefresh(refreshToken)
                .orElseThrow(() -> AuthException.unauthenticated("refresh credential is not valid"));
    }

    private static String required(String path, String value) {
        if (value == null || value.isBlank()) {
            throw AuthException.validation(path + " is required", new FieldIssue(path, "required"));
        }
        return value.trim();
    }
}
