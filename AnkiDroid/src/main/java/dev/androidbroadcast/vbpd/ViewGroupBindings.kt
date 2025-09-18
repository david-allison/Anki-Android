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

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.viewbinding.ViewBinding
import dev.androidbroadcast.vbpd.internal.requireViewByIdCompat

/**
 * Create new [ViewBinding] associated with the [ViewGroup]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 */
public inline fun <VG : ViewGroup, T : ViewBinding> VG.viewBinding(crossinline vbFactory: (VG) -> T): ViewBindingProperty<VG, T> =
    when {
        isInEditMode -> EagerViewBindingProperty(vbFactory(this))
        else -> LazyViewBindingProperty { viewGroup -> vbFactory(viewGroup) }
    }

/**
 * Create new [ViewBinding] associated with the [ViewGroup]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewBindingRootId Root view's id that will be used as a root for the view binding
 */
@Deprecated("Order of arguments was changed", ReplaceWith("viewBinding(viewBindingRootId, vbFactory)"))
public inline fun <T : ViewBinding> ViewGroup.viewBinding(
    crossinline vbFactory: (View) -> T,
    @IdRes viewBindingRootId: Int,
): ViewBindingProperty<ViewGroup, T> = viewBinding(viewBindingRootId, vbFactory)

/**
 * Create new [ViewBinding] associated with the [ViewGroup]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewBindingRootId Root view's id that will be used as a root for the view binding
 */
public inline fun <T : ViewBinding> ViewGroup.viewBinding(
    @IdRes viewBindingRootId: Int,
    crossinline vbFactory: (View) -> T,
): ViewBindingProperty<ViewGroup, T> =
    when {
        isInEditMode -> EagerViewBindingProperty(requireViewByIdCompat(viewBindingRootId))
        else ->
            LazyViewBindingProperty { viewGroup: ViewGroup ->
                vbFactory(viewGroup.requireViewByIdCompat(viewBindingRootId))
            }
    }
