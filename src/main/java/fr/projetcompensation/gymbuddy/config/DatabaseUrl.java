package fr.projetcompensation.gymbuddy.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static DatabaseUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is blank");
        }
        String value = raw.trim();
        if (value.startsWith("jdbc:")) {
            return parseJdbc(value);
        }
        if (value.startsWith("postgres://") || value.startsWith("postgresql://")) {
            return parseUri(value);
        }
        throw new IllegalArgumentException("Unsupported DATABASE_URL scheme");
    }

    private static DatabaseUrl parseUri(String value) {
        URI uri = URI.create(value);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is missing a host");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath() == null ? "" : uri.getPath();
        String database = path.startsWith("/") ? path.substring(1) : path;
        if (database.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is missing a database name");
        }
        String userInfo = uri.getUserInfo();
        String username = "";
        String password = "";
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                username = decode(userInfo.substring(0, colon));
                password = decode(userInfo.substring(colon + 1));
            } else {
                username = decode(userInfo);
            }
        }
        String query = uri.getRawQuery();
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        if (query != null && !query.isBlank()) {
            jdbcUrl = jdbcUrl + "?" + query;
        }
        return new DatabaseUrl(jdbcUrl, username, password);
    }

    private static DatabaseUrl parseJdbc(String value) {
        int schemeEnd = value.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalArgumentException("DATABASE_URL JDBC form is missing ://");
        }
        String rest = value.substring(schemeEnd + 3);
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return new DatabaseUrl(value, "", "");
        }
        String userInfo = rest.substring(0, at);
        String hostAndDb = rest.substring(at + 1);
        String username = "";
        String password = "";
        int colon = userInfo.indexOf(':');
        if (colon >= 0) {
            username = decode(userInfo.substring(0, colon));
            password = decode(userInfo.substring(colon + 1));
        } else {
            username = decode(userInfo);
        }
        return new DatabaseUrl("jdbc:postgresql://" + hostAndDb, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
