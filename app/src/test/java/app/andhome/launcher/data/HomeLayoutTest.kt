package app.andhome.launcher.data

import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    @Test fun movingBetweenSurfacesNeverDuplicates() {
        val layout = HomeLayout(home = listOf("a", "b")).pin("a", Placement.DOCK).pin("a", Placement.DOCK)
        assertEquals(listOf("b"), layout.home)
        assertEquals(listOf("a"), layout.dock)
    }
    @Test fun fullDockDoesNotLoseHomePin() {
        val layout = HomeLayout(home = listOf("keep"), dock = listOf("a", "b", "c", "d", "e"))
        assertEquals(layout, layout.pin("keep", Placement.DOCK))
    }
    @Test fun reorderClampsToEdgesAndIgnoresMissingIds() {
        val layout = HomeLayout(home = listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), layout.move("a", 1).home)
        assertEquals(layout, layout.move("a", -1))
        assertEquals(layout, layout.move("missing", 1))
        assertEquals(listOf("c", "a", "b"), layout.move("c", -50).home)
    }
    @Test fun removeClearsOnlyRequestedPin() {
        assertEquals(listOf("b"), HomeLayout(home = listOf("a", "b")).remove("a").home)
    }
    @Test fun emptyPinIsIgnored() { assertEquals(HomeLayout(), HomeLayout().pin("", Placement.HOME)) }
    @Test fun shrinkingDockPreservesOverflowPinsOnHome() {
        val layout = HomeLayout(home = listOf("home"), dock = listOf("a", "b", "c", "d", "e")).withDockLimit(3)
        assertEquals(listOf("a", "b", "c"), layout.dock)
        assertEquals(listOf("home", "d", "e"), layout.home)
        assertEquals(3, layout.dockLimit)
    }
    @Test fun hiddenAppsLeaveHomeAndDock() {
        val layout = HomeLayout(home = listOf("a"), dock = listOf("b")).hide("a").hide("b")
        assertTrue(layout.home.isEmpty()); assertTrue(layout.dock.isEmpty())
        assertEquals(setOf("a", "b"), layout.hidden)
    }
    @Test fun expandedDockAcceptsSevenPins() {
        val layout = (1..7).fold(HomeLayout(dockLimit = 7)) { current, i -> current.pin("$i", Placement.DOCK) }
        assertEquals(7, layout.dock.size)
        assertEquals(layout, layout.pin("8", Placement.DOCK))
    }
}
