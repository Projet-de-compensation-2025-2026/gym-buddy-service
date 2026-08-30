package fr.projetcompensation.gymbuddy.messaging.ws;

import fr.projetcompensation.gymbuddy.config.ApiCorsConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MessagingWebSocketConfiguration implements WebSocketConfigurer {

    private final MessagingWebSocketHandler handler;
    private final MessagingHandshakeInterceptor interceptor;

    public MessagingWebSocketConfiguration(
            MessagingWebSocketHandler handler, MessagingHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/ws")
                .addInterceptors(interceptor)
                .setAllowedOrigins(ApiCorsConfiguration.PAGES_ORIGIN);
    }
}
