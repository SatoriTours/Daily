import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';

/// 书籍观点模型
class BookViewpointModel extends EntityModel<BookViewpoint> {
  BookViewpointModel(super.entity);

  factory BookViewpointModel.fromId(int id) {
    final viewpoint = BookRepository.instance.getViewpointById(id);
    if (viewpoint == null) {
      throw Exception('找不到 ID 为 $id 的观点');
    }
    return BookViewpointModel(viewpoint);
  }

  factory BookViewpointModel.create({
    int id = 0,
    required int bookId,
    required String title,
    required String content,
    String example = '',
    DateTime? createAt,
    DateTime? updateAt,
  }) {
    return BookViewpointModel(
      BookViewpoint(
        id: id,
        bookId: bookId,
        title: title,
        content: content,
        example: example,
        createAt: createAt,
        updateAt: updateAt,
      ),
    );
  }

  factory BookViewpointModel.fromJson(Map<String, dynamic> json) {
    return BookViewpointModel.create(
      id: json['id'] as int? ?? 0,
      bookId: json['bookId'] as int,
      title: json['title'] as String,
      content: json['content'] as String,
      example: json['example'] as String? ?? '',
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

  int get bookId => entity.bookId;
  set bookId(int value) => entity.bookId = value;

  String get title => entity.title;
  set title(String value) => entity.title = value;

  String get content => entity.content;
  set content(String value) => entity.content = value;

  String get example => entity.example;
  set example(String value) => entity.example = value;

  // ==================== 便捷属性(兼容旧代码) ====================

  DateTime get createAt => entity.createAt;
  set createAt(DateTime value) => entity.createAt = value;

  DateTime get updateAt => entity.updateAt;
  set updateAt(DateTime value) => entity.updateAt = value;

  // ==================== 关联属性 ====================

  /// 关联的书籍
  BookModel? get book {
    final bookEntity = BookRepository.instance.getBookById(bookId);
    return bookEntity != null ? BookModel(bookEntity) : null;
  }

  // ==================== 转换方法 ====================

  BookViewpoint toEntity() => entity;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'bookId': bookId,
      'title': title,
      'content': content,
      'example': example,
      'createAt': createdAt?.toIso8601String(),
      'updateAt': updatedAt?.toIso8601String(),
    };
  }
}
