import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/services/book_search_engine/book_search_engine.dart';
import 'package:daily_satori/app/services/book_search_engine/google_books_search_engine.dart';
import 'package:dio/dio.dart';
import 'package:html/parser.dart' as html_parser;

/// ISBNdb 搜索引擎实现
///
/// 爬取 isbndb.com 的搜索结果
/// 如果没有结果，回退到 Google Books
class IsbndbSearchEngine extends BookSearchEngine {
  static final IsbndbSearchEngine _instance = IsbndbSearchEngine._();
  static IsbndbSearchEngine get i => _instance;

  IsbndbSearchEngine._();

  @override
  String get engineName => 'ISBN Search';

  final Dio _dio = Dio();

  @override
  Future<List<BookSearchResult>> searchBooks(String query, {int limit = 8}) async {
    try {
      logger.i('开始通过 $engineName 搜索书籍: $query');

      // 并行搜索第一页和第二页
      final results = await Future.wait([_searchPage(query, 1), _searchPage(query, 2)]);

      // 合并结果
      final allResults = results.expand((element) => element).toList();

      if (allResults.isEmpty) {
        logger.w('$engineName 没有找到结果，尝试回退到 Google Books');
        return await _fallbackToGoogle(query, limit);
      }

      logger.i('$engineName 搜索完成，找到 ${allResults.length} 个结果');

      // 简单的去重（根据ISBN或标题+作者）
      final uniqueResults = <String, BookSearchResult>{};
      for (var book in allResults) {
        final key = book.isbn.isNotEmpty ? book.isbn : '${book.title}-${book.author}';
        uniqueResults.putIfAbsent(key, () => book);
      }

      return uniqueResults.values.take(limit).toList();
    } catch (e, stackTrace) {
      logger.e('$engineName 搜索失败: $query', error: e, stackTrace: stackTrace);
      return await _fallbackToGoogle(query, limit);
    }
  }

  Future<List<BookSearchResult>> _searchPage(String query, int page) async {
    try {
      final encodedQuery = Uri.encodeQueryComponent(query);
      final url = 'https://isbnsearch.org/search?s=$encodedQuery&p=$page';

      final response = await _dio.get(
        url,
        options: Options(
          headers: {
            'User-Agent':
                'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36',
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
            'Accept-Encoding': 'gzip, deflate, br, zstd',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'none',
            'Sec-Fetch-User': '?1',
            'Cache-Control': 'max-age=0',
          },
        ),
      );

      if (response.statusCode != 200) {
        return [];
      }

      final document = html_parser.parse(response.data);
      final listItems = document.querySelectorAll('#searchresults li');

      final results = <BookSearchResult>[];

      for (final li in listItems) {
        try {
          final bookInfoDiv = li.querySelector('.bookinfo');
          if (bookInfoDiv == null) continue;

          // 提取标题
          final titleElement = bookInfoDiv.querySelector('h2 > a');
          final title = titleElement?.text.trim() ?? '';
          if (title.isEmpty) continue;

          // 提取封面
          final imageDiv = li.querySelector('.image');
          final imgElement = imageDiv?.querySelector('img');
          final coverUrl = imgElement?.attributes['src'] ?? '';

          // 提取作者和ISBN
          String author = '';
          String isbn = '';

          final paragraphs = bookInfoDiv.querySelectorAll('p');
          for (final p in paragraphs) {
            final text = p.text.trim();
            if (text.startsWith('Author:')) {
              author = text.substring(7).trim();
            } else if (text.startsWith('ISBN-13:')) {
              isbn = text.substring(8).trim();
            }
          }

          results.add(
            BookSearchResult(
              title: title,
              author: author,
              category: '图书',
              introduction: '',
              isbn: isbn,
              publishYear: '', // 网站结果中未直接提供年份
              coverUrl: coverUrl,
            ),
          );
        } catch (e) {
          continue;
        }
      }
      return results;
    } catch (e) {
      logger.w('获取 ISBN Search 第 $page 页失败', error: e);
      return [];
    }
  }

  Future<List<BookSearchResult>> _fallbackToGoogle(String query, int limit) async {
    logger.i('回退到 Google Books 搜索');
    return await GoogleBooksSearchEngine.i.searchBooks(query, limit: limit);
  }
}
