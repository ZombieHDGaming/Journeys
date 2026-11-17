package dev.voidscape.journeys.api.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Immutable raw parameter values of a configured objective, condition or reward.
 *
 * <p>Values are kept as strings and parsed lazily through the {@link Parameter}'s type. Parsed values are cached,
 * so calling {@link #get(Parameter)} from a hot event listener does not re-parse on every event.</p>
 */
public final class ParameterValues {

    private static final ParameterValues EMPTY = new ParameterValues(Map.of());

    private final Map<String, String> raw;
    private final Map<String, Object> parsed = new ConcurrentHashMap<>();

    private ParameterValues(Map<String, String> raw) {
        this.raw = raw;
    }

    public static ParameterValues empty() {
        return EMPTY;
    }

    public static ParameterValues of(Map<String, String> raw) {
        if (raw.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> copy = new LinkedHashMap<>();
        raw.forEach((k, v) -> copy.put(Objects.requireNonNull(k), Objects.requireNonNull(v)));
        return new ParameterValues(Collections.unmodifiableMap(copy));
    }

    /** All raw values in insertion order. */
    public Map<String, String> asMap() {
        return raw;
    }

    public Optional<String> raw(String id) {
        return Optional.ofNullable(raw.get(id));
    }

    public boolean has(Parameter<?> parameter) {
        return raw.containsKey(parameter.id());
    }

    /**
     * Returns the parsed value of a parameter, falling back to its default.
     *
     * @throws IllegalStateException if the parameter is required and missing, or the stored value is invalid.
     *                               Quests are validated on load, so this indicates a programming error.
     */
    public <T> T get(Parameter<T> parameter) {
        T value = getOrNull(parameter);
        if (value == null) {
            throw new IllegalStateException("Missing value for parameter '" + parameter.id() + "'");
        }
        return value;
    }

    /** Returns the parsed value, its default, or null when neither is present. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getOrNull(Parameter<T> parameter) {
        String value = raw.getOrDefault(parameter.id(), parameter.defaultValue());
        if (value == null) {
            return null;
        }
        String cacheKey = parameter.id() + '\u0000' + parameter.type().id() + '\u0000' + value;
        Object cached = parsed.get(cacheKey);
        if (cached == null) {
            try {
                cached = parameter.type().parse(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid value for parameter '" + parameter.id() + "': " + e.getMessage(), e);
            }
            parsed.put(cacheKey, cached);
        }
        return (T) cached;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ParameterValues other && raw.equals(other.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return raw.toString();
    }
}
