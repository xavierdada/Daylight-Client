package net.aspw.client.compat.leaderlite;

import net.aspw.client.value.IntegerValue;

import java.util.function.BooleanSupplier;

/**
 * LeaderLite's {@code IntProperty}, as a NightX {@link IntegerValue}.
 *
 * <p>This adapter <em>is</em> the value: it extends {@link IntegerValue} so that
 * {@code Module#values} (which reflects over the module's fields and keeps everything that is a
 * {@link net.aspw.client.value.Value}) finds it. The old version wrapped an {@code IntegerValue} in a
 * {@code value} field, which is why this setting never showed up in the GUI.</p>
 *
 * <p>LeaderLite's {@code getValue()} is <em>not</em> re-declared here: {@code Value} already publishes
 * it through its {@code value} property ({@code Integer getValue()} from Java, because Kotlin's
 * {@code Int} is {@code Integer} in a generic position), and that accessor is {@code final}.</p>
 */
public class IntProperty extends IntegerValue implements Property {

    /**
     * @param name         the setting name
     * @param defaultValue the default value
     * @param min          the minimum
     * @param max          the maximum
     */
    public IntProperty(String name, int defaultValue, int min, int max) {
        this(name, defaultValue, min, max, null);
    }

    /**
     * @param name         the setting name
     * @param defaultValue the default value
     * @param min          the minimum
     * @param max          the maximum
     * @param displayable  LeaderLite's display predicate, or {@code null} for "always visible"
     */
    public IntProperty(String name, int defaultValue, int min, int max, BooleanSupplier displayable) {
        // IntegerValue's only (name, value, min, max, ...) constructors take the display callback, and
        // Value#canDisplay has a private backing field, so the predicate is passed here rather than
        // bound afterwards. It is deferred, so the predicate may read not-yet-assigned module fields,
        // and it is never null, so the Kotlin non-null parameter is satisfied.
        super(name, defaultValue, min, max, () -> displayable == null || displayable.getAsBoolean());
    }
}
