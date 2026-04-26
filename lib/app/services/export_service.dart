import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart' as arch;
import 'package:flutter/foundation.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/objectbox/objectbox.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

class ExportService {
  ExportService._();
  static final ExportService i = ExportService._();

  final ValueNotifier<double> exportProgress = ValueNotifier(0.0);

  Future<String?> exportToZip(String outputPath) async {
    try {
      exportProgress.value = 0.0;

      final archive = arch.Archive();

      final articles = ArticleRepository.i.all();
      exportProgress.value = 0.05;

      final diaries = DiaryRepository.i.all();
      final books = BookRepository.i.all();
      final viewpoints = BookViewpointRepository.i.all();
      final tags = TagRepository.i.all();
      final images = ImageRepository.i.all();
      final screenshots = ScreenshotRepository.i.all();
      final settings = SettingRepository.i.all();
      final aiConfigs = AIConfigRepository.i.all();
      final weeklySummaries = WeeklySummaryRepository.i.all();
      final sessions = SessionRepository.i.all();
      exportProgress.value = 0.1;

      _addJsonData(archive, 'articles.json', _serializeArticles(articles));
      _addJsonData(archive, 'diaries.json', _serializeDiaries(diaries));
      _addJsonData(archive, 'books.json', _serializeBooks(books));
      _addJsonData(archive, 'book_viewpoints.json', _serializeViewpoints(viewpoints));
      _addJsonData(archive, 'tags.json', _serializeTags(tags));
      _addJsonData(archive, 'images.json', _serializeImages(images));
      _addJsonData(archive, 'screenshots.json', _serializeScreenshots(screenshots));
      _addJsonData(archive, 'settings.json', _serializeSettings(settings));
      _addJsonData(archive, 'ai_configs.json', _serializeAiConfigs(aiConfigs));
      _addJsonData(archive, 'weekly_summaries.json', _serializeWeeklySummaries(weeklySummaries));
      _addJsonData(archive, 'sessions.json', _serializeSessions(sessions));
      _addJsonData(archive, 'article_tags.json', _serializeArticleTags(articles));
      exportProgress.value = 0.3;

      await _collectAndAddImages(archive, articles, diaries, images, screenshots);
      exportProgress.value = 0.9;

      final manifest = await _buildManifest(
        articles: articles.length,
        diaries: diaries.length,
        books: books.length,
        viewpoints: viewpoints.length,
        tags: tags.length,
        images: images.length,
        screenshots: screenshots.length,
        settings: settings.length,
        aiConfigs: aiConfigs.length,
        weeklySummaries: weeklySummaries.length,
        sessions: sessions.length,
      );
      _addJsonFile(archive, 'manifest.json', manifest);
      exportProgress.value = 0.95;

      final zipData = arch.ZipEncoder().encode(archive);
      if (zipData == null) {
        logger.e('[ExportService] ZIP 编码失败');
        return null;
      }

      await File(outputPath).writeAsBytes(zipData);
      exportProgress.value = 1.0;
      logger.i('[ExportService] 导出完成: $outputPath');
      return outputPath;
    } catch (e, stack) {
      logger.e('[ExportService] 导出失败', error: e, stackTrace: stack);
      return null;
    }
  }

  void _addJsonFile(arch.Archive archive, String name, String jsonStr) {
    archive.addFile(arch.File.fromBytes(name, utf8.encode(jsonStr)));
  }

  void _addJsonData(arch.Archive archive, String name, List<Map<String, dynamic>> data) {
    _addJsonFile(archive, name, JsonEncoder.withIndent('  ').convert(data));
  }

  List<Map<String, dynamic>> _serializeArticles(List<ArticleModel> articles) {
    return articles
        .map((a) => {
              'id': a.entity.id,
              'title': a.entity.title,
              'ai_title': a.entity.aiTitle,
              'content': a.entity.content,
              'ai_content': a.entity.aiContent,
              'html_content': a.entity.htmlContent,
              'ai_markdown_content': a.entity.aiMarkdownContent,
              'url': a.entity.url,
              'is_favorite': a.entity.isFavorite,
              'comment': a.entity.comment,
              'status': a.entity.status,
              'cover_image': a.entity.coverImage,
              'cover_image_url': a.entity.coverImageUrl,
              'pub_date': a.entity.pubDate?.toIso8601String(),
              'created_at': a.entity.createdAt.toIso8601String(),
              'updated_at': a.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeDiaries(List<DiaryModel> diaries) {
    return diaries
        .map((d) => {
              'id': d.entity.id,
              'content': d.entity.content,
              'tags': d.entity.tags,
              'mood': d.entity.mood,
              'images': d.entity.images,
              'created_at': d.entity.createdAt.toIso8601String(),
              'updated_at': d.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeBooks(List<BookModel> books) {
    return books
        .map((b) => {
              'id': b.entity.id,
              'title': b.entity.title,
              'author': b.entity.author,
              'category': b.entity.category,
              'cover_image': b.entity.coverImage,
              'introduction': b.entity.introduction,
              'has_update': b.entity.hasUpdate,
              'created_at': b.entity.createdAt.toIso8601String(),
              'updated_at': b.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeViewpoints(List<BookViewpointModel> viewpoints) {
    return viewpoints
        .map((v) => {
              'id': v.entity.id,
              'book_id': v.entity.bookId,
              'title': v.entity.title,
              'content': v.entity.content,
              'example': v.entity.example,
              'created_at': v.entity.createdAt.toIso8601String(),
              'updated_at': v.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeTags(List<TagModel> tags) {
    return tags
        .map((t) => {
              'id': t.entity.id,
              'name': t.entity.name,
              'icon': t.entity.icon,
              'created_at': t.entity.createdAt.toIso8601String(),
              'updated_at': t.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeImages(List<ImageModel> images) {
    return images
        .map((i) => {
              'id': i.entity.id,
              'url': i.entity.url,
              'path': i.entity.path,
              'article_id': i.entity.article.targetId,
              'created_at': i.entity.createdAt.toIso8601String(),
              'updated_at': i.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeScreenshots(List<ScreenshotModel> screenshots) {
    return screenshots
        .map((s) => {
              'id': s.entity.id,
              'path': s.entity.path,
              'article_id': s.entity.article.targetId,
              'created_at': s.entity.createdAt.toIso8601String(),
              'updated_at': s.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeSettings(List<SettingModel> settings) {
    return settings
        .map((s) => {
              'id': s.entity.id,
              'key': s.entity.key,
              'value': s.entity.value,
              'created_at': s.entity.createdAt.toIso8601String(),
              'updated_at': s.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeAiConfigs(List<AIConfigModel> configs) {
    return configs
        .map((c) => {
              'id': c.entity.id,
              'name': c.entity.name,
              'api_address': c.entity.apiAddress,
              'api_token': c.entity.apiToken,
              'model_name': c.entity.modelName,
              'function_type': c.entity.functionType,
              'inherit_from_general': c.entity.inheritFromGeneral,
              'is_default': c.entity.isDefault,
              'created_at': c.entity.createdAt.toIso8601String(),
              'updated_at': c.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeWeeklySummaries(List<WeeklySummaryModel> summaries) {
    return summaries
        .map((s) => {
              'id': s.entity.id,
              'week_start_date': s.entity.weekStartDate.toIso8601String(),
              'week_end_date': s.entity.weekEndDate.toIso8601String(),
              'content': s.entity.content,
              'article_count': s.entity.articleCount,
              'diary_count': s.entity.diaryCount,
              'viewpoint_count': s.entity.viewpointCount,
              'article_ids': s.entity.articleIds,
              'diary_ids': s.entity.diaryIds,
              'viewpoint_ids': s.entity.viewpointIds,
              'app_ideas': s.entity.appIdeas,
              'status': s.entity.status,
              'created_at': s.entity.createdAt.toIso8601String(),
              'updated_at': s.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeSessions(List<SessionModel> sessions) {
    return sessions
        .map((s) => {
              'id': s.entity.id,
              'session_id': s.entity.sessionId,
              'is_authenticated': s.entity.isAuthenticated,
              'username': s.entity.username,
              'last_accessed_at': s.entity.lastAccessedAt.toIso8601String(),
              'created_at': s.entity.createdAt.toIso8601String(),
              'updated_at': s.entity.updatedAt.toIso8601String(),
            })
        .toList();
  }

  List<Map<String, dynamic>> _serializeArticleTags(List<ArticleModel> articles) {
    final result = <Map<String, dynamic>>[];
    for (final a in articles) {
      for (final tag in a.entity.tags) {
        result.add({'article_id': a.entity.id, 'tag_id': tag.id});
      }
    }
    return result;
  }

  Future<void> _collectAndAddImages(
    arch.Archive archive,
    List<ArticleModel> articles,
    List<DiaryModel> diaries,
    List<ImageModel> images,
    List<ScreenshotModel> screenshots,
  ) async {
    final collected = <String>{};

    for (final a in articles) {
      if (a.entity.coverImage != null && a.entity.coverImage!.isNotEmpty) {
        collected.add(a.entity.coverImage!);
      }
    }

    for (final d in diaries) {
      final imgPaths = d.entity.images?.split(',').where((e) => e.trim().isNotEmpty) ?? [];
      for (final p in imgPaths) {
        collected.add(p.trim());
      }
    }

    for (final img in images) {
      if (img.entity.path != null && img.entity.path!.isNotEmpty) {
        collected.add(img.entity.path!);
      }
    }

    for (final s in screenshots) {
      if (s.entity.path != null && s.entity.path!.isNotEmpty) {
        collected.add(s.entity.path!);
      }
    }

    final step = collected.length > 0 ? 0.6 / collected.length : 0.0;
    var imageIdx = 0;

    for (final relPath in collected) {
      final absPath = FileService.i.toAbsolutePath(relPath);
      final file = File(absPath);
      if (await file.exists()) {
        final bytes = await file.readAsBytes();
        final relativePath = FileService.i.toRelativePath(relPath);
        archive.addFile(arch.File.fromBytes(relativePath, bytes));
      } else {
        logger.w('[ExportService] 图片文件不存在: $absPath');
      }
      imageIdx++;
      exportProgress.value = 0.3 + step * imageIdx;
    }
  }

  Future<String> _buildManifest({
    required int articles,
    required int diaries,
    required int books,
    required int viewpoints,
    required int tags,
    required int images,
    required int screenshots,
    required int settings,
    required int aiConfigs,
    required int weeklySummaries,
    required int sessions,
  }) async {
    final packageInfo = await PackageInfo.fromPlatform();
    final manifest = {
      'version': 1,
      'app_version': packageInfo.version,
      'export_time': DateTime.now().toUtc().toIso8601String(),
      'platform': 'flutter',
      'counts': {
        'articles': articles,
        'diaries': diaries,
        'books': books,
        'book_viewpoints': viewpoints,
        'tags': tags,
        'images': images,
        'screenshots': screenshots,
        'settings': settings,
        'ai_configs': aiConfigs,
        'weekly_summaries': weeklySummaries,
        'sessions': sessions,
      },
    };
    return JsonEncoder.withIndent('  ').convert(manifest);
  }
}
