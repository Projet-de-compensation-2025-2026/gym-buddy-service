package fr.projetcompensation.gymbuddy.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** JSONB does not preserve object key order; all consumers use the same decoder. */
public final class PreferredWindowJson {
    private static final ObjectMapper WINDOWS_JSON = new ObjectMapper();

    private PreferredWindowJson() {}

    public static String write(List<PreferredWindow> windows) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            PreferredWindow window = windows.get(i);
            json.append("{\"weekday\":")
                    .append(window.weekday())
                    .append(",\"start\":\"")
                    .append(window.start())
                    .append("\",\"end\":\"")
                    .append(window.end())
                    .append("\"}");
        }
        return json.append(']').toString();
    }

    public static List<PreferredWindow> read(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return List.of();
        }
        try {
            JsonNode arr = WINDOWS_JSON.readTree(raw);
            if (!arr.isArray()) {
                return List.of();
            }
            List<PreferredWindow> windows = new ArrayList<>();
            for (JsonNode node : arr) {
                windows.add(new PreferredWindow(
                        node.path("weekday").asInt(),
                        node.path("start").asText(),
                        node.path("end").asText()));
            }
            return List.copyOf(windows);
        } catch (Exception ex) {
            throw new IllegalStateException("preferred_windows is not valid JSON", ex);
        }
    }
}
