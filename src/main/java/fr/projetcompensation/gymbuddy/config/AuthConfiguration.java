package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.auth.Argon2PasswordHasher;
import fr.projetcompensation.gymbuddy.auth.AuthService;
import fr.projetcompensation.gymbuddy.auth.JwtTokenService;
import fr.projetcompensation.gymbuddy.auth.PasswordHasher;
import fr.projetcompensation.gymbuddy.auth.PasswordPolicy;
import fr.projetcompensation.gymbuddy.auth.RedisRefreshTokenStore;
import fr.projetcompensation.gymbuddy.auth.RefreshTokenStore;
import fr.projetcompensation.gymbuddy.auth.TokenService;
import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AuthConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new Argon2PasswordHasher();
    }

    @Bean
    @ConditionalOnProperty(name = "JWT_ACCESS_SECRET")
    TokenService tokenService(@Value("${JWT_ACCESS_SECRET}") String secret, Clock clock) {
        return new JwtTokenService(secret, clock);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "REDIS_URL")
    RedisClient redisClient(@Value("${REDIS_URL}") String redisUrl) {
        return RedisClient.create(redisUrl);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(RedisClient.class)
    StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean
    @ConditionalOnBean(StatefulRedisConnection.class)
    RedisCommands<String, String> redisCommands(StatefulRedisConnection<String, String> connection) {
        return connection.sync();
    }

    @Bean
    @ConditionalOnBean(RedisCommands.class)
    RefreshTokenStore refreshTokenStore(RedisCommands<String, String> redisCommands, Clock clock) {
        return new RedisRefreshTokenStore(redisCommands, clock);
    }

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return template.execute(status -> work.get());
            }
        };
    }

    @Bean
    @ConditionalOnBean({
        UserRepository.class,
        ProfileRepository.class,
        TokenService.class,
        RefreshTokenStore.class,
        TransactionRunner.class
    })
    AuthService authService(
            UserRepository users,
            ProfileRepository profiles,
            PasswordHasher passwords,
            TokenService tokens,
            RefreshTokenStore refreshTokens,
            PasswordPolicy passwordPolicy,
            TransactionRunner transactions,
            Clock clock) {
        return new AuthService(users, profiles, passwords, tokens, refreshTokens, passwordPolicy, transactions, clock);
    }
}
