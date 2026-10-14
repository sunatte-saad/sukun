package sukun.minimalist.app.launcher.com.data

import sukun.minimalist.app.launcher.com.R

enum class OnboardingAction {
    TAP_START,
    SET_DEFAULT_LAUNCHER,
    OPEN_SETTINGS,
    TAP_PRAYER_SETTINGS,
    TAP_FOCUS_MODE,
    TAP_MINDFUL_MORNING,
    TAP_SCREEN_TIME,
    TAP_LANGUAGE,
    TAP_TEXT_SIZE,
    OPEN_APP_DRAWER,
    SEARCH_APPS,
    TAP_APPEARANCE,
}

data class OnboardingStep(
    val titleRes: Int,
    val bodyRes: Int,
    val actionHintRes: Int,
    val requiredAction: OnboardingAction,
    val iconRes: Int? = null,
    val isWelcome: Boolean = false,
)

object OnboardingManager {
    val steps: List<OnboardingStep> = listOf(
        OnboardingStep(
            R.string.onboarding_welcome_title,
            R.string.onboarding_welcome_body,
            R.string.onboarding_action_start,
            OnboardingAction.TAP_START,
            iconRes = R.mipmap.ic_launcher,
            isWelcome = true,
        ),
        OnboardingStep(
            R.string.onboarding_settings_title,
            R.string.onboarding_settings_body,
            R.string.onboarding_action_settings,
            OnboardingAction.OPEN_SETTINGS,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_focus_title,
            R.string.onboarding_focus_body,
            R.string.onboarding_action_focus,
            OnboardingAction.TAP_FOCUS_MODE,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_screen_time_title,
            R.string.onboarding_screen_time_body,
            R.string.onboarding_action_screen_time,
            OnboardingAction.TAP_SCREEN_TIME,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_search_apps_title,
            R.string.onboarding_search_apps_body,
            R.string.onboarding_action_search_apps,
            OnboardingAction.SEARCH_APPS,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_hide_apps_title,
            R.string.onboarding_hide_apps_body,
            R.string.onboarding_action_hide_apps,
            OnboardingAction.OPEN_APP_DRAWER,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_appearance_title,
            R.string.onboarding_appearance_body,
            R.string.onboarding_action_appearance,
            OnboardingAction.TAP_APPEARANCE,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_text_size_title,
            R.string.onboarding_text_size_body,
            R.string.onboarding_action_text_size,
            OnboardingAction.TAP_TEXT_SIZE,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_mindful_morning_title,
            R.string.onboarding_mindful_morning_body,
            R.string.onboarding_action_mindful_morning,
            OnboardingAction.TAP_MINDFUL_MORNING,
            iconRes = R.mipmap.ic_launcher,
        ),
        OnboardingStep(
            R.string.onboarding_launcher_title,
            R.string.onboarding_launcher_body,
            R.string.onboarding_action_launcher,
            OnboardingAction.SET_DEFAULT_LAUNCHER,
            iconRes = R.mipmap.ic_launcher,
        ),
    )

    fun stepAt(index: Int): OnboardingStep = steps[index.coerceIn(0, steps.lastIndex)]
}
