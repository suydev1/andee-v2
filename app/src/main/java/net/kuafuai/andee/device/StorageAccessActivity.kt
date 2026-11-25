package net.kuafuai.andee.device

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import net.kuafuai.andee.config.VoiceConfig

class StorageAccessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            ))
            finish()
            return
        }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 2)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        val uri = data?.data
        if (request == 2 && result == RESULT_OK && uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                VoiceConfig.setStorageTree(this, uri.toString())
            }
        }
        finish()
    }
}
