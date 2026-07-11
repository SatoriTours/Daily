package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DiaryRepositoryInsertTest {
    @Test
    fun diaryCreateReturnsTheInsertedIdInsideOneTransaction() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val repository = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt").readText()

        assertTrue(schema.contains("insertDiaryReturningId:"))
        assertTrue(schema.contains("RETURNING id;"))
        assertTrue(repository.contains("suspend fun create("))
        assertTrue(repository.contains("q.transactionWithResult"))
        assertTrue(repository.contains("q.insertDiaryReturningId(content, tags, mood, images, now, now).executeAsOne()"))
    }
}
