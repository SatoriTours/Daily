import 'package:daily_satori/app/data/book/book_search_result.dart';

/// 书籍搜索引擎抽象基类
///
/// 定义书籍搜索引擎需要实现的接口
/// 支持多种搜索引擎实现（如 ISBNSearch、Google Books 等）
abstract class BookSearchEngine {
  /// 搜索引擎名称
  String get engineName;

  /// 搜索书籍
  ///
  /// [query] 搜索关键词（支持中文）
  /// [limit] 返回结果数量，默认8条
  /// 返回书籍搜索结果列表
  Future<List<BookSearchResult>> searchBooks(String query, {int limit = 8});

  /// 检查搜索引擎是否可用
  ///
  /// 返回 true 表示可用，false 表示不可用
  Future<bool> isAvailable() async {
    return true;
  }
}
