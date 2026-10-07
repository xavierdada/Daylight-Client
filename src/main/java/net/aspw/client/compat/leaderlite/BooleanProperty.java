package net.aspw.client.compat.leaderlite;

import net.aspw.client.value.BoolValue;

import java.util.function.BooleanSupplier;

/**
 * LeaderLite's {@code BooleanProperty}, as a NightX {@link BoolValue}.
 *
 * <p>This adapter <em>is</em> the value: it extends {@link BoolValue} so that
 * {@code Module#values} (which reflects over the module's fields and keeps everything that is a
 * {@link net.aspw.client.value.Value}) finds it, and so that the click GUI renders it and the config
 * serialises it. The old version wrapped a {@code BoolValue} in a {@code value} field, which is why
 * this setting never showed up in the GUI.</p>
 *
 * <p>LeaderLite's {@code getValue()}/{@code setValue(Boolean)} are <em>not</em> re-declared here:
 * {@code Value} already publishes exactly those names through its {@code value} property, whose
 * accessors ({@code Boolean getValue()}, {@code void setValue(Boolean)}) are {@code final}. Declaring
 * them again was an error, so the modules simply use the inherited ones.</p>
 */
public class BooleanProperty extends BoolValue implements Property {

    /**
     * @param name         the setting name
     * @param defaultValue the default value
     */
    public BooleanProperty(String name, boolean defaultValue) {
        this(name, defaultValue, null);
    }

    /**
     * @param name         the setting name
     * @param defaultValue the default value
     * @param displayable  LeaderLite's display predicate, or {@code null} for "always visible"
     */
    public BooleanProperty(String name, boolean defaultValue, BooleanSupplier displayable) {
        // The predicate has to be handed over here. BoolValue stores it in Value#canDisplay, whose
        // backing field is private, so a subclass can neither assign it after construction nor read it;
        // the constructor parameter, on the other hand, is legally usable in a super(...) argument.
        // The lambda is deferred - neither Value's nor BoolValue's constructor invokes canDisplay (the
        // click GUI invokes it while rendering) - so the predicate may read module fields that are still
        // unassigned at this point, and the Kotlin non-null parameter is never passed null.
        super(name, defaultValue, () -> displayable == null || displayable.getAsBoolean());
    }
}
