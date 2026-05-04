package com.dailysatori.data.repository

import kotlin.test.Test
import kotlin.test.assertTrue

class BookRepositoryInsertApiTest {
    @Test
    fun repositoryExposesInsertAndReturnIdApi() {
        assertTrue(::insertAndReturnIdSignatureCompiles.name.isNotBlank())
    }

    private fun insertAndReturnIdSignatureCompiles(repository: BookRepository): Long =
        repository.insertAndReturnId(
            title = "测试书",
            author = "作者",
            category = "分类",
            coverImage = "",
            introduction = "简介",
        )
}
