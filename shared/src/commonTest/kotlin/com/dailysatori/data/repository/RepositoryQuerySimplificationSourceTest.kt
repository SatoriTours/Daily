package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryQuerySimplificationSourceTest {
    @Test
    fun articleRemoteLookupUsesTargetedSqlForArticlesWithoutUrl() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()

        assertTrue(source.contains("selectArticleByNoUrlRemoteContent"))
        assertFalse(source.contains("q.selectArticles().executeAsList().firstOrNull"))
    }

    @Test
    fun repositoryCountsUseSqlCountQueries() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val bookRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt").readText()
        val viewpointRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointRepository.kt").readText()
        val tagRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/TagRepository.kt").readText()

        assertTrue(schema.contains("bookCount:\nSELECT COUNT(*) FROM book;"))
        assertTrue(schema.contains("bookViewpointCount:\nSELECT COUNT(*) FROM book_viewpoint;"))
        assertTrue(schema.contains("tagCount:\nSELECT COUNT(*) FROM tag;"))
        assertTrue(bookRepo.contains("q.bookCount().executeAsOne()"))
        assertTrue(viewpointRepo.contains("q.bookViewpointCount().executeAsOne()"))
        assertTrue(tagRepo.contains("q.tagCount().executeAsOne()"))
        assertFalse(bookRepo.contains("selectAllBooks().executeAsList().size"))
        assertFalse(viewpointRepo.contains("selectAllViewpoints().executeAsList().size"))
        assertFalse(tagRepo.contains("selectAllTags().executeAsList().size"))
    }

    @Test
    fun bookViewpointContentSearchUsesSqlInsteadOfInMemoryFiltering() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointRepository.kt").readText()

        assertTrue(source.contains("q.searchViewpointsByContent"))
        assertFalse(source.contains("selectAllViewpoints().executeAsList().filter"))
    }

    @Test
    fun searchRepositoriesUseFtsQueriesWithLegacyLikeFallback() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val articleRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
        val diaryRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt").readText()
        val bookRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt").readText()
        val viewpointRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointRepository.kt").readText()
        val memoryRepo = File("src/commonMain/kotlin/com/dailysatori/data/repository/MemoryRepository.kt").readText()

        assertTrue(schema.contains("CREATE VIRTUAL TABLE article_fts USING fts5"))
        assertTrue(schema.contains("CREATE VIRTUAL TABLE diary_fts USING fts5"))
        assertTrue(schema.contains("CREATE VIRTUAL TABLE book_fts USING fts5"))
        assertTrue(schema.contains("CREATE VIRTUAL TABLE book_viewpoint_fts USING fts5"))
        assertTrue(schema.contains("CREATE VIRTUAL TABLE memory_entry_fts USING fts5"))
        assertTrue(schema.contains("searchArticlesFts:"))
        assertTrue(schema.contains("searchDiariesFts:"))
        assertTrue(schema.contains("searchBooksFts:"))
        assertTrue(schema.contains("searchViewpointsByContentFts:"))
        assertTrue(schema.contains("searchMemoryFts:"))
        assertTrue(schema.contains("MATCH :ftsQuery"))
        assertTrue(schema.contains("LIKE '%' || :legacyQuery || '%'"))
        assertTrue(articleRepo.contains("q.searchArticlesFts"))
        assertTrue(diaryRepo.contains("q.searchDiariesFts"))
        assertTrue(bookRepo.contains("q.searchBooksFts"))
        assertTrue(viewpointRepo.contains("q.searchViewpointsByContentFts"))
        assertTrue(memoryRepo.contains("q.searchMemoryFts"))
    }

    @Test
    fun taskCenterFilteringIsPushedIntoSql() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val repository = File("src/commonMain/kotlin/com/dailysatori/data/repository/AsyncTaskRepository.kt").readText()

        assertTrue(schema.contains("selectAsyncTasksForTaskCenterFilteredPage:"))
        assertTrue(schema.contains("type IN :types"))
        assertTrue(schema.contains("status IN :statuses"))
        assertTrue(repository.contains("selectAsyncTasksForTaskCenterFilteredPage"))
        assertFalse(repository.contains("filterAsyncTasks(tasks, filter)"))
    }

    @Test
    fun externalFavoriteArticleListUsesAggregatedJoin() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val allQuery = schema.substringAfter("selectExternalFavoriteArticles:").substringBefore("selectExternalFavoriteArticlesBySource:")
        val sourceQuery = schema.substringAfter("selectExternalFavoriteArticlesBySource:").substringBefore("searchExternalFavoriteArticlesBySourceFts:")

        assertTrue(allQuery.contains("JOIN ("))
        assertTrue(allQuery.contains("GROUP BY article_id"))
        assertFalse(allQuery.contains("ORDER BY (\n    SELECT MAX"))
        assertTrue(sourceQuery.contains("JOIN ("))
        assertTrue(sourceQuery.contains("GROUP BY article_id"))
        assertFalse(sourceQuery.contains("ORDER BY (\n    SELECT MAX"))
    }

    @Test
    fun externalFavoriteSourceSearchUsesScopedFtsQuery() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val repository = File("src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
        val viewModel = File("../app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt").readText()
        val query = schema.substringAfter("searchExternalFavoriteArticlesBySourceFts:")
            .substringBefore("selectArticleDailyCounts:")

        assertTrue(query.contains("source_id = :sourceId"))
        assertTrue(query.contains("article_fts MATCH :ftsQuery"))
        assertTrue(query.contains("GROUP BY article_id"))
        assertTrue(repository.contains("searchExternalFavoritesBySource("))
        assertTrue(repository.contains("searchExternalFavoritesBySourceSync("))
        assertTrue(viewModel.contains("currentState.externalFavoriteSourceId != null && currentState.searchQuery.isNotBlank()"))
        assertTrue(viewModel.indexOf("searchExternalFavoritesBySource(currentState.externalFavoriteSourceId") < viewModel.indexOf("currentState.searchQuery.isNotBlank() -> articleRepo.search"))
    }
}
