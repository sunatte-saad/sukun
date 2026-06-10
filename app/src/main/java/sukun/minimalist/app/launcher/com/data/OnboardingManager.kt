package sukun.minimalist.app.launcher.com.data

import sukun.minimalist.app.launcher.com.R

enum class OnboardingAction {
    TAP_START,
    TAP_HOME_APP_SLOT,
    SET_DEFAULT_LAUNCHER,
    OPEN_SETTINGS,
    TAP_PRAYER_SETTINGS,
    TAP_FOCUS_MODE,
    TAP_SCREEN_TIME,
    TAP_LANGUAGE,
    OPEN_APP_DRAWER,
    SEARCH_APPS,
    TAP_APPEARANCE,
    TAP_FINISH,
}

data class OnboardingStep(
    val titleRes: Int,
    val bodyRes: Int,
    val actionHintRes: Int,
    val requiredAction: OnboardingAction,
    val isWelcome: Boolean = false,
    val isDone: Boolean = false,
)

object OnboardingManager {
    val steps: List<OnboardingStep> = listOf(
        OnboardingStep(
            R.string.onboarding_welcome_title,
            R.string.onboarding_welcome_body,
            R.string.onboarding_action_start,
            OnboardingAction.TAP_START,
            isWelcome = true,
        ),
        OnboardingStep(
            R.string.onboarding_home_apps_title,
            R.string.onboarding_home_apps_body,
            R.string.onboarding_action_home_apps,
            OnboardingAction.TAP_HOME_APP_SLOT,
        ),
        OnboardingStep(
            R.string.onboarding_launcher_title,
            R.string.onboarding_launcher_body,
            R.string.onboarding_action_launcher,
            OnboardingAction.SET_DEFAULT_LAUNCHER,
        ),
        OnboardingStep(
            R.string.onboarding_settings_title,
            R.string.onboarding_settings_body,
            R.string.onboarding_action_settings,
            OnboardingAction.OPEN_SETTINGS,
        ),
        OnboardingStep(
            R.string.onboarding_prayer_title,
            R.string.onboarding_prayer_body,
            R.string.onboarding_action_prayer,
            OnboardingAction.TAP_PRAYER_SETTINGS,
        ),
        OnboardingStep(
            R.string.onboarding_focus_title,
            R.string.onboarding_focus_body,
            R.string.onboarding_action_focus,
            OnboardingAction.TAP_FOCUS_MODE,
        ),
        OnboardingStep(
            R.string.onboarding_screen_time_title,
            R.string.onboarding_screen_time_body,
            R.string.onboarding_action_screen_time,
            OnboardingAction.TAP_SCREEN_TIME,
        ),
        OnboardingStep(
            R.string.onboarding_language_title,
            R.string.onboarding_language_body,
            R.string.onboarding_action_language,
            OnboardingAction.TAP_LANGUAGE,
        ),
        OnboardingStep(
            R.string.onboarding_search_apps_title,
            R.string.onboarding_search_apps_body,
            R.string.onboarding_action_search_apps,
            OnboardingAction.SEARCH_APPS,
        ),
        OnboardingStep(
            R.string.onboarding_hide_apps_title,
            R.string.onboarding_hide_apps_body,
            R.string.onboarding_action_hide_apps,
            OnboardingAction.OPEN_APP_DRAWER,
        ),
        OnboardingStep(
            R.string.onboarding_appearance_title,
            R.string.onboarding_appearance_body,
            R.string.onboarding_action_appearance,
            OnboardingAction.TAP_APPEARANCE,
        ),
        OnboardingStep(
            R.string.onboarding_done_title,
            R.string.onboarding_done_body,
            R.string.onboarding_action_finish,
            OnboardingAction.TAP_FINISH,
            isDone = true,
        ),
    )

    fun stepAt(index: Int): OnboardingStep = steps[index.coerceIn(0, steps.lastIndex)]
}
