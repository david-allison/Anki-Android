/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>                      *
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
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptOptions

class DimmedPromptBackgroundAdapter(private val mDimmedPromptBackgroundInterface: DimmedPromptBackgroundInterface) : PromptBackground() {
    override fun update(options: PromptOptions<out PromptOptions<*>>, revealModifier: Float, alphaModifier: Float) {
        mDimmedPromptBackgroundInterface.update(options, revealModifier, alphaModifier)
    }

    override fun draw(canvas: Canvas) {
        mDimmedPromptBackgroundInterface.draw(canvas)
    }

    override fun contains(x: Float, y: Float): Boolean {
        return mDimmedPromptBackgroundInterface.contains(x, y)
    }

    override fun setColour(colour: Int) {
        mDimmedPromptBackgroundInterface.setColour(colour)
    }

    override fun prepare(options: PromptOptions<out PromptOptions<*>>, clipToBounds: Boolean, clipBounds: Rect) {
        mDimmedPromptBackgroundInterface.prepare(options, clipToBounds, clipBounds)
    }

    companion object {
        fun DimmedPromptBackgroundInterface.castToPromptBackground(): PromptBackground {
            return DimmedPromptBackgroundAdapter(this)
        }
    }
}
