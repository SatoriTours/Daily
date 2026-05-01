import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/services/book_search_engine/book_search_engine.dart';
import 'package:dio/dio.dart';
import 'package:pinyin/pinyin.dart';
import 'dart:convert';

/// OpenLibrary 搜索引擎实现
///
/// 基于 OpenLibrary API 的书籍搜索服务（支持中文，通过拼音转换）
class OpenLibrarySearchEngine extends BookSearchEngine {
  static final OpenLibrarySearchEngine _instance = OpenLibrarySearchEngine._();
  static OpenLibrarySearchEngine get i => _instance;

  OpenLibrarySearchEngine._();

  @override
  String get engineName => 'OpenLibrary';

  final AiService _aiService = AiService.i;

  final Dio _dio = Dio(
    BaseOptions(
      baseUrl: 'https://openlibrary.org',
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 15),
      headers: {
        'User-Agent':
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
      },
    ),
  );

  @override
  Future<List<BookSearchResult>> searchBooks(
    String query, {
    int limit = 8,
  }) async {
    try {
      logger.i('开始通过 $engineName 搜索书籍: $query');

      // 保存原始中文查询，用于后续相关性过滤
      final originalChineseQuery = query;

      // 1. 如果是中文，转换为拼音进行搜索
      String searchTerm = query;
      if (_containsChinese(query)) {
        searchTerm = PinyinHelper.getPinyinE(
          query,
          separator: ' ',
          format: PinyinFormat.WITHOUT_TONE,
        );
        logger.i('中文查询转换为拼音: $searchTerm');
      }

      // 2. 调用 OpenLibrary API 搜索
      final openLibraryResults = await _searchOpenLibrary(
        searchTerm,
        limit: limit * 2,
      ); // 多获取一些结果供AI筛选

      if (openLibraryResults.isEmpty) {
        logger.w('OpenLibrary 没有找到结果');
        return [];
      }

      logger.i('OpenLibrary 返回 ${openLibraryResults.length} 个结果');

      // 3. 使用 AI 处理结果：翻译拼音为中文，过滤不相关的结果
      final processedResults = await _processResultsWithAI(
        openLibraryResults,
        originalChineseQuery,
        limit,
      );

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
      // AI 服务默认可用
      return true;
    } catch (e) {
      logger.w('$engineName 不可用: $e');
      return false;
    }
  }

  /// 检查字符串是否包含中文
  bool _containsChinese(String text) {
    return RegExp(r'[\u4e00-\u9fa5]').hasMatch(text);
  }

  /// 调用 OpenLibrary API 搜索书籍
  Future<List<Map<String, dynamic>>> _searchOpenLibrary(
    String query, {
    int limit = 16,
  }) async {
    try {
      // 使用 OpenLibrary Search API
      // 文档: https://openlibrary.org/dev/docs/api/search
      final response = await _dio.get(
        '/search.json',
        queryParameters: {
          'q': query,
          'limit': limit,
          'fields':
              'key,title,author_name,first_publish_year,isbn,cover_i,publisher,subject',
          'lang': 'en',
        },
      );

      if (response.statusCode != 200) {
        logger.e('OpenLibrary API 返回错误状态码: ${response.statusCode}');
        return [];
      }

      final data = response.data as Map<String, dynamic>;
      final docs = data['docs'] as List<dynamic>? ?? [];

      return docs.map((doc) => doc as Map<String, dynamic>).toList();
    } catch (e, stackTrace) {
      logger.e('调用 OpenLibrary API 失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 使用 AI 处理搜索结果：翻译拼音为中文，过滤不相关的结果
  Future<List<BookSearchResult>> _processResultsWithAI(
    List<Map<String, dynamic>> openLibraryResults,
    String originalChineseQuery,
    int limit,
  ) async {
    try {
      // 构建书籍列表的简要信息
      final booksInfo = openLibraryResults.asMap().entries.map((entry) {
        final index = entry.key;
        final doc = entry.value;
        return {
          'index': index,
          'title': doc['title'] ?? '',
          'author': (doc['author_name'] as List<dynamic>?)?.join(', ') ?? '',
          'year': doc['first_publish_year']?.toString() ?? '',
        };
      }).toList();

      final prompt =
          '''
我通过拼音搜索 OpenLibrary 获得了以下书籍列表，原始搜索词是"$originalChineseQuery"。

书籍列表（JSON格式）:
${jsonEncode(booksInfo)}

请完成以下任务：
1. 将书名和作者名中的拼音翻译成中文（如果有的话）
2. **严格过滤与"$originalChineseQuery"不相关的书籍**：
   - 注意拼音的歧义性：例如"shi jian"可能是"实践"也可能是"时间"
   - 只保留与"$originalChineseQuery"主题真正相关的书籍
   - 如果原始查询是"实践"，则"时间"相关的书完全不相关，应该被过滤掉
3. 选择最相关的最多$limit本书籍
4. 为每本书添加50-100字的中文简介，突出核心价值

返回JSON格式（只返回JSON，不要其他文字）：
{
  "results": [
    {
      "index": 原始列表中的索引,
      "title": "翻译后的中文书名",
      "author": "翻译后的中文作者名",
      "category": "书籍分类",
      "introduction": "中文简介（50-100字）",
      "relevance_score": 与原始查询的相关度分数(0-10)
    }
  ]
}

要求：
- 只返回相关度分数 >= 7 的书籍
- 按相关度分数从高到低排序
- 如果找不到$limit本相关书籍，宁可少返回也不要凑数
''';

      final response = await _aiService.complete(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      final Map<String, dynamic> aiData = jsonDecode(cleanedResponse);

      if (!aiData.containsKey('results')) {
        logger.w('AI 返回的数据格式不正确');
        return [];
      }

      final aiResults = aiData['results'] as List<dynamic>;
      final processedBooks = <BookSearchResult>[];

      for (final aiResult in aiResults) {
        final index = aiResult['index'] as int;
        if (index < 0 || index >= openLibraryResults.length) continue;

        final originalDoc = openLibraryResults[index];

        // 获取封面 URL
        String coverUrl = '';
        if (originalDoc['cover_i'] != null) {
          final coverId = originalDoc['cover_i'];
          coverUrl = 'https://covers.openlibrary.org/b/id/$coverId-L.jpg';
        }

        // 获取 ISBN
        String isbn = '';
        if (originalDoc['isbn'] != null &&
            (originalDoc['isbn'] as List).isNotEmpty) {
          isbn = (originalDoc['isbn'] as List).first.toString();
        }

        processedBooks.add(
          BookSearchResult(
            title: aiResult['title'] ?? originalDoc['title'] ?? '',
            author: aiResult['author'] ?? '',
            category: aiResult['category'] ?? '图书',
            introduction: aiResult['introduction'] ?? '',
            isbn: isbn,
            publishYear: originalDoc['first_publish_year']?.toString() ?? '',
            coverUrl: coverUrl,
          ),
        );
      }

      return processedBooks;
    } catch (e, stackTrace) {
      logger.e('AI 处理搜索结果失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 清理JSON响应
  String _cleanJsonResponse(String response) {
    String cleaned = response.trim();

    // 移除可能的markdown代码块标记
    if (cleaned.startsWith('```json')) {
      cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith('```')) {
      cleaned = cleaned.substring(3);
    }

    if (cleaned.endsWith('```')) {
      cleaned = cleaned.substring(0, cleaned.length - 3);
    }

    return cleaned.trim();
  }
}
