enum SearchResultType { article, diary, book }

class SearchResult {
  final SearchResultType type;
  final int id;
  final String title;
  final String? summary;
  final DateTime? createdAt;
  final List<String>? tags;
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

  factory SearchResult.fromArticle({
    required int id,
    required String title,
    String? summary,
    DateTime? createdAt,
    List<String>? tags,
    bool? isFavorite,
  }) => SearchResult(
    type: SearchResultType.article,
    id: id,
    title: title,
    summary: summary,
    createdAt: createdAt,
    tags: tags,
    isFavorite: isFavorite,
  );

  factory SearchResult.fromDiary({
    required int id,
    required String title,
    String? summary,
    DateTime? createdAt,
    List<String>? tags,
  }) => SearchResult(
    type: SearchResultType.diary,
    id: id,
    title: title,
    summary: summary,
    createdAt: createdAt,
    tags: tags,
  );

  factory SearchResult.fromBook({
    required int id,
    required String title,
    String? summary,
    DateTime? createdAt,
  }) => SearchResult(
    type: SearchResultType.book,
    id: id,
    title: title,
    summary: summary,
    createdAt: createdAt,
  );

  String get typeIcon => switch (type) {
    SearchResultType.article => '📄',
    SearchResultType.diary => '📔',
    SearchResultType.book => '📖',
  };

  String get typeName => switch (type) {
    SearchResultType.article => '文章',
    SearchResultType.diary => '日记',
    SearchResultType.book => '书籍',
  };
}
