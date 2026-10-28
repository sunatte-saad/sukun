package sukun.minimalist.app.launcher.com.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRepairTest {

    @Test
    fun darkModeOnScreenDoesNotRestart() {
        assertEquals(
            ThemeRepairAction.None,
            decideThemeRepair(
                configurationIsDark = true,
                savedThemeIsDark = true,
                appliedNightModeMatchesSaved = true,
                repairAlreadyUsed = false,
            ),
        )
    }

    @Test
    fun darkModeOnScreenDoesNotRestartAfterAnEarlierRepair() {
        assertEquals(
            ThemeRepairAction.None,
            decideThemeRepair(
                configurationIsDark = true,
                savedThemeIsDark = true,
                appliedNightModeMatchesSaved = true,
                repairAlreadyUsed = true,
            ),
        )
    }

    @Test
    fun lightModeOnScreenDoesNotRestart() {
        assertEquals(
            ThemeRepairAction.None,
            decideThemeRepair(
                configurationIsDark = false,
                savedThemeIsDark = false,
                appliedNightModeMatchesSaved = false,
                repairAlreadyUsed = false,
            ),
        )
    }

    @Test
    fun savedDarkButNotAppliedYetOnlySetsNightMode() {
        assertEquals(
            ThemeRepairAction.ApplyNightMode,
            decideThemeRepair(
                configurationIsDark = false,
                savedThemeIsDark = true,
                appliedNightModeMatchesSaved = false,
                repairAlreadyUsed = false,
            ),
        )
    }

    @Test
    fun savedLightButNotAppliedYetOnlySetsNightMode() {
        assertEquals(
            ThemeRepairAction.ApplyNightMode,
            decideThemeRepair(
                configurationIsDark = true,
                savedThemeIsDark = false,
                appliedNightModeMatchesSaved = false,
                repairAlreadyUsed = true,
            ),
        )
    }

    @Test
    fun stuckDarkPreferenceRecreatesOnce() {
        assertEquals(
            ThemeRepairAction.RecreateOnce,
            decideThemeRepair(
                configurationIsDark = false,
                savedThemeIsDark = true,
                appliedNightModeMatchesSaved = true,
                repairAlreadyUsed = false,
            ),
        )
    }

    @Test
    fun stuckLightPreferenceRecreatesOnce() {
        assertEquals(
            ThemeRepairAction.RecreateOnce,
            decideThemeRepair(
                configurationIsDark = true,
                savedThemeIsDark = false,
                appliedNightModeMatchesSaved = true,
                repairAlreadyUsed = false,
            ),
        )
    }

    @Test
    fun secondPassDoesNotRecreateAgain() {
        assertEquals(
            ThemeRepairAction.None,
            decideThemeRepair(
                configurationIsDark = false,
                savedThemeIsDark = true,
                appliedNightModeMatchesSaved = true,
                repairAlreadyUsed = true,
            ),
        )
    }
}
