package sukun.minimalist.app.launcher.com.helper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import sukun.minimalist.app.launcher.com.R

/**
 * Temporary alternate HOME activity used only when setting Sukun as default (legacy pre-Q flow).
 * Must not open [sukun.minimalist.app.launcher.com.MainActivity].
 */
class FakeHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_home)
        finish()
    }
}