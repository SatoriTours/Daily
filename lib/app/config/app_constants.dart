library;

/// 应用程序常量定义 & 业务相关的常量集中管理

import 'package:flutter/material.dart';

class AppConstants {
  // ====================== 分页相关 ======================
  static const int defaultPageSize = 20;
  static const int maxPageSize = 100;
  static const int minPageSize = 5;

  // ====================== 搜索相关 ======================
  static const Duration searchDebounceTime = Duration(milliseconds: 300);
  static const int minSearchLength = 2;
  static const int maxSearchLength = 100;

  // ====================== 图片相关 ======================
  static const int maxImageUploadSize = 5 * 1024 * 1024; // 5MB
  static const int maxImageWidth = 1920;
  static const int maxImageHeight = 1080;
  static const Duration imageCacheDuration = Duration(days: 7);
  static const Duration imageDownloadTimeout = Duration(seconds: 30);

  // ====================== AI配置 ======================
  static const Duration aiTimeout = Duration(seconds: 30);
  static const int maxSummaryLength = 500;
  static const int maxContentLength = 10000;
  static const int maxTitleLength = 100;
  static const int maxTagsPerArticle = 10;

  // ====================== 缓存配置 ======================
  static const Duration cacheExpiration = Duration(hours: 24);
  static const int maxCacheSize = 50 * 1024 * 1024; // 50MB
  static const int maxCacheEntries = 1000;

  // ====================== 备份配置 ======================
  static const Duration backupInterval = Duration(hours: 6);
  static const String backupFileExtension = '.zip';
  static const String backupDateFormat = 'yyyy-MM-dd_HH-mm-ss';

  // ====================== WebView配置 ======================
  static const Duration webViewTimeout = Duration(seconds: 25);
  static const Duration sessionMaxLifetime = Duration(minutes: 4);
  static const int maxConcurrentSessions = 2;
  static const int maxRedirects = 10;

  // ====================== 网络配置 ======================
  static const Duration networkTimeout = Duration(seconds: 30);
  static const int maxRetries = 3;
  static const Duration retryDelay = Duration(seconds: 1);

  // ====================== 数据库配置 ======================
  static const int databaseVersion = 1;
  static const String databaseName = 'daily_satori.db';
  static const int maxDatabaseSize = 100 * 1024 * 1024; // 100MB

  // ====================== 文件配置 ======================
  static const String appDocumentsDirectory = 'DailySatori';
  static const String backupDirectory = 'backups';
  static const String cacheDirectory = 'cache';
  static const String imagesDirectory = 'images';
  static const String logsDirectory = 'logs';

  // ====================== 日期格式 ======================
  static const String dateFormatDisplay = 'yyyy-MM-dd';
  static const String dateFormatFull = 'yyyy-MM-dd HH:mm:ss';
  static const String dateFormatISO = 'yyyy-MM-ddTHH:mm:ssZ';
  static const String dateFormatFile = 'yyyyMMdd_HHmmss';

  // ====================== 正则表达式 ======================
  static final RegExp urlRegex = RegExp(
    r'^(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?$',
    caseSensitive: false,
  );

  // 注意：正则中的单引号需要转义或使用双引号包裹整个字符串
  static final RegExp emailRegex = RegExp(
    r"^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,253}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,253}[a-zA-Z0-9])?)*$",
  );

  static final RegExp phoneRegex = RegExp(
    r'^\+?[0-9]{1,3}?[-.\s]?\(?[0-9]{1,4}?\)?[-.\s]?[0-9]{1,4}[-.\s]?[0-9]{1,9}$',
  );

  // ====================== 颜色常量 ======================
  static const int primaryColorValue = 0xFF2196F3;
  static const int secondaryColorValue = 0xFF03DAC6;
  static const int errorColorValue = 0xFFB00020;
  static const int successColorValue = 0xFF4CAF50;
  static const int warningColorValue = 0xFFFF9800;
  static const int infoColorValue = 0xFF2196F3;

  // ====================== 尺寸常量 ======================
  static const double defaultPadding = 16.0;
  static const double smallPadding = 8.0;
  static const double largePadding = 24.0;
  static const double buttonHeight = 48.0;
  static const double inputHeight = 48.0;
  static const double cardElevation = 2.0;
  static const double borderRadius = 8.0;
  static const double iconSize = 24.0;
  static const double smallIconSize = 16.0;
  static const double largeIconSize = 32.0;

  // ====================== 动画配置 ======================
  static const Duration animationDuration = Duration(milliseconds: 300);
  static const Duration shortAnimationDuration = Duration(milliseconds: 150);
  static const Duration longAnimationDuration = Duration(milliseconds: 500);
  static const Curve animationCurve = Curves.easeInOut;
  static const Curve bounceCurve = Curves.bounceOut;

  // ====================== 错误消息 ======================
  static const String errorNetwork = '网络连接错误，请检查网络后重试';
  static const String errorServer = '服务器错误，请稍后重试';
  static const String errorTimeout = '请求超时，请稍后重试';
  static const String errorUnknown = '发生未知错误，请稍后重试';
  static const String errorValidation = '输入数据无效，请检查后重试';
  static const String errorPermission = '权限不足，请联系管理员';
  static const String errorNotFound = '请求的资源不存在';
  static const String errorDuplicate = '数据已存在，无法重复添加';

  // ====================== 成功消息 ======================
  static const String successSave = '保存成功';
  static const String successDelete = '删除成功';
  static const String successUpdate = '更新成功';
  static const String successImport = '导入成功';
  static const String successExport = '导出成功';
  static const String successBackup = '备份成功';
  static const String successRestore = '恢复成功';

  // ====================== 提示消息 ======================
  static const String hintSearch = '输入关键词搜索...';
  static const String hintComment = '添加备注...';
  static const String hintUrl = '输入网址...';
  static const String hintTag = '添加标签...';
  static const String hintTitle = '输入标题...';
  static const String hintContent = '输入内容...';

  // ====================== 空状态消息 ======================
  static const String emptyArticles = '暂无文章，快去添加吧';
  static const String emptyDiary = '暂无日记，开始记录吧';
  static const String emptyBooks = '暂无书籍，快去阅读吧';
  static const String emptyTags = '暂无标签';
  static const String emptySearch = '没有找到匹配的结果';
  static const String emptyFavorites = '暂无收藏';

  // ====================== 占位符文本 ======================
  static const String placeholderTitle = '无标题';
  static const String placeholderContent = '暂无内容';
  static const String placeholderSummary = '暂无摘要';
  static const String placeholderUrl = 'https://example.com';
  static const String placeholderDate = '暂无日期';
  static const String placeholderAuthor = '未知作者';
}
