package fr.projetcompensation.gymbuddy.fixtures;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI: {@code mvn compile exec:java -Dexec.mainClass=fr.projetcompensation.gymbuddy.fixtures.FixturesCli
 * -Dexec.args="--users 3000 --posts-per-user 5 --events 800 --reset"}.
 */
public record FixtureArgs(FixtureMagnitude magnitude, boolean reset, long seed) {

    public static FixtureArgs parse(String[] args) {
        Integer users = null;
        Integer friendships = null;
        Integer posts = null;
        Integer postsPerUser = null;
        Integer comments = null;
        Integer events = null;
        Integer applications = null;
        Integer messages = null;
        Integer media = null;
        boolean reset = false;
        long seed = FixtureSeed.DEFAULT;
        List<String> tokens = flatten(args);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("fixtures".equals(token)) {
                continue;
            }
            String flag = token;
            String value = null;
            if (token.startsWith("--") && token.contains("=")) {
                String[] parts = token.split("=", 2);
                flag = parts[0];
                value = parts[1];
            }
            switch (flag) {
                case "--reset" -> reset = true;
                case "--users" -> users = integerValue(tokens, i, flag, value);
                case "--friendships" -> friendships = integerValue(tokens, i, flag, value);
                case "--posts" -> posts = integerValue(tokens, i, flag, value);
                case "--posts-per-user" -> postsPerUser = integerValue(tokens, i, flag, value);
                case "--comments" -> comments = integerValue(tokens, i, flag, value);
                case "--events" -> events = integerValue(tokens, i, flag, value);
                case "--applications" -> applications = integerValue(tokens, i, flag, value);
                case "--messages" -> messages = integerValue(tokens, i, flag, value);
                case "--media" -> media = integerValue(tokens, i, flag, value);
                case "--seed" -> seed = longValue(tokens, i, flag, value);
                default -> throw new IllegalArgumentException("unknown argument: " + token);
            }
            if (needsValue(flag) && value == null) {
                i++;
            }
        }
        if (posts == null && postsPerUser != null) {
            int userCount = users == null ? FixtureMagnitude.DEMO.users() : users;
            posts = Math.multiplyExact(userCount, postsPerUser);
        }
        return new FixtureArgs(
                FixtureMagnitude.of(users, friendships, posts, comments, events, applications, messages, media),
                reset,
                seed);
    }

    private static boolean needsValue(String flag) {
        return !"--reset".equals(flag);
    }

    private static int integerValue(List<String> tokens, int index, String flag, String inline) {
        return Integer.parseInt(rawValue(tokens, index, flag, inline));
    }

    private static long longValue(List<String> tokens, int index, String flag, String inline) {
        return Long.parseLong(rawValue(tokens, index, flag, inline));
    }

    private static String rawValue(List<String> tokens, int index, String flag, String inline) {
        if (inline != null) {
            return inline;
        }
        int next = index + 1;
        if (next >= tokens.size()) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return tokens.get(next);
    }

    private static List<String> flatten(String[] args) {
        List<String> tokens = new ArrayList<>();
        if (args == null) {
            return tokens;
        }
        for (String arg : args) {
            if (arg != null && !arg.isBlank()) {
                tokens.add(arg.trim());
            }
        }
        return tokens;
    }
}
