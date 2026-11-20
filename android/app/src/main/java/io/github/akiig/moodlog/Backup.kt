package io.github.akiig.moodlog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject

/**
 * Backup to a file you pick once: in Google Drive, on the phone or a memory card. Moodlog
 * keeps that file up to date after each change; the Drive app (not Moodlog) uploads it.
 * Moodlog itself still never goes on the internet.
 *
 * The file is the same as the iPhone app's backup (and its saved data), so a backup made
 * on one phone can be restored on the other. Restoring merges: nothing on the phone is lost.
 */
object Backup {
    const val MIME = "application/json"

    fun fileName(): String = "moodlog-backup.json"

    fun content(store: AppStore): String =
        store.toJson().put("moodlog_backup", 1).put("exported", Stamp.now()).toString(1)

    /** Reads a backup (or a moodlog.json from the iPhone app). */
    fun parse(text: String): SyncState {
        val doc = JSONObject(text)
        if (!doc.has("entries") && !doc.has("events") && !doc.has("dreams")) throw IllegalArgumentException("not a Moodlog backup")
        return SyncState.from(doc)
    }

    /** Remembers the picked file, so Moodlog can keep writing to it. */
    fun remember(context: Context, uri: Uri, prefs: Prefs) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (e: SecurityException) { }
        prefs.backupUri = uri.toString()
        prefs.backupName = displayName(context, uri) ?: fileName()
    }

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Where the file lives, in words: "Google Drive", "Downloads" and so on. */
    fun place(uri: Uri): String = when {
        uri.authority?.contains("google.android.apps.docs") == true -> "Google Drive"
        uri.authority?.contains("downloads") == true -> tr("Downloads")
        uri.authority?.contains("externalstorage") == true -> tr("This phone")
        else -> uri.authority ?: ""
    }

    /** Writes the backup; true if it worked. */
    fun write(context: Context, uri: Uri, store: AppStore): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content(store).toByteArray()) } != null
    } catch (e: Exception) {
        false
    }

    fun read(context: Context, uri: Uri): SyncState {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IllegalArgumentException("unreadable")
        return parse(text)
    }
}
