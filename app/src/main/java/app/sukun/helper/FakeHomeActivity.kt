package app.sukun.helper

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.sukun.MainActivity
import app.sukun.R

class FakeHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_home)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}