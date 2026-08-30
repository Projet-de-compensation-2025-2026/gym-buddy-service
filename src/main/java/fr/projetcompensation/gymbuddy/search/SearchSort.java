package fr.projetcompensation.gymbuddy.search;

public enum SearchSort {
    RELEVANCE("relevance"),
    DISTANCE("distance"),
    STARTS_AT("starts_at");

    private final String wireValue;

    SearchSort(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static SearchSort fromWire(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        for (SearchSort sort : values()) {
            if (sort.wireValue.equals(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unknown sort");
    }
}
