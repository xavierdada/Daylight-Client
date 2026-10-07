package net.aspw.client.compat.leaderlite;

import net.aspw.client.value.Value;

/**
 * Marker for the thin "LeaderLite property" adapters.
 *
 * <p>LeaderLite's modules declare settings as their own property objects
 * ({@code BooleanProperty}, {@code IntProperty}, {@code FloatProperty}, {@code ModeProperty}) and read
 * them with {@code getValue()}. This project declares settings as NightX {@code Value} objects and
 * reads them with {@code get()}. The adapters in this package bridge the two spellings.</p>
 *
 * <p><b>Why this is only a marker and not a base class.</b> This project's settings are discovered by
 * reflection: {@code Module.values} collects {@code javaClass.declaredFields} and keeps those whose
 * runtime type is a {@link Value} (see {@code Module.kt}, {@code values}). An adapter that merely
 * <em>holds</em> a {@code Value} is therefore invisible to the click GUI and to the config. The
 * adapters are consequently the {@code Value} itself - each one extends the concrete NightX value it
 * fronts - and Java's single inheritance leaves no room for a shared {@code Property} superclass. The
 * old base class was also never used as a type anywhere (no {@code Property<?>} variable, parameter,
 * field or collection), so nothing is lost here.</p>
 *
 * <p>Each adapter is its own wrapped value: {@code BooleanProperty extends BoolValue},
 * {@code IntProperty extends IntegerValue}, {@code FloatProperty extends FloatValue} and
 * {@code ModeProperty extends ListValue}. All four are therefore picked up by
 * {@code Module.values} exactly like a hand declared {@code BoolValue}/{@code ListValue} field, all four
 * keep LeaderLite's {@code getValue()} spelling where the two sides agree, and all four serialise through
 * {@code Value#toJson()}/{@code Value#fromJson(JsonElement)} for free.</p>
 *
 * <p>The generic parameter is deliberately gone: the adapters no longer share a {@code getValue()}
 * shape. {@code ModeProperty} in particular answers with a mode <em>index</em>
 * ({@code getModeIndex()}), because {@code ListValue#getValue()} already answers with the selected
 * mode <em>name</em> and a single class cannot have both.</p>
 */
public interface Property {
}
