// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>

package com.ichi2.anki.libanki.sched

import anki.decks.deckTreeNode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test

class DeckNodeTraversalTest {
    @Test
    fun `selecting child deck does not collect parent or sibling ids`() {
        // Build tree: Parent (id=1) -> Child1 (id=2), Child2 (id=3)
        val child1 = makeNode("Child1", deckId = 2, level = 2)
        val child2 = makeNode("Child2", deckId = 3, level = 2)
        val parent = makeNode("Parent", deckId = 1, level = 1, children = listOf(child1, child2))

        val selectedChild = parent.find(2L)!!
        val idsToRemove = mutableSetOf(selectedChild.did)
        selectedChild.forEach { idsToRemove.add(it.did) }

        assertThat(idsToRemove, containsInAnyOrder(2L))
        assertThat(idsToRemove, not(hasItem(1L)))
        assertThat(idsToRemove, not(hasItem(3L)))
    }

    @Test
    fun `selecting parent deck with nested children collects all descendants`() {
        // Build tree: A (id=1) -> B (id=2) -> C (id=3)
        //                      -> D (id=4)
        val c = makeNode("C", deckId = 3, level = 3)
        val b = makeNode("B", deckId = 2, level = 2, children = listOf(c))
        val d = makeNode("D", deckId = 4, level = 2)
        val a = makeNode("A", deckId = 1, level = 1, children = listOf(b, d))

        val idsToRemove = mutableSetOf(a.did)
        a.forEach { idsToRemove.add(it.did) }

        assertThat(idsToRemove, containsInAnyOrder(1L, 2L, 3L, 4L))
    }

    private fun makeNode(
        name: String,
        deckId: Long,
        level: Int,
        collapsed: Boolean = false,
        children: List<DeckNode> = emptyList(),
    ): DeckNode {
        val treeNode =
            deckTreeNode {
                this.name = name
                this.deckId = deckId
                this.level = level
                this.collapsed = collapsed
                children.forEach { this.children.add(it.node) }
                this.reviewCount = 0
                this.newCount = 0
                this.learnCount = 0
                this.filtered = false
            }
        return DeckNode(treeNode, name)
    }
}
