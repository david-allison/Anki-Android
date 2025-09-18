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

@file:JvmName("ViewHolderBindings")

package dev.androidbroadcast.vbpd

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import dev.androidbroadcast.vbpd.internal.requireViewByIdCompat

/**
 * Create new [ViewBinding] associated with the [ViewHolder]
 */
@Suppress("UnusedReceiverParameter")
public fun <VH : ViewHolder, T : ViewBinding> ViewHolder.viewBinding(viewBinder: (VH) -> T): ViewBindingProperty<VH, T> =
    LazyViewBindingProperty(viewBinder)

/**
 * Create new [ViewBinding] associated with the [ViewHolder]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewProvider Provide a [View] from the [ViewHolder]. By default call [ViewHolder.itemView]
 */
@Suppress("UnusedReceiverParameter")
public inline fun <VH : ViewHolder, T : ViewBinding> ViewHolder.viewBinding(
    crossinline vbFactory: (View) -> T,
    crossinline viewProvider: (VH) -> View = ViewHolder::itemView,
): ViewBindingProperty<VH, T> =
    LazyViewBindingProperty { viewHolder: VH ->
        viewProvider(viewHolder).let(vbFactory)
    }

/**
 * Create new [ViewBinding] associated with the [ViewHolder]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewBindingRootId Root view's id that will be used as a root for the view binding
 */
@Suppress("UnusedReceiverParameter")
public inline fun <VH : ViewHolder, T : ViewBinding> ViewHolder.viewBinding(
    crossinline vbFactory: (View) -> T,
    @IdRes viewBindingRootId: Int,
): ViewBindingProperty<VH, T> =
    LazyViewBindingProperty { viewHolder: VH ->
        vbFactory(viewHolder.itemView.requireViewByIdCompat(viewBindingRootId))
    }
