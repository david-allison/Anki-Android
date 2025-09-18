/*
 * Copyright 2020-2025 Kirill Rozov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.androidbroadcast.vbpd

import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Base ViewBindingProperty interface that provides access to operations in the property delegate.
 */
public interface ViewBindingProperty<in R : Any, out T : ViewBinding> : ReadOnlyProperty<R, T> {
    /**
     * Clear all cached data. Will be called when own object destroys view
     */
    public fun clear() {
        // Do nothing
    }
}

/**
 * Eager implementation of [ViewBindingProperty] that holds [ViewBinding] instance.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class EagerViewBindingProperty<in R : Any, out T : ViewBinding>(
    private val viewBinding: T,
) : ViewBindingProperty<R, T> {
    public override fun getValue(
        thisRef: R,
        property: KProperty<*>,
    ): T = viewBinding
}

/**
 * Lazy implementation of [ViewBindingProperty] that creates [ViewBinding] instance on the first access.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class LazyViewBindingProperty<in R : Any, T : ViewBinding>(
    private val viewBinder: (R) -> T,
) : ViewBindingProperty<R, T> {
    private var viewBinding: T? = null

    public override fun getValue(
        thisRef: R,
        property: KProperty<*>,
    ): T = viewBinding ?: viewBinder(thisRef).also { viewBinding = it }

    @CallSuper
    public override fun clear() {
        viewBinding = null
    }
}
