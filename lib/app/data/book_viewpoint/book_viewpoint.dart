import 'package:daily_satori/app/data/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';

/// 书籍观点模型
class BookViewpointModel extends EntityModel<BookViewpoint> {
  BookViewpointModel(super.entity);

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

  // ==================== 基本属性 ====================

  int get bookId => entity.bookId;
  set bookId(int value) => entity.bookId = value;

  String get title => entity.title;
  set title(String value) => entity.title = value;

  String get content => entity.content;
  set content(String value) => entity.content = value;

  String get example => entity.example;
  set example(String value) => entity.example = value;

  // ==================== 转换方法 ====================

  BookViewpoint toEntity() => entity;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'bookId': bookId,
      'title': title,
      'content': content,
      'example': example,
      'createAt': createdAt.toIso8601String(),
      'updateAt': updatedAt.toIso8601String(),
    };
  }
}
