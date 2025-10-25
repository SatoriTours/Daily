import 'package:daily_satori/app/models/mixins/entity_model_mixin.dart';
import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';

/// 书籍模型
///
/// 是对 ObjectBox 实体的包装，提供统一的数据访问接口
class BookModel with EntityModelMixin<Book> {
  // ==================== 私有字段 ====================

  final Book _entity;

  // ==================== 构造函数 ====================

  /// 构造函数
  BookModel(this._entity);

  /// 从 ID 创建实例
  factory BookModel.fromId(int id) {
    final book = BookRepository.instance.getBookById(id);
    if (book == null) {
      throw Exception('找不到 ID 为 $id 的书籍');
    }
    return BookModel(book);
  }

  /// 创建新实例
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

  /// 从 JSON 创建实例
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

  // ==================== 实现 Mixin 要求的属性 ====================

  /// 底层实体对象
  @override
  Book get entity => _entity;

  /// ID
  @override
  int get id => entity.id;
  set id(int value) => entity.id = value;

  /// 创建日期
  @override
  DateTime? get createdAt => entity.createAt;
  @override
  set createdAt(DateTime? value) => entity.createAt = value ?? DateTime.now();

  /// 更新日期
  @override
  DateTime? get updatedAt => entity.updateAt;
  @override
  set updatedAt(DateTime? value) => entity.updateAt = value ?? DateTime.now();

  // ==================== 基本属性 ====================

  /// 书名
  String get title => entity.title;
  set title(String value) => entity.title = value;

  /// 作者
  String get author => entity.author;
  set author(String value) => entity.author = value;

  /// 分类
  String get category => entity.category;
  set category(String value) => entity.category = value;

  /// 封面图片
  String get coverImage => entity.coverImage;
  set coverImage(String value) => entity.coverImage = value;

  /// 介绍信息
  String get introduction => entity.introduction;
  set introduction(String value) => entity.introduction = value;

  /// 是否有更新或未读
  bool get hasUpdate => entity.hasUpdate;
  set hasUpdate(bool value) => entity.hasUpdate = value;

  // ==================== 便捷属性(兼容旧代码) ====================

  /// 创建日期（兼容旧代码）
  DateTime get createAt => entity.createAt;
  set createAt(DateTime value) => entity.createAt = value;

  /// 更新日期（兼容旧代码）
  DateTime get updateAt => entity.updateAt;
  set updateAt(DateTime value) => entity.updateAt = value;

  // ==================== 转换方法 ====================

  /// 从实体创建模型（兼容旧代码）
  factory BookModel.fromEntity(Book entity) => BookModel(entity);

  /// 获取底层实体（兼容旧代码）
  Book toEntity() => entity;

  /// 转换为 JSON
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

/// 书籍观点模型
///
/// 是对 ObjectBox 实体的包装，提供统一的数据访问接口
class BookViewpointModel {
  // ==================== 私有字段 ====================

  final BookViewpoint _entity;

  // ==================== 构造函数 ====================

  /// 构造函数
  BookViewpointModel(this._entity);

  /// 从 ID 创建实例
  factory BookViewpointModel.fromId(int id) {
    final viewpoint = BookRepository.instance.getViewpointById(id);
    if (viewpoint == null) {
      throw Exception('找不到 ID 为 $id 的观点');
    }
    return BookViewpointModel(viewpoint);
  }

  /// 创建新实例
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

  /// 从 JSON 创建实例
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

  /// 底层实体对象
  BookViewpoint get entity => _entity;

  /// ID
  int get id => entity.id;
  set id(int value) => entity.id = value;

  /// 关联的书籍 ID
  int get bookId => entity.bookId;
  set bookId(int value) => entity.bookId = value;

  /// 观点标题
  String get title => entity.title;
  set title(String value) => entity.title = value;

  /// 观点内容
  String get content => entity.content;
  set content(String value) => entity.content = value;

  /// 案例
  String get example => entity.example;
  set example(String value) => entity.example = value;

  /// 创建日期
  DateTime get createdAt => entity.createAt;
  set createdAt(DateTime value) => entity.createAt = value;

  /// 更新日期
  DateTime get updatedAt => entity.updateAt;
  set updatedAt(DateTime value) => entity.updateAt = value;

  // ==================== 便捷属性(兼容旧代码) ====================

  /// 创建日期（兼容旧代码）
  DateTime get createAt => entity.createAt;
  set createAt(DateTime value) => entity.createAt = value;

  /// 更新日期（兼容旧代码）
  DateTime get updateAt => entity.updateAt;
  set updateAt(DateTime value) => entity.updateAt = value;

  // ==================== 关联属性 ====================

  /// 关联的书籍
  BookModel? get book {
    final bookEntity = BookRepository.instance.getBookById(bookId);
    return bookEntity != null ? BookModel(bookEntity) : null;
  }

  // ==================== 转换方法 ====================

  /// 获取底层实体
  BookViewpoint toEntity() => entity;

  /// 转换为 JSON
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
