// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'database.dart';

// ignore_for_file: type=lint
class $ArticlesTable extends Articles with TableInfo<$ArticlesTable, Article> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ArticlesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
      'id', aliasedName, false,
      hasAutoIncrement: true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('PRIMARY KEY AUTOINCREMENT'));
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
      'title', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _aiTitleMeta =
      const VerificationMeta('aiTitle');
  @override
  late final GeneratedColumn<String> aiTitle = GeneratedColumn<String>(
      'ai_title', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _contentMeta =
      const VerificationMeta('content');
  @override
  late final GeneratedColumn<String> content = GeneratedColumn<String>(
      'content', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _aiContentMeta =
      const VerificationMeta('aiContent');
  @override
  late final GeneratedColumn<String> aiContent = GeneratedColumn<String>(
      'ai_content', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _htmlContentMeta =
      const VerificationMeta('htmlContent');
  @override
  late final GeneratedColumn<String> htmlContent = GeneratedColumn<String>(
      'html_content', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _urlMeta = const VerificationMeta('url');
  @override
  late final GeneratedColumn<String> url = GeneratedColumn<String>(
      'url', aliasedName, false,
      additionalChecks:
          GeneratedColumn.checkTextLength(minTextLength: 1, maxTextLength: 255),
      type: DriftSqlType.string,
      requiredDuringInsert: true,
      defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'));
  static const VerificationMeta _imageUrlMeta =
      const VerificationMeta('imageUrl');
  @override
  late final GeneratedColumn<String> imageUrl = GeneratedColumn<String>(
      'image_url', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _imagePathMeta =
      const VerificationMeta('imagePath');
  @override
  late final GeneratedColumn<String> imagePath = GeneratedColumn<String>(
      'image_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _screenshotPathMeta =
      const VerificationMeta('screenshotPath');
  @override
  late final GeneratedColumn<String> screenshotPath = GeneratedColumn<String>(
      'screenshot_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _isFavoriteMeta =
      const VerificationMeta('isFavorite');
  @override
  late final GeneratedColumn<bool> isFavorite = GeneratedColumn<bool>(
      'is_favorite', aliasedName, false,
      type: DriftSqlType.bool,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('CHECK ("is_favorite" IN (0, 1))'),
      defaultValue: const Constant(false));
  static const VerificationMeta _pubDateMeta =
      const VerificationMeta('pubDate');
  @override
  late final GeneratedColumn<DateTime> pubDate = GeneratedColumn<DateTime>(
      'pub_date', aliasedName, true,
      type: DriftSqlType.dateTime, requiredDuringInsert: false);
  static const VerificationMeta _commentMeta =
      const VerificationMeta('comment');
  @override
  late final GeneratedColumn<String> comment = GeneratedColumn<String>(
      'comment', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _updatedAtMeta =
      const VerificationMeta('updatedAt');
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  static const VerificationMeta _createdAtMeta =
      const VerificationMeta('createdAt');
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
      'created_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  @override
  List<GeneratedColumn> get $columns => [
        id,
        title,
        aiTitle,
        content,
        aiContent,
        htmlContent,
        url,
        imageUrl,
        imagePath,
        screenshotPath,
        isFavorite,
        pubDate,
        comment,
        updatedAt,
        createdAt
      ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'articles';
  @override
  VerificationContext validateIntegrity(Insertable<Article> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
          _titleMeta, title.isAcceptableOrUnknown(data['title']!, _titleMeta));
    }
    if (data.containsKey('ai_title')) {
      context.handle(_aiTitleMeta,
          aiTitle.isAcceptableOrUnknown(data['ai_title']!, _aiTitleMeta));
    }
    if (data.containsKey('content')) {
      context.handle(_contentMeta,
          content.isAcceptableOrUnknown(data['content']!, _contentMeta));
    }
    if (data.containsKey('ai_content')) {
      context.handle(_aiContentMeta,
          aiContent.isAcceptableOrUnknown(data['ai_content']!, _aiContentMeta));
    }
    if (data.containsKey('html_content')) {
      context.handle(
          _htmlContentMeta,
          htmlContent.isAcceptableOrUnknown(
              data['html_content']!, _htmlContentMeta));
    }
    if (data.containsKey('url')) {
      context.handle(
          _urlMeta, url.isAcceptableOrUnknown(data['url']!, _urlMeta));
    } else if (isInserting) {
      context.missing(_urlMeta);
    }
    if (data.containsKey('image_url')) {
      context.handle(_imageUrlMeta,
          imageUrl.isAcceptableOrUnknown(data['image_url']!, _imageUrlMeta));
    }
    if (data.containsKey('image_path')) {
      context.handle(_imagePathMeta,
          imagePath.isAcceptableOrUnknown(data['image_path']!, _imagePathMeta));
    }
    if (data.containsKey('screenshot_path')) {
      context.handle(
          _screenshotPathMeta,
          screenshotPath.isAcceptableOrUnknown(
              data['screenshot_path']!, _screenshotPathMeta));
    }
    if (data.containsKey('is_favorite')) {
      context.handle(
          _isFavoriteMeta,
          isFavorite.isAcceptableOrUnknown(
              data['is_favorite']!, _isFavoriteMeta));
    }
    if (data.containsKey('pub_date')) {
      context.handle(_pubDateMeta,
          pubDate.isAcceptableOrUnknown(data['pub_date']!, _pubDateMeta));
    }
    if (data.containsKey('comment')) {
      context.handle(_commentMeta,
          comment.isAcceptableOrUnknown(data['comment']!, _commentMeta));
    }
    if (data.containsKey('updated_at')) {
      context.handle(_updatedAtMeta,
          updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta));
    }
    if (data.containsKey('created_at')) {
      context.handle(_createdAtMeta,
          createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta));
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Article map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Article(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}id'])!,
      title: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}title']),
      aiTitle: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}ai_title']),
      content: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}content']),
      aiContent: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}ai_content']),
      htmlContent: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}html_content']),
      url: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}url'])!,
      imageUrl: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}image_url']),
      imagePath: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}image_path']),
      screenshotPath: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}screenshot_path']),
      isFavorite: attachedDatabase.typeMapping
          .read(DriftSqlType.bool, data['${effectivePrefix}is_favorite'])!,
      pubDate: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}pub_date']),
      comment: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}comment']),
      updatedAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}updated_at'])!,
      createdAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}created_at'])!,
    );
  }

  @override
  $ArticlesTable createAlias(String alias) {
    return $ArticlesTable(attachedDatabase, alias);
  }
}

class Article extends DataClass implements Insertable<Article> {
  final int id;
  final String? title;
  final String? aiTitle;
  final String? content;
  final String? aiContent;
  final String? htmlContent;
  final String url;
  final String? imageUrl;
  final String? imagePath;
  final String? screenshotPath;
  final bool isFavorite;
  final DateTime? pubDate;
  final String? comment;
  final DateTime updatedAt;
  final DateTime createdAt;
  const Article(
      {required this.id,
      this.title,
      this.aiTitle,
      this.content,
      this.aiContent,
      this.htmlContent,
      required this.url,
      this.imageUrl,
      this.imagePath,
      this.screenshotPath,
      required this.isFavorite,
      this.pubDate,
      this.comment,
      required this.updatedAt,
      required this.createdAt});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || title != null) {
      map['title'] = Variable<String>(title);
    }
    if (!nullToAbsent || aiTitle != null) {
      map['ai_title'] = Variable<String>(aiTitle);
    }
    if (!nullToAbsent || content != null) {
      map['content'] = Variable<String>(content);
    }
    if (!nullToAbsent || aiContent != null) {
      map['ai_content'] = Variable<String>(aiContent);
    }
    if (!nullToAbsent || htmlContent != null) {
      map['html_content'] = Variable<String>(htmlContent);
    }
    map['url'] = Variable<String>(url);
    if (!nullToAbsent || imageUrl != null) {
      map['image_url'] = Variable<String>(imageUrl);
    }
    if (!nullToAbsent || imagePath != null) {
      map['image_path'] = Variable<String>(imagePath);
    }
    if (!nullToAbsent || screenshotPath != null) {
      map['screenshot_path'] = Variable<String>(screenshotPath);
    }
    map['is_favorite'] = Variable<bool>(isFavorite);
    if (!nullToAbsent || pubDate != null) {
      map['pub_date'] = Variable<DateTime>(pubDate);
    }
    if (!nullToAbsent || comment != null) {
      map['comment'] = Variable<String>(comment);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ArticlesCompanion toCompanion(bool nullToAbsent) {
    return ArticlesCompanion(
      id: Value(id),
      title:
          title == null && nullToAbsent ? const Value.absent() : Value(title),
      aiTitle: aiTitle == null && nullToAbsent
          ? const Value.absent()
          : Value(aiTitle),
      content: content == null && nullToAbsent
          ? const Value.absent()
          : Value(content),
      aiContent: aiContent == null && nullToAbsent
          ? const Value.absent()
          : Value(aiContent),
      htmlContent: htmlContent == null && nullToAbsent
          ? const Value.absent()
          : Value(htmlContent),
      url: Value(url),
      imageUrl: imageUrl == null && nullToAbsent
          ? const Value.absent()
          : Value(imageUrl),
      imagePath: imagePath == null && nullToAbsent
          ? const Value.absent()
          : Value(imagePath),
      screenshotPath: screenshotPath == null && nullToAbsent
          ? const Value.absent()
          : Value(screenshotPath),
      isFavorite: Value(isFavorite),
      pubDate: pubDate == null && nullToAbsent
          ? const Value.absent()
          : Value(pubDate),
      comment: comment == null && nullToAbsent
          ? const Value.absent()
          : Value(comment),
      updatedAt: Value(updatedAt),
      createdAt: Value(createdAt),
    );
  }

  factory Article.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Article(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String?>(json['title']),
      aiTitle: serializer.fromJson<String?>(json['aiTitle']),
      content: serializer.fromJson<String?>(json['content']),
      aiContent: serializer.fromJson<String?>(json['aiContent']),
      htmlContent: serializer.fromJson<String?>(json['htmlContent']),
      url: serializer.fromJson<String>(json['url']),
      imageUrl: serializer.fromJson<String?>(json['imageUrl']),
      imagePath: serializer.fromJson<String?>(json['imagePath']),
      screenshotPath: serializer.fromJson<String?>(json['screenshotPath']),
      isFavorite: serializer.fromJson<bool>(json['isFavorite']),
      pubDate: serializer.fromJson<DateTime?>(json['pubDate']),
      comment: serializer.fromJson<String?>(json['comment']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'title': serializer.toJson<String?>(title),
      'aiTitle': serializer.toJson<String?>(aiTitle),
      'content': serializer.toJson<String?>(content),
      'aiContent': serializer.toJson<String?>(aiContent),
      'htmlContent': serializer.toJson<String?>(htmlContent),
      'url': serializer.toJson<String>(url),
      'imageUrl': serializer.toJson<String?>(imageUrl),
      'imagePath': serializer.toJson<String?>(imagePath),
      'screenshotPath': serializer.toJson<String?>(screenshotPath),
      'isFavorite': serializer.toJson<bool>(isFavorite),
      'pubDate': serializer.toJson<DateTime?>(pubDate),
      'comment': serializer.toJson<String?>(comment),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  Article copyWith(
          {int? id,
          Value<String?> title = const Value.absent(),
          Value<String?> aiTitle = const Value.absent(),
          Value<String?> content = const Value.absent(),
          Value<String?> aiContent = const Value.absent(),
          Value<String?> htmlContent = const Value.absent(),
          String? url,
          Value<String?> imageUrl = const Value.absent(),
          Value<String?> imagePath = const Value.absent(),
          Value<String?> screenshotPath = const Value.absent(),
          bool? isFavorite,
          Value<DateTime?> pubDate = const Value.absent(),
          Value<String?> comment = const Value.absent(),
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      Article(
        id: id ?? this.id,
        title: title.present ? title.value : this.title,
        aiTitle: aiTitle.present ? aiTitle.value : this.aiTitle,
        content: content.present ? content.value : this.content,
        aiContent: aiContent.present ? aiContent.value : this.aiContent,
        htmlContent: htmlContent.present ? htmlContent.value : this.htmlContent,
        url: url ?? this.url,
        imageUrl: imageUrl.present ? imageUrl.value : this.imageUrl,
        imagePath: imagePath.present ? imagePath.value : this.imagePath,
        screenshotPath:
            screenshotPath.present ? screenshotPath.value : this.screenshotPath,
        isFavorite: isFavorite ?? this.isFavorite,
        pubDate: pubDate.present ? pubDate.value : this.pubDate,
        comment: comment.present ? comment.value : this.comment,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  Article copyWithCompanion(ArticlesCompanion data) {
    return Article(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      aiTitle: data.aiTitle.present ? data.aiTitle.value : this.aiTitle,
      content: data.content.present ? data.content.value : this.content,
      aiContent: data.aiContent.present ? data.aiContent.value : this.aiContent,
      htmlContent:
          data.htmlContent.present ? data.htmlContent.value : this.htmlContent,
      url: data.url.present ? data.url.value : this.url,
      imageUrl: data.imageUrl.present ? data.imageUrl.value : this.imageUrl,
      imagePath: data.imagePath.present ? data.imagePath.value : this.imagePath,
      screenshotPath: data.screenshotPath.present
          ? data.screenshotPath.value
          : this.screenshotPath,
      isFavorite:
          data.isFavorite.present ? data.isFavorite.value : this.isFavorite,
      pubDate: data.pubDate.present ? data.pubDate.value : this.pubDate,
      comment: data.comment.present ? data.comment.value : this.comment,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Article(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('aiTitle: $aiTitle, ')
          ..write('content: $content, ')
          ..write('aiContent: $aiContent, ')
          ..write('htmlContent: $htmlContent, ')
          ..write('url: $url, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('imagePath: $imagePath, ')
          ..write('screenshotPath: $screenshotPath, ')
          ..write('isFavorite: $isFavorite, ')
          ..write('pubDate: $pubDate, ')
          ..write('comment: $comment, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
      id,
      title,
      aiTitle,
      content,
      aiContent,
      htmlContent,
      url,
      imageUrl,
      imagePath,
      screenshotPath,
      isFavorite,
      pubDate,
      comment,
      updatedAt,
      createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Article &&
          other.id == this.id &&
          other.title == this.title &&
          other.aiTitle == this.aiTitle &&
          other.content == this.content &&
          other.aiContent == this.aiContent &&
          other.htmlContent == this.htmlContent &&
          other.url == this.url &&
          other.imageUrl == this.imageUrl &&
          other.imagePath == this.imagePath &&
          other.screenshotPath == this.screenshotPath &&
          other.isFavorite == this.isFavorite &&
          other.pubDate == this.pubDate &&
          other.comment == this.comment &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class ArticlesCompanion extends UpdateCompanion<Article> {
  final Value<int> id;
  final Value<String?> title;
  final Value<String?> aiTitle;
  final Value<String?> content;
  final Value<String?> aiContent;
  final Value<String?> htmlContent;
  final Value<String> url;
  final Value<String?> imageUrl;
  final Value<String?> imagePath;
  final Value<String?> screenshotPath;
  final Value<bool> isFavorite;
  final Value<DateTime?> pubDate;
  final Value<String?> comment;
  final Value<DateTime> updatedAt;
  final Value<DateTime> createdAt;
  const ArticlesCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.aiTitle = const Value.absent(),
    this.content = const Value.absent(),
    this.aiContent = const Value.absent(),
    this.htmlContent = const Value.absent(),
    this.url = const Value.absent(),
    this.imageUrl = const Value.absent(),
    this.imagePath = const Value.absent(),
    this.screenshotPath = const Value.absent(),
    this.isFavorite = const Value.absent(),
    this.pubDate = const Value.absent(),
    this.comment = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  ArticlesCompanion.insert({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.aiTitle = const Value.absent(),
    this.content = const Value.absent(),
    this.aiContent = const Value.absent(),
    this.htmlContent = const Value.absent(),
    required String url,
    this.imageUrl = const Value.absent(),
    this.imagePath = const Value.absent(),
    this.screenshotPath = const Value.absent(),
    this.isFavorite = const Value.absent(),
    this.pubDate = const Value.absent(),
    this.comment = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : url = Value(url);
  static Insertable<Article> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<String>? aiTitle,
    Expression<String>? content,
    Expression<String>? aiContent,
    Expression<String>? htmlContent,
    Expression<String>? url,
    Expression<String>? imageUrl,
    Expression<String>? imagePath,
    Expression<String>? screenshotPath,
    Expression<bool>? isFavorite,
    Expression<DateTime>? pubDate,
    Expression<String>? comment,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (aiTitle != null) 'ai_title': aiTitle,
      if (content != null) 'content': content,
      if (aiContent != null) 'ai_content': aiContent,
      if (htmlContent != null) 'html_content': htmlContent,
      if (url != null) 'url': url,
      if (imageUrl != null) 'image_url': imageUrl,
      if (imagePath != null) 'image_path': imagePath,
      if (screenshotPath != null) 'screenshot_path': screenshotPath,
      if (isFavorite != null) 'is_favorite': isFavorite,
      if (pubDate != null) 'pub_date': pubDate,
      if (comment != null) 'comment': comment,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  ArticlesCompanion copyWith(
      {Value<int>? id,
      Value<String?>? title,
      Value<String?>? aiTitle,
      Value<String?>? content,
      Value<String?>? aiContent,
      Value<String?>? htmlContent,
      Value<String>? url,
      Value<String?>? imageUrl,
      Value<String?>? imagePath,
      Value<String?>? screenshotPath,
      Value<bool>? isFavorite,
      Value<DateTime?>? pubDate,
      Value<String?>? comment,
      Value<DateTime>? updatedAt,
      Value<DateTime>? createdAt}) {
    return ArticlesCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      aiTitle: aiTitle ?? this.aiTitle,
      content: content ?? this.content,
      aiContent: aiContent ?? this.aiContent,
      htmlContent: htmlContent ?? this.htmlContent,
      url: url ?? this.url,
      imageUrl: imageUrl ?? this.imageUrl,
      imagePath: imagePath ?? this.imagePath,
      screenshotPath: screenshotPath ?? this.screenshotPath,
      isFavorite: isFavorite ?? this.isFavorite,
      pubDate: pubDate ?? this.pubDate,
      comment: comment ?? this.comment,
      updatedAt: updatedAt ?? this.updatedAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (aiTitle.present) {
      map['ai_title'] = Variable<String>(aiTitle.value);
    }
    if (content.present) {
      map['content'] = Variable<String>(content.value);
    }
    if (aiContent.present) {
      map['ai_content'] = Variable<String>(aiContent.value);
    }
    if (htmlContent.present) {
      map['html_content'] = Variable<String>(htmlContent.value);
    }
    if (url.present) {
      map['url'] = Variable<String>(url.value);
    }
    if (imageUrl.present) {
      map['image_url'] = Variable<String>(imageUrl.value);
    }
    if (imagePath.present) {
      map['image_path'] = Variable<String>(imagePath.value);
    }
    if (screenshotPath.present) {
      map['screenshot_path'] = Variable<String>(screenshotPath.value);
    }
    if (isFavorite.present) {
      map['is_favorite'] = Variable<bool>(isFavorite.value);
    }
    if (pubDate.present) {
      map['pub_date'] = Variable<DateTime>(pubDate.value);
    }
    if (comment.present) {
      map['comment'] = Variable<String>(comment.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ArticlesCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('aiTitle: $aiTitle, ')
          ..write('content: $content, ')
          ..write('aiContent: $aiContent, ')
          ..write('htmlContent: $htmlContent, ')
          ..write('url: $url, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('imagePath: $imagePath, ')
          ..write('screenshotPath: $screenshotPath, ')
          ..write('isFavorite: $isFavorite, ')
          ..write('pubDate: $pubDate, ')
          ..write('comment: $comment, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $SettingsTable extends Settings with TableInfo<$SettingsTable, Setting> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SettingsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _keyMeta = const VerificationMeta('key');
  @override
  late final GeneratedColumn<String> key = GeneratedColumn<String>(
      'key', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _valueMeta = const VerificationMeta('value');
  @override
  late final GeneratedColumn<String> value = GeneratedColumn<String>(
      'value', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _updatedAtMeta =
      const VerificationMeta('updatedAt');
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  static const VerificationMeta _createdAtMeta =
      const VerificationMeta('createdAt');
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
      'created_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  @override
  List<GeneratedColumn> get $columns => [key, value, updatedAt, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'settings';
  @override
  VerificationContext validateIntegrity(Insertable<Setting> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('key')) {
      context.handle(
          _keyMeta, key.isAcceptableOrUnknown(data['key']!, _keyMeta));
    } else if (isInserting) {
      context.missing(_keyMeta);
    }
    if (data.containsKey('value')) {
      context.handle(
          _valueMeta, value.isAcceptableOrUnknown(data['value']!, _valueMeta));
    } else if (isInserting) {
      context.missing(_valueMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(_updatedAtMeta,
          updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta));
    }
    if (data.containsKey('created_at')) {
      context.handle(_createdAtMeta,
          createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta));
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {key};
  @override
  Setting map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Setting(
      key: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}key'])!,
      value: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}value'])!,
      updatedAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}updated_at'])!,
      createdAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}created_at'])!,
    );
  }

  @override
  $SettingsTable createAlias(String alias) {
    return $SettingsTable(attachedDatabase, alias);
  }
}

class Setting extends DataClass implements Insertable<Setting> {
  final String key;
  final String value;
  final DateTime updatedAt;
  final DateTime createdAt;
  const Setting(
      {required this.key,
      required this.value,
      required this.updatedAt,
      required this.createdAt});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['key'] = Variable<String>(key);
    map['value'] = Variable<String>(value);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  SettingsCompanion toCompanion(bool nullToAbsent) {
    return SettingsCompanion(
      key: Value(key),
      value: Value(value),
      updatedAt: Value(updatedAt),
      createdAt: Value(createdAt),
    );
  }

  factory Setting.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Setting(
      key: serializer.fromJson<String>(json['key']),
      value: serializer.fromJson<String>(json['value']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'key': serializer.toJson<String>(key),
      'value': serializer.toJson<String>(value),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  Setting copyWith(
          {String? key,
          String? value,
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      Setting(
        key: key ?? this.key,
        value: value ?? this.value,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  Setting copyWithCompanion(SettingsCompanion data) {
    return Setting(
      key: data.key.present ? data.key.value : this.key,
      value: data.value.present ? data.value.value : this.value,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Setting(')
          ..write('key: $key, ')
          ..write('value: $value, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(key, value, updatedAt, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Setting &&
          other.key == this.key &&
          other.value == this.value &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class SettingsCompanion extends UpdateCompanion<Setting> {
  final Value<String> key;
  final Value<String> value;
  final Value<DateTime> updatedAt;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const SettingsCompanion({
    this.key = const Value.absent(),
    this.value = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SettingsCompanion.insert({
    required String key,
    required String value,
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  })  : key = Value(key),
        value = Value(value);
  static Insertable<Setting> custom({
    Expression<String>? key,
    Expression<String>? value,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (key != null) 'key': key,
      if (value != null) 'value': value,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SettingsCompanion copyWith(
      {Value<String>? key,
      Value<String>? value,
      Value<DateTime>? updatedAt,
      Value<DateTime>? createdAt,
      Value<int>? rowid}) {
    return SettingsCompanion(
      key: key ?? this.key,
      value: value ?? this.value,
      updatedAt: updatedAt ?? this.updatedAt,
      createdAt: createdAt ?? this.createdAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (key.present) {
      map['key'] = Variable<String>(key.value);
    }
    if (value.present) {
      map['value'] = Variable<String>(value.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SettingsCompanion(')
          ..write('key: $key, ')
          ..write('value: $value, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $ArticleImagesTable extends ArticleImages
    with TableInfo<$ArticleImagesTable, ArticleImage> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ArticleImagesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
      'id', aliasedName, false,
      hasAutoIncrement: true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('PRIMARY KEY AUTOINCREMENT'));
  static const VerificationMeta _articleMeta =
      const VerificationMeta('article');
  @override
  late final GeneratedColumn<int> article = GeneratedColumn<int>(
      'article', aliasedName, true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('REFERENCES articles (id)'));
  static const VerificationMeta _imageUrlMeta =
      const VerificationMeta('imageUrl');
  @override
  late final GeneratedColumn<String> imageUrl = GeneratedColumn<String>(
      'image_url', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _imagePathMeta =
      const VerificationMeta('imagePath');
  @override
  late final GeneratedColumn<String> imagePath = GeneratedColumn<String>(
      'image_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _updatedAtMeta =
      const VerificationMeta('updatedAt');
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  static const VerificationMeta _createdAtMeta =
      const VerificationMeta('createdAt');
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
      'created_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  @override
  List<GeneratedColumn> get $columns =>
      [id, article, imageUrl, imagePath, updatedAt, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'article_images';
  @override
  VerificationContext validateIntegrity(Insertable<ArticleImage> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('article')) {
      context.handle(_articleMeta,
          article.isAcceptableOrUnknown(data['article']!, _articleMeta));
    }
    if (data.containsKey('image_url')) {
      context.handle(_imageUrlMeta,
          imageUrl.isAcceptableOrUnknown(data['image_url']!, _imageUrlMeta));
    }
    if (data.containsKey('image_path')) {
      context.handle(_imagePathMeta,
          imagePath.isAcceptableOrUnknown(data['image_path']!, _imagePathMeta));
    }
    if (data.containsKey('updated_at')) {
      context.handle(_updatedAtMeta,
          updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta));
    }
    if (data.containsKey('created_at')) {
      context.handle(_createdAtMeta,
          createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta));
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ArticleImage map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArticleImage(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}id'])!,
      article: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}article']),
      imageUrl: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}image_url']),
      imagePath: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}image_path']),
      updatedAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}updated_at'])!,
      createdAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}created_at'])!,
    );
  }

  @override
  $ArticleImagesTable createAlias(String alias) {
    return $ArticleImagesTable(attachedDatabase, alias);
  }
}

class ArticleImage extends DataClass implements Insertable<ArticleImage> {
  final int id;
  final int? article;
  final String? imageUrl;
  final String? imagePath;
  final DateTime updatedAt;
  final DateTime createdAt;
  const ArticleImage(
      {required this.id,
      this.article,
      this.imageUrl,
      this.imagePath,
      required this.updatedAt,
      required this.createdAt});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || article != null) {
      map['article'] = Variable<int>(article);
    }
    if (!nullToAbsent || imageUrl != null) {
      map['image_url'] = Variable<String>(imageUrl);
    }
    if (!nullToAbsent || imagePath != null) {
      map['image_path'] = Variable<String>(imagePath);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ArticleImagesCompanion toCompanion(bool nullToAbsent) {
    return ArticleImagesCompanion(
      id: Value(id),
      article: article == null && nullToAbsent
          ? const Value.absent()
          : Value(article),
      imageUrl: imageUrl == null && nullToAbsent
          ? const Value.absent()
          : Value(imageUrl),
      imagePath: imagePath == null && nullToAbsent
          ? const Value.absent()
          : Value(imagePath),
      updatedAt: Value(updatedAt),
      createdAt: Value(createdAt),
    );
  }

  factory ArticleImage.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArticleImage(
      id: serializer.fromJson<int>(json['id']),
      article: serializer.fromJson<int?>(json['article']),
      imageUrl: serializer.fromJson<String?>(json['imageUrl']),
      imagePath: serializer.fromJson<String?>(json['imagePath']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'article': serializer.toJson<int?>(article),
      'imageUrl': serializer.toJson<String?>(imageUrl),
      'imagePath': serializer.toJson<String?>(imagePath),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  ArticleImage copyWith(
          {int? id,
          Value<int?> article = const Value.absent(),
          Value<String?> imageUrl = const Value.absent(),
          Value<String?> imagePath = const Value.absent(),
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      ArticleImage(
        id: id ?? this.id,
        article: article.present ? article.value : this.article,
        imageUrl: imageUrl.present ? imageUrl.value : this.imageUrl,
        imagePath: imagePath.present ? imagePath.value : this.imagePath,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  ArticleImage copyWithCompanion(ArticleImagesCompanion data) {
    return ArticleImage(
      id: data.id.present ? data.id.value : this.id,
      article: data.article.present ? data.article.value : this.article,
      imageUrl: data.imageUrl.present ? data.imageUrl.value : this.imageUrl,
      imagePath: data.imagePath.present ? data.imagePath.value : this.imagePath,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ArticleImage(')
          ..write('id: $id, ')
          ..write('article: $article, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('imagePath: $imagePath, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, article, imageUrl, imagePath, updatedAt, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ArticleImage &&
          other.id == this.id &&
          other.article == this.article &&
          other.imageUrl == this.imageUrl &&
          other.imagePath == this.imagePath &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class ArticleImagesCompanion extends UpdateCompanion<ArticleImage> {
  final Value<int> id;
  final Value<int?> article;
  final Value<String?> imageUrl;
  final Value<String?> imagePath;
  final Value<DateTime> updatedAt;
  final Value<DateTime> createdAt;
  const ArticleImagesCompanion({
    this.id = const Value.absent(),
    this.article = const Value.absent(),
    this.imageUrl = const Value.absent(),
    this.imagePath = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  ArticleImagesCompanion.insert({
    this.id = const Value.absent(),
    this.article = const Value.absent(),
    this.imageUrl = const Value.absent(),
    this.imagePath = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  static Insertable<ArticleImage> custom({
    Expression<int>? id,
    Expression<int>? article,
    Expression<String>? imageUrl,
    Expression<String>? imagePath,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (article != null) 'article': article,
      if (imageUrl != null) 'image_url': imageUrl,
      if (imagePath != null) 'image_path': imagePath,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  ArticleImagesCompanion copyWith(
      {Value<int>? id,
      Value<int?>? article,
      Value<String?>? imageUrl,
      Value<String?>? imagePath,
      Value<DateTime>? updatedAt,
      Value<DateTime>? createdAt}) {
    return ArticleImagesCompanion(
      id: id ?? this.id,
      article: article ?? this.article,
      imageUrl: imageUrl ?? this.imageUrl,
      imagePath: imagePath ?? this.imagePath,
      updatedAt: updatedAt ?? this.updatedAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (article.present) {
      map['article'] = Variable<int>(article.value);
    }
    if (imageUrl.present) {
      map['image_url'] = Variable<String>(imageUrl.value);
    }
    if (imagePath.present) {
      map['image_path'] = Variable<String>(imagePath.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ArticleImagesCompanion(')
          ..write('id: $id, ')
          ..write('article: $article, ')
          ..write('imageUrl: $imageUrl, ')
          ..write('imagePath: $imagePath, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $ArticlesTable articles = $ArticlesTable(this);
  late final $SettingsTable settings = $SettingsTable(this);
  late final $ArticleImagesTable articleImages = $ArticleImagesTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities =>
      [articles, settings, articleImages];
}

typedef $$ArticlesTableCreateCompanionBuilder = ArticlesCompanion Function({
  Value<int> id,
  Value<String?> title,
  Value<String?> aiTitle,
  Value<String?> content,
  Value<String?> aiContent,
  Value<String?> htmlContent,
  required String url,
  Value<String?> imageUrl,
  Value<String?> imagePath,
  Value<String?> screenshotPath,
  Value<bool> isFavorite,
  Value<DateTime?> pubDate,
  Value<String?> comment,
  Value<DateTime> updatedAt,
  Value<DateTime> createdAt,
});
typedef $$ArticlesTableUpdateCompanionBuilder = ArticlesCompanion Function({
  Value<int> id,
  Value<String?> title,
  Value<String?> aiTitle,
  Value<String?> content,
  Value<String?> aiContent,
  Value<String?> htmlContent,
  Value<String> url,
  Value<String?> imageUrl,
  Value<String?> imagePath,
  Value<String?> screenshotPath,
  Value<bool> isFavorite,
  Value<DateTime?> pubDate,
  Value<String?> comment,
  Value<DateTime> updatedAt,
  Value<DateTime> createdAt,
});

final class $$ArticlesTableReferences
    extends BaseReferences<_$AppDatabase, $ArticlesTable, Article> {
  $$ArticlesTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static MultiTypedResultKey<$ArticleImagesTable, List<ArticleImage>>
      _articleImagesRefsTable(_$AppDatabase db) =>
          MultiTypedResultKey.fromTable(db.articleImages,
              aliasName: $_aliasNameGenerator(
                  db.articles.id, db.articleImages.article));

  $$ArticleImagesTableProcessedTableManager get articleImagesRefs {
    final manager = $$ArticleImagesTableTableManager($_db, $_db.articleImages)
        .filter((f) => f.article.id($_item.id));

    final cache = $_typedResult.readTableOrNull(_articleImagesRefsTable($_db));
    return ProcessedTableManager(
        manager.$state.copyWith(prefetchedData: cache));
  }
}

class $$ArticlesTableFilterComposer
    extends Composer<_$AppDatabase, $ArticlesTable> {
  $$ArticlesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get title => $composableBuilder(
      column: $table.title, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get aiTitle => $composableBuilder(
      column: $table.aiTitle, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get content => $composableBuilder(
      column: $table.content, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get aiContent => $composableBuilder(
      column: $table.aiContent, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get htmlContent => $composableBuilder(
      column: $table.htmlContent, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get url => $composableBuilder(
      column: $table.url, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get imageUrl => $composableBuilder(
      column: $table.imageUrl, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get imagePath => $composableBuilder(
      column: $table.imagePath, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get screenshotPath => $composableBuilder(
      column: $table.screenshotPath,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<bool> get isFavorite => $composableBuilder(
      column: $table.isFavorite, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get pubDate => $composableBuilder(
      column: $table.pubDate, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get comment => $composableBuilder(
      column: $table.comment, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
      column: $table.updatedAt, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
      column: $table.createdAt, builder: (column) => ColumnFilters(column));

  Expression<bool> articleImagesRefs(
      Expression<bool> Function($$ArticleImagesTableFilterComposer f) f) {
    final $$ArticleImagesTableFilterComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.id,
        referencedTable: $db.articleImages,
        getReferencedColumn: (t) => t.article,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$ArticleImagesTableFilterComposer(
              $db: $db,
              $table: $db.articleImages,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return f(composer);
  }
}

class $$ArticlesTableOrderingComposer
    extends Composer<_$AppDatabase, $ArticlesTable> {
  $$ArticlesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get title => $composableBuilder(
      column: $table.title, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get aiTitle => $composableBuilder(
      column: $table.aiTitle, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get content => $composableBuilder(
      column: $table.content, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get aiContent => $composableBuilder(
      column: $table.aiContent, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get htmlContent => $composableBuilder(
      column: $table.htmlContent, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get url => $composableBuilder(
      column: $table.url, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get imageUrl => $composableBuilder(
      column: $table.imageUrl, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get imagePath => $composableBuilder(
      column: $table.imagePath, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get screenshotPath => $composableBuilder(
      column: $table.screenshotPath,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<bool> get isFavorite => $composableBuilder(
      column: $table.isFavorite, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get pubDate => $composableBuilder(
      column: $table.pubDate, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get comment => $composableBuilder(
      column: $table.comment, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
      column: $table.updatedAt, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
      column: $table.createdAt, builder: (column) => ColumnOrderings(column));
}

class $$ArticlesTableAnnotationComposer
    extends Composer<_$AppDatabase, $ArticlesTable> {
  $$ArticlesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get aiTitle =>
      $composableBuilder(column: $table.aiTitle, builder: (column) => column);

  GeneratedColumn<String> get content =>
      $composableBuilder(column: $table.content, builder: (column) => column);

  GeneratedColumn<String> get aiContent =>
      $composableBuilder(column: $table.aiContent, builder: (column) => column);

  GeneratedColumn<String> get htmlContent => $composableBuilder(
      column: $table.htmlContent, builder: (column) => column);

  GeneratedColumn<String> get url =>
      $composableBuilder(column: $table.url, builder: (column) => column);

  GeneratedColumn<String> get imageUrl =>
      $composableBuilder(column: $table.imageUrl, builder: (column) => column);

  GeneratedColumn<String> get imagePath =>
      $composableBuilder(column: $table.imagePath, builder: (column) => column);

  GeneratedColumn<String> get screenshotPath => $composableBuilder(
      column: $table.screenshotPath, builder: (column) => column);

  GeneratedColumn<bool> get isFavorite => $composableBuilder(
      column: $table.isFavorite, builder: (column) => column);

  GeneratedColumn<DateTime> get pubDate =>
      $composableBuilder(column: $table.pubDate, builder: (column) => column);

  GeneratedColumn<String> get comment =>
      $composableBuilder(column: $table.comment, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  Expression<T> articleImagesRefs<T extends Object>(
      Expression<T> Function($$ArticleImagesTableAnnotationComposer a) f) {
    final $$ArticleImagesTableAnnotationComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.id,
        referencedTable: $db.articleImages,
        getReferencedColumn: (t) => t.article,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$ArticleImagesTableAnnotationComposer(
              $db: $db,
              $table: $db.articleImages,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return f(composer);
  }
}

class $$ArticlesTableTableManager extends RootTableManager<
    _$AppDatabase,
    $ArticlesTable,
    Article,
    $$ArticlesTableFilterComposer,
    $$ArticlesTableOrderingComposer,
    $$ArticlesTableAnnotationComposer,
    $$ArticlesTableCreateCompanionBuilder,
    $$ArticlesTableUpdateCompanionBuilder,
    (Article, $$ArticlesTableReferences),
    Article,
    PrefetchHooks Function({bool articleImagesRefs})> {
  $$ArticlesTableTableManager(_$AppDatabase db, $ArticlesTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ArticlesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ArticlesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ArticlesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<int> id = const Value.absent(),
            Value<String?> title = const Value.absent(),
            Value<String?> aiTitle = const Value.absent(),
            Value<String?> content = const Value.absent(),
            Value<String?> aiContent = const Value.absent(),
            Value<String?> htmlContent = const Value.absent(),
            Value<String> url = const Value.absent(),
            Value<String?> imageUrl = const Value.absent(),
            Value<String?> imagePath = const Value.absent(),
            Value<String?> screenshotPath = const Value.absent(),
            Value<bool> isFavorite = const Value.absent(),
            Value<DateTime?> pubDate = const Value.absent(),
            Value<String?> comment = const Value.absent(),
            Value<DateTime> updatedAt = const Value.absent(),
            Value<DateTime> createdAt = const Value.absent(),
          }) =>
              ArticlesCompanion(
            id: id,
            title: title,
            aiTitle: aiTitle,
            content: content,
            aiContent: aiContent,
            htmlContent: htmlContent,
            url: url,
            imageUrl: imageUrl,
            imagePath: imagePath,
            screenshotPath: screenshotPath,
            isFavorite: isFavorite,
            pubDate: pubDate,
            comment: comment,
            updatedAt: updatedAt,
            createdAt: createdAt,
          ),
          createCompanionCallback: ({
            Value<int> id = const Value.absent(),
            Value<String?> title = const Value.absent(),
            Value<String?> aiTitle = const Value.absent(),
            Value<String?> content = const Value.absent(),
            Value<String?> aiContent = const Value.absent(),
            Value<String?> htmlContent = const Value.absent(),
            required String url,
            Value<String?> imageUrl = const Value.absent(),
            Value<String?> imagePath = const Value.absent(),
            Value<String?> screenshotPath = const Value.absent(),
            Value<bool> isFavorite = const Value.absent(),
            Value<DateTime?> pubDate = const Value.absent(),
            Value<String?> comment = const Value.absent(),
            Value<DateTime> updatedAt = const Value.absent(),
            Value<DateTime> createdAt = const Value.absent(),
          }) =>
              ArticlesCompanion.insert(
            id: id,
            title: title,
            aiTitle: aiTitle,
            content: content,
            aiContent: aiContent,
            htmlContent: htmlContent,
            url: url,
            imageUrl: imageUrl,
            imagePath: imagePath,
            screenshotPath: screenshotPath,
            isFavorite: isFavorite,
            pubDate: pubDate,
            comment: comment,
            updatedAt: updatedAt,
            createdAt: createdAt,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) =>
                  (e.readTable(table), $$ArticlesTableReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: ({articleImagesRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [
                if (articleImagesRefs) db.articleImages
              ],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (articleImagesRefs)
                    await $_getPrefetchedData(
                        currentTable: table,
                        referencedTable: $$ArticlesTableReferences
                            ._articleImagesRefsTable(db),
                        managerFromTypedResult: (p0) =>
                            $$ArticlesTableReferences(db, table, p0)
                                .articleImagesRefs,
                        referencedItemsForCurrentItem: (item,
                                referencedItems) =>
                            referencedItems.where((e) => e.article == item.id),
                        typedResults: items)
                ];
              },
            );
          },
        ));
}

typedef $$ArticlesTableProcessedTableManager = ProcessedTableManager<
    _$AppDatabase,
    $ArticlesTable,
    Article,
    $$ArticlesTableFilterComposer,
    $$ArticlesTableOrderingComposer,
    $$ArticlesTableAnnotationComposer,
    $$ArticlesTableCreateCompanionBuilder,
    $$ArticlesTableUpdateCompanionBuilder,
    (Article, $$ArticlesTableReferences),
    Article,
    PrefetchHooks Function({bool articleImagesRefs})>;
typedef $$SettingsTableCreateCompanionBuilder = SettingsCompanion Function({
  required String key,
  required String value,
  Value<DateTime> updatedAt,
  Value<DateTime> createdAt,
  Value<int> rowid,
});
typedef $$SettingsTableUpdateCompanionBuilder = SettingsCompanion Function({
  Value<String> key,
  Value<String> value,
  Value<DateTime> updatedAt,
  Value<DateTime> createdAt,
  Value<int> rowid,
});

class $$SettingsTableFilterComposer
    extends Composer<_$AppDatabase, $SettingsTable> {
  $$SettingsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get key => $composableBuilder(
      column: $table.key, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get value => $composableBuilder(
      column: $table.value, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
      column: $table.updatedAt, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
      column: $table.createdAt, builder: (column) => ColumnFilters(column));
}

class $$SettingsTableOrderingComposer
    extends Composer<_$AppDatabase, $SettingsTable> {
  $$SettingsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get key => $composableBuilder(
      column: $table.key, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get value => $composableBuilder(
      column: $table.value, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
      column: $table.updatedAt, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
      column: $table.createdAt, builder: (column) => ColumnOrderings(column));
}

class $$SettingsTableAnnotationComposer
    extends Composer<_$AppDatabase, $SettingsTable> {
  $$SettingsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get key =>
      $composableBuilder(column: $table.key, builder: (column) => column);

  GeneratedColumn<String> get value =>
      $composableBuilder(column: $table.value, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$SettingsTableTableManager extends RootTableManager<
    _$AppDatabase,
    $SettingsTable,
    Setting,
    $$SettingsTableFilterComposer,
    $$SettingsTableOrderingComposer,
    $$SettingsTableAnnotationComposer,
    $$SettingsTableCreateCompanionBuilder,
    $$SettingsTableUpdateCompanionBuilder,
    (Setting, BaseReferences<_$AppDatabase, $SettingsTable, Setting>),
    Setting,
    PrefetchHooks Function()> {
  $$SettingsTableTableManager(_$AppDatabase db, $SettingsTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SettingsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SettingsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SettingsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<String> key = const Value.absent(),
            Value<String> value = const Value.absent(),
            Value<DateTime> updatedAt = const Value.absent(),
            Value<DateTime> createdAt = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              SettingsCompanion(
            key: key,
            value: value,
            updatedAt: updatedAt,
            createdAt: createdAt,
            rowid: rowid,
          ),
          createCompanionCallback: ({
            required String key,
            required String value,
            Value<DateTime> updatedAt = const Value.absent(),
            Value<DateTime> createdAt = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              SettingsCompanion.insert(
            key: key,
            value: value,
            updatedAt: updatedAt,
            createdAt: createdAt,
            rowid: rowid,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ));
}

typedef $$SettingsTableProcessedTableManager = ProcessedTableManager<
    _$AppDatabase,
    $SettingsTable,
    Setting,
    $$SettingsTableFilterComposer,
    $$SettingsTableOrderingComposer,
    $$SettingsTableAnnotationComposer,
    $$SettingsTableCreateCompanionBuilder,
    $$SettingsTableUpdateCompanionBuilder,
    (Setting, BaseReferences<_$AppDatabase, $SettingsTable, Setting>),
    Setting,
    PrefetchHooks Function()>;
typedef $$ArticleImagesTableCreateCompanionBuilder = ArticleImagesCompanion
    Function({
  Value<int> id,
  Value<int?> article,
  Value<String?> imageUrl,
  Value<String?> imagePath,
  Value<DateTime> updatedAt,
  Value<DateTime> createdAt,
});
typedef $$ArticleImagesTableUpdateCompanionBuilder = ArticleImagesCompanion
    Function({
  Value<int> id,
  Value<int?> article,
  Value<String?> imageUrl,
  Value<String?> imagePath,
  Value<DateTime> updatedAt,
  Value<DateTime> createdAt,
});

final class $$ArticleImagesTableReferences
    extends BaseReferences<_$AppDatabase, $ArticleImagesTable, ArticleImage> {
  $$ArticleImagesTableReferences(
      super.$_db, super.$_table, super.$_typedResult);

  static $ArticlesTable _articleTable(_$AppDatabase db) =>
      db.articles.createAlias(
          $_aliasNameGenerator(db.articleImages.article, db.articles.id));

  $$ArticlesTableProcessedTableManager? get article {
    if ($_item.article == null) return null;
    final manager = $$ArticlesTableTableManager($_db, $_db.articles)
        .filter((f) => f.id($_item.article!));
    final item = $_typedResult.readTableOrNull(_articleTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
        manager.$state.copyWith(prefetchedData: [item]));
  }
}

class $$ArticleImagesTableFilterComposer
    extends Composer<_$AppDatabase, $ArticleImagesTable> {
  $$ArticleImagesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get imageUrl => $composableBuilder(
      column: $table.imageUrl, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get imagePath => $composableBuilder(
      column: $table.imagePath, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
      column: $table.updatedAt, builder: (column) => ColumnFilters(column));

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
      column: $table.createdAt, builder: (column) => ColumnFilters(column));

  $$ArticlesTableFilterComposer get article {
    final $$ArticlesTableFilterComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.article,
        referencedTable: $db.articles,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$ArticlesTableFilterComposer(
              $db: $db,
              $table: $db.articles,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$ArticleImagesTableOrderingComposer
    extends Composer<_$AppDatabase, $ArticleImagesTable> {
  $$ArticleImagesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get imageUrl => $composableBuilder(
      column: $table.imageUrl, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get imagePath => $composableBuilder(
      column: $table.imagePath, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
      column: $table.updatedAt, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
      column: $table.createdAt, builder: (column) => ColumnOrderings(column));

  $$ArticlesTableOrderingComposer get article {
    final $$ArticlesTableOrderingComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.article,
        referencedTable: $db.articles,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$ArticlesTableOrderingComposer(
              $db: $db,
              $table: $db.articles,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$ArticleImagesTableAnnotationComposer
    extends Composer<_$AppDatabase, $ArticleImagesTable> {
  $$ArticleImagesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get imageUrl =>
      $composableBuilder(column: $table.imageUrl, builder: (column) => column);

  GeneratedColumn<String> get imagePath =>
      $composableBuilder(column: $table.imagePath, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$ArticlesTableAnnotationComposer get article {
    final $$ArticlesTableAnnotationComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.article,
        referencedTable: $db.articles,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$ArticlesTableAnnotationComposer(
              $db: $db,
              $table: $db.articles,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$ArticleImagesTableTableManager extends RootTableManager<
    _$AppDatabase,
    $ArticleImagesTable,
    ArticleImage,
    $$ArticleImagesTableFilterComposer,
    $$ArticleImagesTableOrderingComposer,
    $$ArticleImagesTableAnnotationComposer,
    $$ArticleImagesTableCreateCompanionBuilder,
    $$ArticleImagesTableUpdateCompanionBuilder,
    (ArticleImage, $$ArticleImagesTableReferences),
    ArticleImage,
    PrefetchHooks Function({bool article})> {
  $$ArticleImagesTableTableManager(_$AppDatabase db, $ArticleImagesTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ArticleImagesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ArticleImagesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ArticleImagesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<int> id = const Value.absent(),
            Value<int?> article = const Value.absent(),
            Value<String?> imageUrl = const Value.absent(),
            Value<String?> imagePath = const Value.absent(),
            Value<DateTime> updatedAt = const Value.absent(),
            Value<DateTime> createdAt = const Value.absent(),
          }) =>
              ArticleImagesCompanion(
            id: id,
            article: article,
            imageUrl: imageUrl,
            imagePath: imagePath,
            updatedAt: updatedAt,
            createdAt: createdAt,
          ),
          createCompanionCallback: ({
            Value<int> id = const Value.absent(),
            Value<int?> article = const Value.absent(),
            Value<String?> imageUrl = const Value.absent(),
            Value<String?> imagePath = const Value.absent(),
            Value<DateTime> updatedAt = const Value.absent(),
            Value<DateTime> createdAt = const Value.absent(),
          }) =>
              ArticleImagesCompanion.insert(
            id: id,
            article: article,
            imageUrl: imageUrl,
            imagePath: imagePath,
            updatedAt: updatedAt,
            createdAt: createdAt,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable(table),
                    $$ArticleImagesTableReferences(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: ({article = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins: <
                  T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic>>(state) {
                if (article) {
                  state = state.withJoin(
                    currentTable: table,
                    currentColumn: table.article,
                    referencedTable:
                        $$ArticleImagesTableReferences._articleTable(db),
                    referencedColumn:
                        $$ArticleImagesTableReferences._articleTable(db).id,
                  ) as T;
                }

                return state;
              },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ));
}

typedef $$ArticleImagesTableProcessedTableManager = ProcessedTableManager<
    _$AppDatabase,
    $ArticleImagesTable,
    ArticleImage,
    $$ArticleImagesTableFilterComposer,
    $$ArticleImagesTableOrderingComposer,
    $$ArticleImagesTableAnnotationComposer,
    $$ArticleImagesTableCreateCompanionBuilder,
    $$ArticleImagesTableUpdateCompanionBuilder,
    (ArticleImage, $$ArticleImagesTableReferences),
    ArticleImage,
    PrefetchHooks Function({bool article})>;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$ArticlesTableTableManager get articles =>
      $$ArticlesTableTableManager(_db, _db.articles);
  $$SettingsTableTableManager get settings =>
      $$SettingsTableTableManager(_db, _db.settings);
  $$ArticleImagesTableTableManager get articleImages =>
      $$ArticleImagesTableTableManager(_db, _db.articleImages);
}
