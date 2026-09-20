// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A config property whose reads and writes use the receiving [Config] each time.
 *
 * Only the accessors are stored, so values stay current and collections remain independent.
 * Callers are responsible for accessing the collection on the appropriate thread.
 */
class ConfigProperty<T>(
    private val read: Config.() -> T,
    private val write: Config.(T) -> Unit,
) : ReadWriteProperty<Config, T> {
    override fun getValue(
        thisRef: Config,
        property: KProperty<*>,
    ): T = thisRef.read()

    override fun setValue(
        thisRef: Config,
        property: KProperty<*>,
        value: T,
    ) = thisRef.write(value)

    /** Converts between the stored value and the property's exposed type. */
    fun <R> mapped(
        decode: (T) -> R,
        encode: (R) -> T,
    ): ConfigProperty<R> =
        ConfigProperty(
            read = { decode(read()) },
            write = { write(encode(it)) },
        )
}

/** Uses the backend's boolean defaults and the existing non-undoable [Config.setBool]. */
fun configProperty(key: ConfigKey.Bool): ConfigProperty<Boolean> = ConfigProperty(read = { getBool(key) }, write = { setBool(key, it) })

/** Uses the backend's string defaults and the existing non-undoable [Config.setString]. */
fun configProperty(key: ConfigKey.String): ConfigProperty<String> =
    ConfigProperty(read = { getString(key) }, write = { setString(key, it) })

/**
 * Uses [Config.get] and [Config.set] for a JSON setting.
 *
 * An absent key returns [missingValue]; JSON null or an undecodable value returns null.
 * Other backend errors propagate. Assigning null stores JSON null; it does not remove the key.
 */
inline fun <reified T> jsonConfigProperty(
    key: String,
    missingValue: T? = null,
): ConfigProperty<T?> = ConfigProperty(read = { get<T?>(key, missingValue) }, write = { set(key, it) })

/** Uses [default] whenever a nullable property reads null, including invalid stored JSON. */
fun <T : Any> ConfigProperty<T?>.orDefault(default: T): ConfigProperty<T> = mapped(decode = { it ?: default }, encode = { it })
