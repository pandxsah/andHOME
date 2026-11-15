package app.andhome.launcher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun drawerSearchAndSettingsAreReachable() {
        compose.onNodeWithText("All apps  ↑").performClick()
        compose.onNodeWithText("Search apps").performTextInput("zzzz_no_matching_app_98765")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No apps found").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No apps found").assertIsDisplayed()
        compose.onNodeWithText("Back to home").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Dark"))
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithText("Personal").assertIsDisplayed()
    }
    @Test fun profilesCanBeCreatedAndDeleted() {
        compose.onNodeWithText("Personal").performClick()
        compose.onNodeWithText("Profile name").performTextInput("Work test")
        compose.onNodeWithText("Create from current layout").performClick()
        compose.onNodeWithText("Work test").performClick()
        compose.onNodeWithText("Delete active profile").performClick()
        compose.onNodeWithText("Delete profile").performClick()
        compose.onNodeWithText("Personal").assertIsDisplayed()
    }
}
