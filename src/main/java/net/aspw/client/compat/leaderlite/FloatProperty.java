package net.aspw.client.compat.leaderlite;

import net.aspw.client.value.FloatValue;

import java.util.function.BooleanSupplier;

/**
 * LeaderLite's {@code FloatProperty}, as a NightX {@link FloatValue}.
 *
 * <p>This adapter <em>is</em> the value: it extends {@link FloatValue} so that
 * {@code Module#values} (which reflects over the module's fields and keeps everything that is a
 * {@link net.aspw.client.value.Value}) finds it. The old version wrapped a {@code FloatValue} in a
 * {@code value} field, which is why this setting never showed up in the GUI.</p>
 *
 * <p>LeaderLite's {@code getValue()} is <em>not</em> re-declared here: {@code Value} already publishes
 * it through its {@code value} property ({@code Float getValue()} from Java, because Kotlin's
 * {@code Float} is {@code Float} in a generic position), and that accessor is {@code final}.</p>
 */
public class FloatProperty extends FloatValue implements Property {

    /**
     * @param name         the setting name
     * @param defaultValue the default value
     * @param min          the minimum
     * @param max          the maximum
     */
    public FloatProperty(String name, float defaultValue, float min, float max) {
        this(name, defaultValue, min, max, null);
    }

    /**
     * @param name         the setting name
     * @param defaultValue the default value
     * @param min          the minimum
     * @param max          the maximum
     * @param displayable  LeaderLite's display predicate, or {@code null} for "always visible"
     */
    public FloatProperty(String name, float defaultValue, float min, float max, BooleanSupplier displayable) {
        // FloatValue's only (name, value, min, max, ...) constructors take the display callback, and
        // Value#canDisplay has a private backing field, so the predicate is passed here rather than
        // bound afterwards. It is deferred, so the predicate may read not-yet-assigned module fields,
        // and it is never null, so the Kotlin non-null parameter is satisfied.
        super(name, defaultValue, min, max, () -> displayable == null || displayable.getAsBoolean());
    }
}
