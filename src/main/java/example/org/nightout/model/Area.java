package example.org.nightout.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum Area {
    CAPE_TOWN("Cape Town", "cape-town"),
    CLAREMONT("Claremont", "claremont"),
    STELLENBOSCH("Stellenbosch", "stellenbosch");

    private final String displayName;
    private final String slug;

    Area(String displayName, String slug) {
        this.displayName = displayName;
        this.slug = slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSlug() {
        return slug;
    }

    public static List<Area> all() {
        return List.of(values());
    }

    public static Optional<Area> findBySlug(String slug) {
        String normalized = normalize(slug);
        return Arrays.stream(values())
                .filter(area -> area.slug.equals(normalized))
                .findFirst();
    }

    public static Area requireDisplayName(String displayName) {
        String normalized = normalize(displayName);
        return Arrays.stream(values())
                .filter(area -> area.slug.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Area must be Cape Town, Claremont, or Stellenbosch."));
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return normalized;
    }
}
