package com.dailysatori.config

object AIConfig {
    const val timeoutMs = 30_000L
    const val maxSummaryLength = 500
    const val maxContentLength = 10_000
    const val maxTitleLength = 100
    const val maxTagsPerArticle = 10
    const val defaultTemperature = 0.5
    const val maxProcessContentLength = 50_000
    const val minHtmlLength = 50
    const val minTextLength = 20
    const val longTitleThreshold = 50
    const val randomRecommendationCount = 10
}

object BackupConfig {
    const val productionIntervalHours = 6L
    const val developmentIntervalHours = 24L
    const val fileExtension = ".enc"
}

object DatabaseConfig {
    const val name = "daily_satori.db"
    const val currentSchemaVersion = 13L
}

object DirectoryConfig {
    const val appDocuments = "DailySatori"
    const val backup = "backups"
    const val cache = "cache"
    const val images = "images"
    const val diaryImages = "diary_images"
}

object ImageConfig {
    const val maxUploadSizeBytes = 5 * 1024 * 1024L
    const val maxWidth = 1920
    const val maxHeight = 1080
    const val cacheDurationDays = 7L
    const val downloadTimeoutMs = 30_000L
}

object InputConfig {
    const val maxLength = 120
    const val maxLines = 8
    const val minLines = 1
    const val commentMaxLength = 500
    const val searchMinLength = 2
}

object NetworkConfig {
    const val timeoutMs = 30_000L
    const val maxRetries = 3
    const val retryDelayMs = 1_000L
}

object PaginationConfig {
    const val defaultPageSize = 20L
    const val maxPageSize = 100L
    const val minPageSize = 5L
}

object RemoteNewsConfig {
    const val articlesPageSize = 20
    const val digestsPageSize = 20
    const val feedsPageSize = 50
}

object SearchConfig {
    const val debounceTimeMs = 300L
    const val minLength = 2
    const val maxLength = 100
}

object SessionConfig {
    const val expireTimeMs = 30 * 60 * 1000L
}

object WebServiceConfig {
    const val portRangeStart = 51980
    const val portRangeEnd = 51999
}

object WebViewConfig {
    const val timeoutMs = 10_000L
    const val sessionMaxLifetimeMs = 240_000L
    const val maxConcurrentSessions = 2
    const val maxRedirects = 10
    const val domStabilityCheckDelayMs = 1500L
    const val loadProgressCheckDelayMs = 4_000L
}

object SettingKeys {
    const val openAIToken = "openai_token"
    const val openAIAddress = "openai_address"
    const val backupDir = "backup_directory"
    const val lastBackupTime = "last_backup_time"
    const val appLanguage = "app_language"
    const val webServerPassword = "web_server_password"
    const val webSocketUrl = "web_socket_url"
    const val deviceId = "device_id"
    const val pluginServerUrl = "plugin_server_url"
    const val isFirstLaunch = "is_first_launch"
    const val schemaVersion = "schema_version"
    const val backupPassword = "backup_password"
    const val remoteNewsBaseUrl = "remote_news_base_url"
    const val remoteNewsApiToken = "remote_news_api_token"
    const val crayfishNewsBaseUrl = "crayfish_news_base_url"
    const val crayfishNewsApiToken = "crayfish_news_api_token"
    const val xOAuthClientId = "x_oauth_client_id"
    const val weReadApiKey = "weread_api_key"
    const val legacyWeReadApiKey = weReadApiKey
    const val aiModelCatalogCache = "ai_model_catalog_cache"
}
