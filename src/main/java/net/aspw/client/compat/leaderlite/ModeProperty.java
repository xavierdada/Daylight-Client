package net.aspw.client.compat.leaderlite;

import net.aspw.client.value.ListValue;

import java.util.function.BooleanSupplier;

/**
 * LeaderLite's {@code ModeProperty}, as a NightX {@link ListValue}.
 *
 * <p>LeaderLite stores a mode as an index into its {@code modes} array and its modules compare that
 * index ({@code mode.getValue() == 2}). NightX stores the selected mode <em>name</em> and
 * {@code ListValue.getValue()} already returns that {@code String}.</p>
 *
 * <p><b>The conflict, and the resolution.</b> A single class cannot answer both
 * {@code int getValue()} (LeaderLite's index) and {@code String getValue()} (NightX's mode name): Java
 * does not overload on return type, and {@code ListValue}'s accessor cannot be changed because the
 * click GUI and the config read it. This adapter therefore <em>is</em> the {@link ListValue} - it
 * extends it, so {@code Module#values} reflects it up like any other setting and the GUI shows it and
 * can select modes in it - while the index based API is renamed to {@link #getModeIndex()} and
 * {@link #setValue(int)}. That is the option with no shadow field and no duplicate GUI row: the module
 * keeps exactly one field per mode setting, and that field <em>is</em> the selectable value.</p>
 *
 * <p>Impact: the three mode settings of {@code Scaffold} keep their field names ({@code mode},
 * {@code rotationMode}, {@code moveFix}) and their field type ({@code ModeProperty}, which is still
 * assignable to {@code ListValue}), and the call sites that compared an index changed from
 * {@code getValue()} to {@code getModeIndex()} - count reported in the port notes. The inherited
 * {@code getValue()} is the {@code value} property's getter and returns the mode name, exactly like
 * {@link #getModeString()}.</p>
 */
public class ModeProperty extends ListValue implements Property {

    /** The mode names, in LeaderLite's order. Same array instance the inherited {@code values} holds. */
    private final String[] modes;

    /**
     * @param name         the setting name
     * @param defaultIndex index of the default mode inside {@code modes}
     * @param modes        the mode names
     */
    public ModeProperty(String name, int defaultIndex, String[] modes) {
        this(name, defaultIndex, modes, null);
    }

    /**
     * @param name         the setting name
     * @param defaultIndex index of the default mode inside {@code modes}
     * @param modes        the mode names
     * @param displayable  LeaderLite's display predicate, or {@code null} for "always visible"
     */
    public ModeProperty(String name, int defaultIndex, String[] modes, BooleanSupplier displayable) {
        // super(...) must come first, so the default name is resolved by a static helper, and the
        // display predicate has to be handed over here as well: ListValue keeps it in
        // Value#canDisplay, whose backing field is private, so it cannot be rebound after
        // construction. The lambda is deferred (the click GUI invokes it while rendering), so it may
        // read state that does not exist yet, and it is never null, so the Kotlin non-null parameter
        // is satisfied.
        super(name, modes, defaultMode(modes, defaultIndex), () -> displayable == null || displayable.getAsBoolean());
        this.modes = modes;
    }

    /**
     * Resolves LeaderLite's default index to the mode name {@code ListValue} wants. Called from the
     * {@code super(...)} arguments, where no instance state is available yet.
     *
     * @param modes        the mode names
     * @param defaultIndex the requested default index
     * @return the mode name at that index, clamped into range; never null
     */
    private static String defaultMode(String[] modes, int defaultIndex) {
        if (modes == null || modes.length == 0) {
            return "";
        }
        int index = defaultIndex < 0 ? 0 : (defaultIndex >= modes.length ? modes.length - 1 : defaultIndex);
        return modes[index];
    }

    /**
     * Index mode selection, LeaderLite's {@code setValue(int)} spelling. This is an overload, not an
     * override: the inherited {@code setValue(String)} selects the mode by name and is {@code final}.
     * Writes go through {@code Value#set(String)} so the change hooks and {@code changeValue}'s
     * name normalisation still run.
     *
     * @param index the index inside the mode list
     */
    public void setValue(int index) {
        if (this.modes != null && index >= 0 && index < this.modes.length) {
            super.set(this.modes[index]);
        }
    }

    /**
     * The selected mode as LeaderLite's index - the accessor that replaced the shadowed
     * {@code getValue()}.
     *
     * @return the index of the current mode, or -1 if the inherited value holds an unknown name
     */
    public int getModeIndex() {
        if (this.modes == null) {
            return -1;
        }
        String current = super.get();
        for (int i = 0; i < this.modes.length; i++) {
            if (this.modes[i].equalsIgnoreCase(current)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The selected mode's name. Same call as the inherited {@code getValue()}; kept because the ported
     * call sites and the module tag use this spelling.
     *
     * @return the current mode name
     */
    public String getModeString() {
        return super.get();
    }

    /**
     * The mode names, for callers that used to read the legacy adapter's public {@code modes} field.
     *
     * @return the mode names
     */
    public String[] getModes() {
        return this.modes;
    }
}
