package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.storageLabelTops
import org.junit.Assert.*
import org.junit.Test

class StorageChartLabelsTest {
    @Test
    fun crowdedTinySlicesKeepTheirOrderAndStayWithinTheChart() {
        val heights = listOf(30f, 45f, 30f, 15f, 45f)
        val positions = storageLabelTops(listOf(12f, 12.1f, 12.2f, 12.3f, 12.4f), heights, 8f, 240f, 10f)
        assertTrue(positions.first() >= 8f)
        assertTrue(positions.last() + heights.last() <= 240f)
        positions.indices.drop(1).forEach { index ->
            assertTrue(positions[index] >= positions[index - 1] + heights[index - 1] + 10f)
        }
    }

    @Test
    fun bottomClusterMovesUpWithoutPushingEarlierLabelsOutOfBounds() {
        val positions = storageLabelTops(listOf(180f, 180.1f, 180.2f), List(3) { 30f }, 8f, 192f, 10f)
        assertEquals(listOf(82f, 122f, 162f), positions)
    }

    @Test
    fun isolatedLabelsStayCenteredOnTheirArcs() {
        assertEquals(listOf(20f, 85f, 155f),
            storageLabelTops(listOf(30f, 100f, 170f), listOf(20f, 30f, 30f), 8f, 192f, 10f))
        assertTrue(storageLabelTops(emptyList(), emptyList(), 8f, 192f, 10f).isEmpty())
    }
}
