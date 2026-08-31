package sukun.minimalist.app.launcher.com.helper.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.helper.BackupHelper

/**
 * Last-upload-wins sync via the signed-in user's Google Drive (app-data folder).
 * Uses the same JSON format as Settings → Export / Import.
 */
object AccountSyncManager {

    private const val TAG = "SukunSync"
    private const val MIN_UPLOAD_INTERVAL_MS = 15 * 60 * 1000L

    fun markLocalDirty(context: Context) {
        val prefs = Prefs(context.applicationContext)
        prefs.syncPayloadUpdatedAt = System.currentTimeMillis()
    }

    fun buildCloudBackup(context: Context): JSONObject {
        val prefs = Prefs(context.applicationContext)
        AnalyticsRollupManager.ensureCurrent(context)
        val version = prefs.syncPayloadUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        return BackupHelper.buildCloudBackupRoot(context.applicationContext, version)
    }

    /** True when restoring from Drive would replace meaningful local launcher data. */
    fun hasLocalDataWorthProtecting(context: Context): Boolean {
        val prefs = Prefs(context.applicationContext)
        if (prefs.syncPayloadUpdatedAt > 0L || prefs.syncLastUploadAt > 0L) return true
        if (prefs.remindersJson.isNotBlank()) return true
        if (prefs.todoItemsJson.isNotBlank()) return true
        if (prefs.dailyNotesList.isNotBlank()) return true
        if (prefs.prayerRollupJson.isNotBlank()) return true
        if (prefs.hiddenApps.isNotEmpty()) return true
        return false
    }

    fun applyRemoteBackup(context: Context, remote: GoogleDriveBackupHelper.RemoteBackup): Boolean {
        val appContext = context.applicationContext
        return if (remote.isFullFormat) {
            BackupHelper.applyBackupRootWithoutSyncDirty(
                appContext,
                JSONObject(remote.payloadJson),
                preserveKeys = BackupHelper.CLOUD_EXCLUDE_KEYS,
                updatedAt = remote.updatedAt,
            )
        } else {
            applyLegacyCompact(appContext, remote)
        }
    }

    private fun applyLegacyCompact(context: Context, remote: GoogleDriveBackupHelper.RemoteBackup): Boolean {
        val compact = CompactSyncPayload.fromJsonString(remote.payloadJson) ?: return false
        applyCompactPayload(context, compact, remote.updatedAt)
        return true
    }

    private fun applyCompactPayload(context: Context, payload: CompactSyncPayload, updatedAt: Long) {
        val prefs = Prefs(context.applicationContext)
        val beforeTrial = BackupHelper.captureTrialClocks(context)
        prefs.runWithoutSyncDirty {
            SyncSettingsCodec.decode(prefs, payload.settings)
            AnalyticsRollupManager.applyPrayerRollup(prefs, payload.prayer)
            AnalyticsRollupManager.applyScreenTimeRollup(prefs, payload.screenTime)
            if (payload.trialStart > 0L) {
                if (prefs.accountTrialStart == 0L || payload.trialStart < prefs.accountTrialStart) {
                    prefs.accountTrialStart = payload.trialStart
                }
                if (prefs.firstOpenTime == 0L || prefs.accountTrialStart < prefs.firstOpenTime) {
                    prefs.firstOpenTime = prefs.accountTrialStart
                }
            }
            prefs.syncPayloadUpdatedAt = updatedAt
            prefs.syncLastUploadAt = updatedAt
        }
        BackupHelper.enforceMonotonicTrial(context, beforeTrial)
    }

    private suspend fun driveToken(context: Context, activity: Activity? = null): String? {
        if (!Prefs(context.applicationContext).isSignedIn) return null
        return withContext(Dispatchers.Main.immediate) {
            GoogleDriveAuthHelper.getAccessToken(context, activity)
        }
    }

    sealed class DrivePullOutcome {
        data object Skipped : DrivePullOutcome()
        data object Applied : DrivePullOutcome()
        data class NeedsConfirmation(val remote: GoogleDriveBackupHelper.RemoteBackup) : DrivePullOutcome()
    }

    /**
     * Check Drive for a newer backup. Applies automatically only when local data is empty
     * (fresh install). Otherwise returns [DrivePullOutcome.NeedsConfirmation].
     */
    suspend fun evaluateDrivePull(
        context: Context,
        activity: Activity? = null,
        autoApplyIfEmpty: Boolean = true,
    ): DrivePullOutcome {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        if (!prefs.isSignedIn) return DrivePullOutcome.Skipped
        val token = driveToken(appContext, activity) ?: return DrivePullOutcome.Skipped
        val remote = GoogleDriveBackupHelper.download(appContext, token) ?: return DrivePullOutcome.Skipped
        if (remote.updatedAt <= prefs.syncPayloadUpdatedAt) return DrivePullOutcome.Skipped
        if (remote.updatedAt <= prefs.syncDeclinedRemoteUpdatedAt) return DrivePullOutcome.Skipped
        Log.i(TAG, "drive newer local=${prefs.syncPayloadUpdatedAt} remote=${remote.updatedAt}")
        if (hasLocalDataWorthProtecting(appContext) || !autoApplyIfEmpty) {
            return DrivePullOutcome.NeedsConfirmation(remote)
        }
        return if (applyRemoteBackup(appContext, remote)) {
            DrivePullOutcome.Applied
        } else {
            DrivePullOutcome.Skipped
        }
    }

    /** Download Drive backup when another device uploaded a newer snapshot. */
    suspend fun pullFromDriveIfNewer(
        context: Context,
        activity: Activity? = null,
        requireConfirmIfLocalData: Boolean = true,
    ): Boolean {
        return when (
            val outcome = evaluateDrivePull(
                context,
                activity,
                autoApplyIfEmpty = requireConfirmIfLocalData,
            )
        ) {
            is DrivePullOutcome.Applied -> true
            is DrivePullOutcome.NeedsConfirmation -> {
                if (!requireConfirmIfLocalData) {
                    applyRemoteBackup(context, outcome.remote)
                } else {
                    false
                }
            }
            DrivePullOutcome.Skipped -> false
        }
    }

    /** Upload only when this device has changes newer than Drive. Never silently overwrites local. */
    suspend fun pushToDriveIfLocalNewer(
        context: Context,
        force: Boolean = false,
        activity: Activity? = null,
        overwriteRemote: Boolean = false,
    ): Boolean {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        if (!prefs.isSignedIn) return false
        val token = driveToken(appContext, activity) ?: return false
        val now = System.currentTimeMillis()
        if (!force &&
            prefs.syncPayloadUpdatedAt <= prefs.syncLastUploadAt &&
            now - prefs.syncLastUploadAt < MIN_UPLOAD_INTERVAL_MS
        ) {
            return false
        }
        val remote = GoogleDriveBackupHelper.download(appContext, token)
        val root = buildCloudBackup(appContext)
        val fingerprint = BackupHelper.backupContentFingerprint(root)
        val localVersion = BackupHelper.backupUpdatedAt(root)
        if (remote != null && remote.isFullFormat) {
            val remoteRoot = JSONObject(remote.payloadJson)
            if (fingerprint == BackupHelper.backupContentFingerprint(remoteRoot)) {
                prefs.syncPayloadUpdatedAt = remote.updatedAt
                prefs.syncLastUploadAt = remote.updatedAt
                return false
            }
        }
        if (!overwriteRemote && remote != null && remote.updatedAt >= localVersion) {
            // Drive is newer — do not silently overwrite local; UI must confirm via evaluateDrivePull.
            Log.i(TAG, "push aborted — Drive newer remote=${remote.updatedAt}")
            return false
        }
        Log.i(TAG, "pushToDrive local=$localVersion remote=${remote?.updatedAt}")
        val ok = GoogleDriveBackupHelper.upload(token, root, localVersion)
        if (ok) {
            prefs.syncLastUploadAt = localVersion
        }
        return ok
    }

    /** User chose to keep this device — upload local snapshot and stop restore prompts for this remote version. */
    suspend fun keepLocalAndPushToDrive(
        context: Context,
        remoteUpdatedAt: Long,
        activity: Activity? = null,
    ): Boolean {
        val appContext = context.applicationContext
        markLocalDirty(appContext)
        Prefs(appContext).syncDeclinedRemoteUpdatedAt = remoteUpdatedAt
        return pushToDriveIfLocalNewer(
            appContext,
            force = true,
            activity = activity,
            overwriteRemote = true,
        )
    }

    fun recordDeclinedRemoteRestore(context: Context, remoteUpdatedAt: Long) {
        Prefs(context.applicationContext).syncDeclinedRemoteUpdatedAt = remoteUpdatedAt
    }

    /** After manual export/import — upload so every signed-in device gets this backup. */
    suspend fun pushManualBackupToDrive(context: Context, activity: Activity? = null): Boolean {
        markLocalDirty(context)
        return pushToDriveIfLocalNewer(context, force = true, activity = activity)
    }

    /** Pull (with confirm gate) then push if local still newer. */
    suspend fun syncNow(context: Context, forcePush: Boolean = false, activity: Activity? = null) {
        pullFromDriveIfNewer(context, activity, requireConfirmIfLocalData = true)
        pushToDriveIfLocalNewer(context, force = forcePush, activity = activity)
    }

    suspend fun onSignInSuccess(context: Context, activity: Activity): SyncResult {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        if (prefs.accountTrialStart == 0L && prefs.firstOpenTime > 0L) {
            prefs.accountTrialStart = prefs.firstOpenTime
        } else if (prefs.accountTrialStart == 0L) {
            prefs.accountTrialStart = System.currentTimeMillis()
            if (prefs.firstOpenTime == 0L) prefs.firstOpenTime = prefs.accountTrialStart
        }
        AnalyticsRollupManager.ensureCurrent(appContext)
        val token = GoogleDriveAuthHelper.getAccessToken(appContext, activity)
            ?: return SyncResult.Error("Google Drive access was not granted")
        val hadDriveBackup = GoogleDriveBackupHelper.download(appContext, token) != null
        return when (val outcome = evaluateDrivePull(appContext, activity, autoApplyIfEmpty = true)) {
            is DrivePullOutcome.NeedsConfirmation ->
                SyncResult.NeedsRestoreConfirm(outcome.remote)
            is DrivePullOutcome.Applied ->
                SyncResult.Success(restored = true)
            DrivePullOutcome.Skipped -> {
                pushToDriveIfLocalNewer(appContext, force = true, activity = activity)
                SyncResult.Success(restored = hadDriveBackup && prefs.syncLastUploadAt > 0L)
            }
        }
    }

    suspend fun deleteCloudData(context: Context, activity: Activity? = null): Boolean {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        val token = driveToken(appContext, activity)
        val success = if (token != null) {
            GoogleDriveBackupHelper.delete(token)
        } else {
            true
        }
        if (success) {
            prefs.syncPayloadUpdatedAt = 0L
            prefs.syncLastUploadAt = 0L
            prefs.syncDeclinedRemoteUpdatedAt = 0L
        }
        return success
    }

    fun signOut() {
        GoogleDriveAuthHelper.clearToken()
    }

    sealed class SyncResult {
        data class Success(val restored: Boolean) : SyncResult()
        data class NeedsRestoreConfirm(val remote: GoogleDriveBackupHelper.RemoteBackup) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
