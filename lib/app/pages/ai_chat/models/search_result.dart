/// 搜索结果类型
enum SearchResultType {
  article, // 文章
  diary, // 日记
  book, // 书籍
}

/// AI搜索结果模型
class SearchResult {
  /// 结果类型
  final SearchResultType type;

  /// 结果ID（文章/日记/书籍的数据库ID）
  final int id;

  /// 标题
  final String title;

  /// 摘要/内容预览
  final String? summary;

  /// 创建时间
  final DateTime? createdAt;

  /// 标签列表
  final List<String>? tags;

  /// 是否收藏
  final bool? isFavorite;

  const SearchResult({
    required this.type,
    required this.id,
    required this.title,
    this.summary,
    this.createdAt,
    this.tags,
    this.isFavorite,
  });

  /// 从文章创建搜索结果
  factory SearchResult.fromArticle({
    required int id,
    required String title,
    String? summary,
    DateTime? createdAt,
    List<String>? tags,
    bool? isFavorite,
  }) {
    return SearchResult(
      type: SearchResultType.article,
      id: id,
      title: title,
      summary: summary,
      createdAt: createdAt,
      tags: tags,
      isFavorite: isFavorite,
    );
  }

  /// 从日记创建搜索结果
  factory SearchResult.fromDiary({
    required int id,
    required String title,
    String? summary,
    DateTime? createdAt,
    List<String>? tags,
  }) {
    return SearchResult(
      type: SearchResultType.diary,
      id: id,
      title: title,
      summary: summary,
      createdAt: createdAt,
      tags: tags,
    );
  }

  /// 从书籍创建搜索结果
  factory SearchResult.fromBook({required int id, required String title, String? summary, DateTime? createdAt}) {
    return SearchResult(type: SearchResultType.book, id: id, title: title, summary: summary, createdAt: createdAt);
  }

  /// 获取类型图标
  String get typeIcon {
    switch (type) {
      case SearchResultType.article:
        return '📄';
      case SearchResultType.diary:
        return '📔';
      case SearchResultType.book:
        return '📖';
    }
  }

  /// 获取类型名称
  String get typeName {
    switch (type) {
      case SearchResultType.article:
        return '文章';
      case SearchResultType.diary:
        return '日记';
      case SearchResultType.book:
        return '书籍';
    }
  }
}
