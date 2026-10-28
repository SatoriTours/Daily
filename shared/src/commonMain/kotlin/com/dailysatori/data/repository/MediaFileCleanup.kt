package com.dailysatori.data.repository

import com.dailysatori.platform.FileManager
import com.dailysatori.service.backup.portablePath
import com.dailysatori.shared.db.DailySatoriDatabase

internal fun mediaFileIsReferenced(db:DailySatoriDatabase,path:String,appDataDir:String):Boolean {
    fun normalized(value:String):String {
        if(value.startsWith("/") && !value.startsWith("$appDataDir/") && "/DailySatori/" !in value && "/app_flutter/" !in value) return value
        return portablePath(value,appDataDir) ?: value
    }
    val wanted=normalized(path)
    return db.dailySatoriQueries.selectMediaFileReferences().executeAsList()
        .filterNotNull().any { normalized(it)==wanted }
}

/** Run only after the logical delete committed. Reserve the SQLite writer while checking and unlinking. */
internal fun deleteUnreferencedMedia(db:DailySatoriDatabase,files:FileManager?,paths:List<String>) {
    if(files==null || paths.none(String::isNotBlank)) return
    db.dailySatoriQueries.transaction {
        db.dailySatoriQueries.acquireMediaCleanupLock()
        paths.filter(String::isNotBlank).distinct().forEach { path ->
            val candidate=if(path.startsWith("/")) path else "${files.getAppDataDir()}/$path"
            if(files.isAppDataPath(candidate) && files.listFiles(candidate).isEmpty() &&
                !mediaFileIsReferenced(db,path,files.getAppDataDir())) files.deleteAppOwnedFile(candidate)
        }
    }
}
