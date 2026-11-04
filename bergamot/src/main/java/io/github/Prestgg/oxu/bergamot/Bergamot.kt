// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.bergamot

/**
 * Thin wrapper over the native bergamot-translator engine.
 *
 * All calls are blocking and CPU-heavy: call them from a background dispatcher.
 * Models are identified by an arbitrary key (Oxu uses "<from><to>", e.g. "enaz").
 */
object Bergamot {

    init {
        System.loadLibrary("oxu-bergamot")
    }

    /** Loads a model described by a Marian YAML [config]. No-op if [key] is already loaded. */
    fun load(key: String, config: String) = nativeLoad(key, config)

    fun isLoaded(key: String): Boolean = nativeIsLoaded(key)

    fun unload(key: String) = nativeUnload(key)

    fun unloadAll() = nativeUnloadAll()

    fun translate(key: String, texts: List<String>): List<String> =
        nativeTranslate(key, texts.toBytes()).fromBytes()

    /** Translates through two models, e.g. ru→en then en→az. */
    fun pivot(firstKey: String, secondKey: String, texts: List<String>): List<String> =
        nativePivot(firstKey, secondKey, texts.toBytes()).fromBytes()

    private fun List<String>.toBytes(): Array<ByteArray> =
        Array(size) { this[it].toByteArray(Charsets.UTF_8) }

    private fun Array<ByteArray>.fromBytes(): List<String> = map { String(it, Charsets.UTF_8) }

    private external fun nativeLoad(key: String, config: String)
    private external fun nativeIsLoaded(key: String): Boolean
    private external fun nativeUnload(key: String)
    private external fun nativeUnloadAll()
    private external fun nativeTranslate(key: String, inputs: Array<ByteArray>): Array<ByteArray>
    private external fun nativePivot(first: String, second: String, inputs: Array<ByteArray>): Array<ByteArray>
}
