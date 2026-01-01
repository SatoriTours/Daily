import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/services/book_search_engine/book_search_engine.dart';
import 'package:dio/dio.dart';

/// Google Books 搜索引擎实现
///
/// 基于 Google Books API v1 的书籍搜索服务
class GoogleBooksSearchEngine extends BookSearchEngine {
  static final GoogleBooksSearchEngine _instance = GoogleBooksSearchEngine._();
  static GoogleBooksSearchEngine get i => _instance;

  GoogleBooksSearchEngine._();

  @override
  String get engineName => 'Google Books';

  final Dio _dio = Dio();

  @override
  Future<List<BookSearchResult>> searchBooks(String query, {int limit = 8}) async {
    try {
      logger.i('开始通过 $engineName 搜索书籍: $query');

      // 检查 API Key 是否配置
      final apiKey = SettingRepository.i.getSetting(SettingService.googleCloudApiKeyKey);
      if (apiKey.isEmpty) {
        logger.w('Google Books API Key 未配置，无法搜索');
        return [];
      }

      // 调用 Google Books API v1 REST endpoint
      final response = await _dio.get(
        'https://books.googleapis.com/books/v1/volumes',
        queryParameters: {
          'q': query,
          'key': apiKey,
          'maxResults': limit,
          'printType': 'books', // 只搜索书籍
          'langRestrict': 'zh', // 限制为中文
        },
      );

      if (response.statusCode != 200 || response.data == null) {
        logger.w('$engineName API 返回错误');
        return [];
      }

      final items = response.data['items'] as List<dynamic>?;
      if (items == null || items.isEmpty) {
        logger.w('$engineName 没有找到结果');
        return [];
      }

      logger.i('$engineName 返回 ${items.length} 个结果');

      final processedResults = _mapGoogleBooksResults(items);

      logger.i('$engineName 搜索完成，找到 ${processedResults.length} 个相关结果');
      return processedResults;
    } catch (e, stackTrace) {
      logger.e('$engineName 书籍搜索失败: $query', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  @override
  Future<bool> isAvailable() async {
    try {
      final apiKey = SettingRepository.i.getSetting(SettingService.googleCloudApiKeyKey);
      return apiKey.isNotEmpty;
    } catch (e) {
      logger.w('$engineName 不可用: $e');
      return false;
    }
  }

  /// 映射 Google Books 结果
  List<BookSearchResult> _mapGoogleBooksResults(List<dynamic> items) {
    final results = <BookSearchResult>[];

    for (final item in items) {
      try {
        final volumeInfo = item['volumeInfo'] as Map<String, dynamic>? ?? {};

        // 获取封面 URL（优先使用大图）
        String coverUrl = '';
        final imageLinks = volumeInfo['imageLinks'] as Map<String, dynamic>?;
        if (imageLinks != null) {
          coverUrl = imageLinks['thumbnail'] ?? '';
          // 替换为高分辨率图片
          if (coverUrl.isNotEmpty) {
            coverUrl = coverUrl.replaceAll('zoom=1', 'zoom=2');
          }
        }

        // 获取 ISBN
        String isbn = '';
        final industryIdentifiers = volumeInfo['industryIdentifiers'] as List<dynamic>?;
        if (industryIdentifiers != null && industryIdentifiers.isNotEmpty) {
          // 优先使用 ISBN_13
          final isbn13 = industryIdentifiers
              .where((id) => (id as Map<String, dynamic>)['type'] == 'ISBN_13')
              .map<String>((id) => (id as Map<String, dynamic>)['identifier'] as String)
              .firstOrNull;
          isbn = isbn13 ?? (industryIdentifiers.first as Map<String, dynamic>)['identifier'] as String? ?? '';
        }

        // 获取分类
        String category = '图书';
        final categories = volumeInfo['categories'] as List<dynamic>?;
        if (categories != null && categories.isNotEmpty) {
          category = categories.first as String;
        }

        results.add(
          BookSearchResult(
            title: volumeInfo['title'] ?? '',
            author: (volumeInfo['authors'] as List<dynamic>? ?? []).join(', '),
            category: category,
            introduction: volumeInfo['description'] ?? '',
            isbn: isbn,
            publishYear: volumeInfo['publishedDate'] ?? '',
            coverUrl: coverUrl,
          ),
        );
      } catch (e) {
        logger.w('解析 Google Books 结果失败', error: e);
        continue;
      }
    }

    return results;
  }
}
