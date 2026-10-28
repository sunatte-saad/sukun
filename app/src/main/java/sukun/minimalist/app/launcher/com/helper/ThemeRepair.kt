package sukun.minimalist.app.launcher.com.helper

/**
 * Decides whether Home should touch night mode after it is already on screen.
 *
 * The activity used to recreate whenever a resolved color int differed from cream or ink.
 * On some Android versions that int never matches while dark mode is already applied, and
 * recreate() keeps the same process, so the check failed again forever. Killing the process
 * was the only way out. A matching configuration is left alone, including that color case.
 */
internal enum class ThemeRepairAction {
    None,
    ApplyNightMode,
    RecreateOnce,
}

internal object ThemeRepairState {
    var used: Boolean = false
}

internal fun decideThemeRepair(
    configurationIsDark: Boolean,
    savedThemeIsDark: Boolean,
    appliedNightModeMatchesSaved: Boolean,
    repairAlreadyUsed: Boolean,
): ThemeRepairAction {
    if (configurationIsDark == savedThemeIsDark) return ThemeRepairAction.None
    if (!appliedNightModeMatchesSaved) return ThemeRepairAction.ApplyNightMode
    if (repairAlreadyUsed) return ThemeRepairAction.None
    return ThemeRepairAction.RecreateOnce
}
