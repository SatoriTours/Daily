package com.dailysatori.service.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.service.diary.legacyDiaryImagePaths
import com.dailysatori.service.diary.isRemoteDiaryImage

/** A legacy image occurrence is an association, not a unique file: never deduplicate paths. */
internal object DiaryImageMigration {
    fun paths(value: String?): List<String> = legacyDiaryImagePaths(value)

    fun isRemote(path: String): Boolean = isRemoteDiaryImage(path)

    fun legacyImages(driver: SqlDriver): Map<Long, List<String>> =
        driver.executeQuery(null, "SELECT id,images FROM diary ORDER BY id", { cursor ->
            val result = linkedMapOf<Long,List<String>>()
            while (cursor.next().value) result[cursor.getLong(0)!!] = paths(cursor.getString(1))
            QueryResult.Value(result)
        },0).value

    data class ImageRow(val diaryId:Long,val kind:String,val localPath:String,val remoteUrl:String?,
        val position:Long,val createdAt:Long,val updatedAt:Long)

    fun expectedRows(driver:SqlDriver):List<ImageRow> = if("images" !in DatabaseSchemaInventory.strings(driver,"SELECT name FROM pragma_table_info('diary')")) emptyList() else driver.executeQuery(null,
        "SELECT id,images,created_at,updated_at FROM diary ORDER BY id", { c ->
            val rows=mutableListOf<ImageRow>()
            while(c.next().value) paths(c.getString(1)).forEachIndexed { index,path ->
                rows+=ImageRow(c.getLong(0)!!,"image",if(isRemote(path)) "" else path,path.takeIf(::isRemote),
                    index.toLong(),c.getLong(2)!!,c.getLong(3)!!)
            }
            QueryResult.Value(rows)
        },0).value

    fun actualNewRows(driver:SqlDriver,previousMaxId:Long):List<ImageRow> = driver.executeQuery(null,
        "SELECT diary_id,kind,local_path,remote_url,position,created_at,updated_at FROM diary_attachment WHERE id>? ORDER BY diary_id,position,id", { c ->
            val rows=mutableListOf<ImageRow>()
            while(c.next().value) rows+=ImageRow(c.getLong(0)!!,c.getString(1)!!,c.getString(2)!!,
                c.getString(3),c.getLong(4)!!,c.getLong(5)!!,c.getLong(6)!!)
            QueryResult.Value(rows)
        },1) { bindLong(0,previousMaxId) }.value

    fun migrate(driver: SqlDriver) {
        check(DatabaseSchemaInventory.strings(driver,
            "SELECT id FROM diary_attachment WHERE kind NOT IN ('image','audio','video','file')").isEmpty()) {
            "日记附件类型未知，原有数据已保留"
        }
        legacyImages(driver).forEach { (diaryId, images) ->
            val existing = driver.executeQuery(null,
                "SELECT id FROM diary_attachment WHERE diary_id=? ORDER BY created_at,id", { c ->
                    val ids = mutableListOf<Long>()
                    while(c.next().value) ids += c.getLong(0)!!
                    QueryResult.Value(ids)
                },1) { bindLong(0,diaryId) }.value
            existing.forEachIndexed { index, id ->
                driver.execute(null,"UPDATE diary_attachment SET position=? WHERE id=?",2) {
                    bindLong(0,images.size.toLong()+index); bindLong(1,id)
                }
            }
            images.forEachIndexed { index,path ->
                driver.execute(null,"""INSERT INTO diary_attachment
                    (diary_id,kind,local_path,remote_url,position,created_at,updated_at)
                    SELECT id,'image',?,?,?,created_at,updated_at FROM diary WHERE id=?""",4) {
                    bindString(0,if(isRemote(path)) "" else path)
                    bindString(1,path.takeIf(::isRemote))
                    bindLong(2,index.toLong()); bindLong(3,diaryId)
                }
            }
        }
        driver.execute(null,"CREATE INDEX IF NOT EXISTS idx_diary_attachment_position ON diary_attachment(diary_id,position,id)",0)
    }
}
