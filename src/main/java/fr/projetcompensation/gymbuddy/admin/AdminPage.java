package fr.projetcompensation.gymbuddy.admin;

import java.util.List;
import java.util.function.Function;

public record AdminPage<T>(List<T> data, String next, int size) {

    static <T> AdminPage<T> of(List<T> rows, int size, Function<T, String> cursor) {
        if (rows.size() <= size) {
            return new AdminPage<>(List.copyOf(rows), null, size);
        }
        List<T> page = List.copyOf(rows.subList(0, size));
        T last = page.get(page.size() - 1);
        return new AdminPage<>(page, cursor.apply(last), size);
    }
}
