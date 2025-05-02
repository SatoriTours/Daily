import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';

/// 书籍模型
///
/// 是对ObjectBox实体的包装，处理默认值问题
class BookModel {
  final Book __entity;

  /// ID
  int get id => __entity.id;
  set id(int value) => __entity.id = value;

  /// 书名
  String get title => __entity.title;
  set title(String value) => __entity.title = value;

  /// 作者
  String get author => __entity.author;
  set author(String value) => __entity.author = value;

  /// 分类
  String get category => __entity.category;
  set category(String value) => __entity.category = value;

  /// 封面图片
  String get coverImage => __entity.coverImage;
  set coverImage(String value) => __entity.coverImage = value;

  /// 介绍信息
  String get introduction => __entity.introduction;
  set introduction(String value) => __entity.introduction = value;

  /// 是否有更新或未读
  bool get hasUpdate => __entity.hasUpdate;
  set hasUpdate(bool value) => __entity.hasUpdate = value;

  /// 创建日期
  DateTime get createAt => __entity.createAt;
  set createAt(DateTime value) => __entity.createAt = value;

  /// 更新日期
  DateTime get updateAt => __entity.updateAt;
  set updateAt(DateTime value) => __entity.updateAt = value;

  /// 构造函数，通过实体创建模型
  BookModel(this.__entity);

  /// 创建新模型，内部自动创建实体
  BookModel.create({
    int id = 0,
    required String title,
    required String author,
    required String category,
    String coverImage = '',
    String introduction = '',
    bool hasUpdate = false,
    DateTime? createAt,
    DateTime? updateAt,
  }) : __entity = Book(
         id: id,
         title: title,
         author: author,
         category: category,
         coverImage: coverImage,
         introduction: introduction,
         hasUpdate: hasUpdate,
         createAt: createAt,
         updateAt: updateAt,
       );

  /// 从ObjectBox实体创建模型
  factory BookModel.fromEntity(Book entity) {
    return BookModel(entity);
  }

  /// 获取底层实体
  Book toEntity() {
    return __entity;
  }

  /// 从JSON创建模型
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

  /// 转换为JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'author': author,
      'category': category,
      'coverImage': coverImage,
      'introduction': introduction,
      'hasUpdate': hasUpdate,
      'createAt': createAt.toIso8601String(),
      'updateAt': updateAt.toIso8601String(),
    };
  }
}

/// 书籍观点模型
///
/// 是对ObjectBox实体的包装，处理默认值问题
class BookViewpointModel {
  final BookViewpoint __entity;

  /// ID
  int get id => __entity.id;
  set id(int value) => __entity.id = value;

  /// 关联的书籍ID
  int get bookId => __entity.bookId;
  set bookId(int value) => __entity.bookId = value;

  /// 观点标题
  String get title => __entity.title;
  set title(String value) => __entity.title = value;

  /// 观点内容
  String get content => __entity.content;
  set content(String value) => __entity.content = value;

  /// 案例
  String get example => __entity.example;
  set example(String value) => __entity.example = value;

  /// 个人感悟
  String get feeling => __entity.feeling;
  set feeling(String value) => __entity.feeling = value;

  /// 创建日期
  DateTime get createAt => __entity.createAt;
  set createAt(DateTime value) => __entity.createAt = value;

  /// 更新日期
  DateTime get updateAt => __entity.updateAt;
  set updateAt(DateTime value) => __entity.updateAt = value;

  /// 关联的书籍
  BookModel? get book => BookRepository.getBookById(bookId);

  /// 构造函数，通过实体创建模型
  BookViewpointModel(this.__entity);

  /// 创建新模型，内部自动创建实体
  BookViewpointModel.create({
    int id = 0,
    required int bookId,
    required String title,
    required String content,
    String example = '',
    String feeling = '',
    DateTime? createAt,
    DateTime? updateAt,
  }) : __entity = BookViewpoint(
         id: id,
         bookId: bookId,
         title: title,
         content: content,
         example: example,
         feeling: feeling,
         createAt: createAt,
         updateAt: updateAt,
       );

  /// 从ObjectBox实体创建模型
  factory BookViewpointModel.fromEntity(BookViewpoint entity) {
    return BookViewpointModel(entity);
  }

  /// 获取底层实体
  BookViewpoint toEntity() {
    return __entity;
  }

  /// 从JSON创建模型
  factory BookViewpointModel.fromJson(Map<String, dynamic> json) {
    return BookViewpointModel.create(
      id: json['id'] as int? ?? 0,
      bookId: json['bookId'] as int,
      title: json['title'] as String,
      content: json['content'] as String,
      example: json['example'] as String? ?? '',
      feeling: json['feeling'] as String? ?? '',
      createAt: json['createAt'] != null ? DateTime.parse(json['createAt'] as String) : null,
      updateAt: json['updateAt'] != null ? DateTime.parse(json['updateAt'] as String) : null,
    );
  }

  /// 转换为JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'bookId': bookId,
      'title': title,
      'content': content,
      'example': example,
      'feeling': feeling,
      'createAt': createAt.toIso8601String(),
      'updateAt': updateAt.toIso8601String(),
    };
  }
}
