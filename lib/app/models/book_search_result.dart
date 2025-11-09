/// 书籍搜索结果模型
///
/// 纯内存数据结构，用于临时存储AI搜索结果
/// 用户选择后直接转换为BookModel进行保存
class BookSearchResult {
  final String title;
  final String author;
  final String category;
  final String introduction;
  final String isbn;
  final String publishYear;
  late final DateTime createdAt;

  BookSearchResult({
    required this.title,
    required this.author,
    required this.category,
    required this.introduction,
    this.isbn = '',
    this.publishYear = '',
  }) {
    createdAt = DateTime.now();
  }

  /// 带可选参数的构造函数
  BookSearchResult.withCreatedAt({
    required this.title,
    required this.author,
    required this.category,
    required this.introduction,
    this.isbn = '',
    this.publishYear = '',
    DateTime? createdAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
  }

  /// 从JSON创建BookSearchResult
  factory BookSearchResult.fromJson(Map<String, dynamic> json) {
    return BookSearchResult.withCreatedAt(
      title: json['title'] as String,
      author: json['author'] as String,
      category: json['category'] as String,
      introduction: json['introduction'] as String,
      isbn: json['isbn'] as String? ?? '',
      publishYear: json['publishYear'] as String? ?? '',
    );
  }

  /// 转换为JSON
  Map<String, dynamic> toJson() {
    return {
      'title': title,
      'author': author,
      'category': category,
      'introduction': introduction,
      'isbn': isbn,
      'publishYear': publishYear,
    };
  }

  @override
  String toString() {
    return 'BookSearchResult{title: $title, author: $author, category: $category}';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is BookSearchResult &&
        other.title == title &&
        other.author == author &&
        other.isbn == isbn;
  }

  @override
  int get hashCode => Object.hash(title, author, isbn);
}