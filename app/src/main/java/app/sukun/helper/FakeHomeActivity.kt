package app.sukun.helper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.sukun.R

/**
 * Temporary alternate HOME activity used only when setting Sukun as default (legacy pre-Q flow).
 * Must not open [app.sukun.MainActivity].
 */
class FakeHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_home)
        finish()
    }
}