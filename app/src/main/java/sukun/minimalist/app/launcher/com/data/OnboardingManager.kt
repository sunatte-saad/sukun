package sukun.minimalist.app.launcher.com.data

import sukun.minimalist.app.launcher.com.R

enum class OnboardingAction {
    TAP_START,
    TAP_HOME_APP_SLOT,
    SET_DEFAULT_LAUNCHER,
    OPEN_SETTINGS,
    TAP_PRAYER_SETTINGS,
    TAP_FOCUS_MODE,
    TAP_MINDFUL_MORNING,
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
    val iconRes: Int? = null,
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
            iconRes = R.drawable.logo,
            isWelcome = true,
        ),
        OnboardingStep(
            R.string.onboarding_home_apps_title,
            R.string.onboarding_home_apps_body,
            R.string.onboarding_action_home_apps,
            OnboardingAction.TAP_HOME_APP_SLOT,
            iconRes = R.drawable.ic_rounded,
        ),
        OnboardingStep(
            R.string.onboarding_launcher_title,
            R.string.onboarding_launcher_body,
            R.string.onboarding_action_launcher,
            OnboardingAction.SET_DEFAULT_LAUNCHER,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_settings_title,
            R.string.onboarding_settings_body,
            R.string.onboarding_action_settings,
            OnboardingAction.OPEN_SETTINGS,
            iconRes = R.drawable.ic_info,
        ),
        OnboardingStep(
            R.string.onboarding_prayer_title,
            R.string.onboarding_prayer_body,
            R.string.onboarding_action_prayer,
            OnboardingAction.TAP_PRAYER_SETTINGS,
            iconRes = R.drawable.ic_bell,
        ),
        OnboardingStep(
            R.string.onboarding_focus_title,
            R.string.onboarding_focus_body,
            R.string.onboarding_action_focus,
            OnboardingAction.TAP_FOCUS_MODE,
            iconRes = R.drawable.rounded_primary_gradient,
        ),
        OnboardingStep(
            R.string.onboarding_screen_time_title,
            R.string.onboarding_screen_time_body,
            R.string.onboarding_action_screen_time,
            OnboardingAction.TAP_SCREEN_TIME,
            iconRes = R.drawable.logo,
        ),
        OnboardingStep(
            R.string.onboarding_language_title,
            R.string.onboarding_language_body,
            R.string.onboarding_action_language,
            OnboardingAction.TAP_LANGUAGE,
            iconRes = R.drawable.logo,
        ),
        OnboardingStep(
            R.string.onboarding_search_apps_title,
            R.string.onboarding_search_apps_body,
            R.string.onboarding_action_search_apps,
            OnboardingAction.SEARCH_APPS,
            iconRes = R.drawable.ic_check,
        ),
        OnboardingStep(
            R.string.onboarding_hide_apps_title,
            R.string.onboarding_hide_apps_body,
            R.string.onboarding_action_hide_apps,
            OnboardingAction.OPEN_APP_DRAWER,
            iconRes = R.drawable.ic_hide,
        ),
        OnboardingStep(
            R.string.onboarding_appearance_title,
            R.string.onboarding_appearance_body,
            R.string.onboarding_action_appearance,
            OnboardingAction.TAP_APPEARANCE,
            iconRes = R.drawable.rounded_rect_shade_color,
        ),
        OnboardingStep(
            R.string.onboarding_mindful_morning_title,
            R.string.onboarding_mindful_morning_body,
            R.string.onboarding_action_mindful_morning,
            OnboardingAction.TAP_MINDFUL_MORNING,
            iconRes = R.drawable.logo,
        ),
        OnboardingStep(
            R.string.onboarding_done_title,
            R.string.onboarding_done_body,
            R.string.onboarding_action_finish,
            OnboardingAction.TAP_FINISH,
            iconRes = R.drawable.ic_check,
            isDone = true,
        ),
    )

    fun stepAt(index: Int): OnboardingStep = steps[index.coerceIn(0, steps.lastIndex)]
}
