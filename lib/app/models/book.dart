import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';

/// 书籍模型
class BookModel extends EntityModel<Book> {
  BookModel(super.entity);

  factory BookModel.fromId(int id) {
    final book = BookRepository.instance.getBookById(id);
    if (book == null) {
      throw Exception('找不到 ID 为 $id 的书籍');
    }
    return BookModel(book);
  }

  factory BookModel.create({
    int id = 0,
    required String title,
    required String author,
    required String category,
    String coverImage = '',
    String introduction = '',
    bool hasUpdate = false,
    DateTime? createAt,
    DateTime? updateAt,
  }) {
    return BookModel(
      Book(
        id: id,
        title: title,
        author: author,
        category: category,
        coverImage: coverImage,
        introduction: introduction,
        hasUpdate: hasUpdate,
        createAt: createAt,
        updateAt: updateAt,
      ),
    );
  }

  factory BookModel.fromJson(Map<String, dynamic> json) {
    return BookModel.create(
      id: json['id'] as int? ?? 0,
      title: json['title'] as String,
      author: json['author'] as String,
      category: json['category'] as String,
      coverImage: json['coverImage'] as String? ?? '',
      introduction: json['introduction'] as String? ?? '',
      hasUpdate: json['hasUpdate'] as bool? ?? false,
      createAt: json['createAt'] != null ? DateTime.parse(json['createAt'] as String) : null,
      updateAt: json['updateAt'] != null ? DateTime.parse(json['updateAt'] as String) : null,
    );
  }

  @override
  int get id => entity.id;
  set id(int value) => entity.id = value;

  @override
  DateTime? get createdAt => entity.createAt;
  @override
  set createdAt(DateTime? value) => entity.createAt = value ?? DateTime.now();

  @override
  DateTime? get updatedAt => entity.updateAt;
  @override
  set updatedAt(DateTime? value) => entity.updateAt = value ?? DateTime.now();

  // ==================== 基本属性 ====================

  String get title => entity.title;
  set title(String value) => entity.title = value;

  String get author => entity.author;
  set author(String value) => entity.author = value;

  String get category => entity.category;
  set category(String value) => entity.category = value;

  String get coverImage => entity.coverImage;
  set coverImage(String value) => entity.coverImage = value;

  String get introduction => entity.introduction;
  set introduction(String value) => entity.introduction = value;

  bool get hasUpdate => entity.hasUpdate;
  set hasUpdate(bool value) => entity.hasUpdate = value;

  // ==================== 便捷属性(兼容旧代码) ====================

  DateTime get createAt => entity.createAt;
  set createAt(DateTime value) => entity.createAt = value;

  DateTime get updateAt => entity.updateAt;
  set updateAt(DateTime value) => entity.updateAt = value;

  // ==================== 转换方法 ====================

  factory BookModel.fromEntity(Book entity) => BookModel(entity);

  Book toEntity() => entity;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'author': author,
      'category': category,
      'coverImage': coverImage,
      'introduction': introduction,
      'hasUpdate': hasUpdate,
      'createAt': createdAt?.toIso8601String(),
      'updateAt': updatedAt?.toIso8601String(),
    };
  }
}
