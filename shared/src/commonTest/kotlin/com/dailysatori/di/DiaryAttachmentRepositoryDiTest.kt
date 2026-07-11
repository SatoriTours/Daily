package com.dailysatori.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DiaryAttachmentRepositoryDiTest {
    @Test
    fun sharedModuleRegistersDiaryAttachmentRepositoryWithAllConstructorDependencies() {
        val source = File("src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(source.contains("import com.dailysatori.data.repository.DiaryAttachmentRepository"))
        assertTrue(source.contains("single { DiaryAttachmentRepository(get(), get(), get()) }"))
    }
}
