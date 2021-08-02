/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.graphics.Canvas
import android.graphics.Rect
import androidx.annotation.ColorInt
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptOptions

interface DimmedPromptBackgroundInterface {
    fun prepare(options: PromptOptions<*>, clipToBounds: Boolean, clipBounds: Rect)
    fun update(options: PromptOptions<*>, revealModifier: Float, alphaModifier: Float)
    fun draw(canvas: Canvas)
    fun contains(x: Float, y: Float): Boolean
    fun setColour(@ColorInt colour: Int)
}

class DimmedPromptBackgroundInterfaceAdapter(private val mPromptBackground: PromptBackground) : DimmedPromptBackgroundInterface {
    companion object {
        fun PromptBackground.toInterface(): DimmedPromptBackgroundInterface {
            return DimmedPromptBackgroundInterfaceAdapter(this)
        }
    }

    override fun prepare(options: PromptOptions<*>, clipToBounds: Boolean, clipBounds: Rect) =
        mPromptBackground.prepare(options, clipToBounds, clipBounds)

    override fun update(options: PromptOptions<*>, revealModifier: Float, alphaModifier: Float) =
        mPromptBackground.update(options, revealModifier, alphaModifier)

    override fun draw(canvas: Canvas) = mPromptBackground.draw(canvas)

    override fun contains(x: Float, y: Float): Boolean = mPromptBackground.contains(x, y)

    override fun setColour(colour: Int) = mPromptBackground.setColour(colour)
}
