package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import sukun.minimalist.app.launcher.com.R

fun Context.showAccessibilityDisclosure(onDismiss: (() -> Unit)? = null): AlertDialog {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_accessibility_disclosure, null)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .setCancelable(true)
        .create()
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

    dialogView.findViewById<TextView>(R.id.closeAccessibility).setOnClickListener {
        dialog.dismiss()
        onDismiss?.invoke()
    }
    dialogView.findViewById<TextView>(R.id.actionAccessibility).setOnClickListener {
        dialog.dismiss()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    val notWorking = dialogView.findViewById<TextView>(R.id.notWorking)
    notWorking.visibility = View.VISIBLE
    notWorking.setOnClickListener {
        showToast(R.string.accessibility_not_working_help, Toast.LENGTH_LONG)
    }

    dialog.setOnCancelListener { onDismiss?.invoke() }
    dialog.show()
    return dialog
}
