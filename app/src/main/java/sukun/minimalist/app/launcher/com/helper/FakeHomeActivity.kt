package sukun.minimalist.app.launcher.com.helper

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import sukun.minimalist.app.launcher.com.MainActivity

/**
 * Temporary alternate HOME activity used only when setting Sukun as default (legacy pre-Q flow).
 * If the user picks this entry by mistake, forward to the real launcher instead of closing.
 */
class FakeHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
        )
        finish()
    }
}