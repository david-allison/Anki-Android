// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import com.ichi2.anki.cardviewer.SingleCardSide

fun SpecialFields.all(side: SingleCardSide): List<SpecialField> = all(onAnswerSide = side == SingleCardSide.BACK)
