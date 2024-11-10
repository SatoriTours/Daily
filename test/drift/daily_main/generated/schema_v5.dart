// GENERATED CODE, DO NOT EDIT BY HAND.
// ignore_for_file: type=lint
//@dart=2.12
import 'package:drift/drift.dart';

class Articles extends Table with TableInfo<Articles, ArticlesData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  Articles(this.attachedDatabase, [this._alias]);
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
      'id', aliasedName, false,
      hasAutoIncrement: true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('PRIMARY KEY AUTOINCREMENT'));
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
      'title', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> aiTitle = GeneratedColumn<String>(
      'ai_title', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> content = GeneratedColumn<String>(
      'content', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> aiContent = GeneratedColumn<String>(
      'ai_content', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> htmlContent = GeneratedColumn<String>(
      'html_content', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> url = GeneratedColumn<String>(
      'url', aliasedName, false,
      additionalChecks:
          GeneratedColumn.checkTextLength(minTextLength: 1, maxTextLength: 255),
      type: DriftSqlType.string,
      requiredDuringInsert: true,
      defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'));
  late final GeneratedColumn<String> imageUrl = GeneratedColumn<String>(
      'image_url', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> imagePath = GeneratedColumn<String>(
      'image_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> screenshotPath = GeneratedColumn<String>(
      'screenshot_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<bool> isFavorite = GeneratedColumn<bool>(
      'is_favorite', aliasedName, false,
      type: DriftSqlType.bool,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('CHECK ("is_favorite" IN (0, 1))'),
      defaultValue: const Constant(false));
  late final GeneratedColumn<DateTime> pubDate = GeneratedColumn<DateTime>(
      'pub_date', aliasedName, true,
      type: DriftSqlType.dateTime, requiredDuringInsert: false);
  late final GeneratedColumn<String> comment = GeneratedColumn<String>(
      'comment', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
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
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ArticlesData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArticlesData(
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
  Articles createAlias(String alias) {
    return Articles(attachedDatabase, alias);
  }
}

class ArticlesData extends DataClass implements Insertable<ArticlesData> {
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
  const ArticlesData(
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

  factory ArticlesData.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArticlesData(
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

  ArticlesData copyWith(
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
      ArticlesData(
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
  ArticlesData copyWithCompanion(ArticlesCompanion data) {
    return ArticlesData(
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
    return (StringBuffer('ArticlesData(')
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
      (other is ArticlesData &&
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

class ArticlesCompanion extends UpdateCompanion<ArticlesData> {
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
  static Insertable<ArticlesData> custom({
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

class Settings extends Table with TableInfo<Settings, SettingsData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  Settings(this.attachedDatabase, [this._alias]);
  late final GeneratedColumn<String> key = GeneratedColumn<String>(
      'key', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  late final GeneratedColumn<String> value = GeneratedColumn<String>(
      'value', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
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
  Set<GeneratedColumn> get $primaryKey => {key};
  @override
  SettingsData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SettingsData(
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
  Settings createAlias(String alias) {
    return Settings(attachedDatabase, alias);
  }
}

class SettingsData extends DataClass implements Insertable<SettingsData> {
  final String key;
  final String value;
  final DateTime updatedAt;
  final DateTime createdAt;
  const SettingsData(
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

  factory SettingsData.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SettingsData(
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

  SettingsData copyWith(
          {String? key,
          String? value,
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      SettingsData(
        key: key ?? this.key,
        value: value ?? this.value,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  SettingsData copyWithCompanion(SettingsCompanion data) {
    return SettingsData(
      key: data.key.present ? data.key.value : this.key,
      value: data.value.present ? data.value.value : this.value,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SettingsData(')
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
      (other is SettingsData &&
          other.key == this.key &&
          other.value == this.value &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class SettingsCompanion extends UpdateCompanion<SettingsData> {
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
  static Insertable<SettingsData> custom({
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

class ArticleImages extends Table
    with TableInfo<ArticleImages, ArticleImagesData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  ArticleImages(this.attachedDatabase, [this._alias]);
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
      'id', aliasedName, false,
      hasAutoIncrement: true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('PRIMARY KEY AUTOINCREMENT'));
  late final GeneratedColumn<int> article = GeneratedColumn<int>(
      'article', aliasedName, true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('REFERENCES articles (id)'));
  late final GeneratedColumn<String> imageUrl = GeneratedColumn<String>(
      'image_url', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> imagePath = GeneratedColumn<String>(
      'image_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
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
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ArticleImagesData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArticleImagesData(
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
  ArticleImages createAlias(String alias) {
    return ArticleImages(attachedDatabase, alias);
  }
}

class ArticleImagesData extends DataClass
    implements Insertable<ArticleImagesData> {
  final int id;
  final int? article;
  final String? imageUrl;
  final String? imagePath;
  final DateTime updatedAt;
  final DateTime createdAt;
  const ArticleImagesData(
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

  factory ArticleImagesData.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArticleImagesData(
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

  ArticleImagesData copyWith(
          {int? id,
          Value<int?> article = const Value.absent(),
          Value<String?> imageUrl = const Value.absent(),
          Value<String?> imagePath = const Value.absent(),
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      ArticleImagesData(
        id: id ?? this.id,
        article: article.present ? article.value : this.article,
        imageUrl: imageUrl.present ? imageUrl.value : this.imageUrl,
        imagePath: imagePath.present ? imagePath.value : this.imagePath,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  ArticleImagesData copyWithCompanion(ArticleImagesCompanion data) {
    return ArticleImagesData(
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
    return (StringBuffer('ArticleImagesData(')
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
      (other is ArticleImagesData &&
          other.id == this.id &&
          other.article == this.article &&
          other.imageUrl == this.imageUrl &&
          other.imagePath == this.imagePath &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class ArticleImagesCompanion extends UpdateCompanion<ArticleImagesData> {
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
  static Insertable<ArticleImagesData> custom({
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

class ArticleScreenshots extends Table
    with TableInfo<ArticleScreenshots, ArticleScreenshotsData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  ArticleScreenshots(this.attachedDatabase, [this._alias]);
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
      'id', aliasedName, false,
      hasAutoIncrement: true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('PRIMARY KEY AUTOINCREMENT'));
  late final GeneratedColumn<int> article = GeneratedColumn<int>(
      'article', aliasedName, true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('REFERENCES articles (id)'));
  late final GeneratedColumn<String> imagePath = GeneratedColumn<String>(
      'image_path', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
      'created_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  @override
  List<GeneratedColumn> get $columns =>
      [id, article, imagePath, updatedAt, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'article_screenshots';
  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ArticleScreenshotsData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArticleScreenshotsData(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}id'])!,
      article: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}article']),
      imagePath: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}image_path']),
      updatedAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}updated_at'])!,
      createdAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}created_at'])!,
    );
  }

  @override
  ArticleScreenshots createAlias(String alias) {
    return ArticleScreenshots(attachedDatabase, alias);
  }
}

class ArticleScreenshotsData extends DataClass
    implements Insertable<ArticleScreenshotsData> {
  final int id;
  final int? article;
  final String? imagePath;
  final DateTime updatedAt;
  final DateTime createdAt;
  const ArticleScreenshotsData(
      {required this.id,
      this.article,
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
    if (!nullToAbsent || imagePath != null) {
      map['image_path'] = Variable<String>(imagePath);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ArticleScreenshotsCompanion toCompanion(bool nullToAbsent) {
    return ArticleScreenshotsCompanion(
      id: Value(id),
      article: article == null && nullToAbsent
          ? const Value.absent()
          : Value(article),
      imagePath: imagePath == null && nullToAbsent
          ? const Value.absent()
          : Value(imagePath),
      updatedAt: Value(updatedAt),
      createdAt: Value(createdAt),
    );
  }

  factory ArticleScreenshotsData.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArticleScreenshotsData(
      id: serializer.fromJson<int>(json['id']),
      article: serializer.fromJson<int?>(json['article']),
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
      'imagePath': serializer.toJson<String?>(imagePath),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  ArticleScreenshotsData copyWith(
          {int? id,
          Value<int?> article = const Value.absent(),
          Value<String?> imagePath = const Value.absent(),
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      ArticleScreenshotsData(
        id: id ?? this.id,
        article: article.present ? article.value : this.article,
        imagePath: imagePath.present ? imagePath.value : this.imagePath,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  ArticleScreenshotsData copyWithCompanion(ArticleScreenshotsCompanion data) {
    return ArticleScreenshotsData(
      id: data.id.present ? data.id.value : this.id,
      article: data.article.present ? data.article.value : this.article,
      imagePath: data.imagePath.present ? data.imagePath.value : this.imagePath,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ArticleScreenshotsData(')
          ..write('id: $id, ')
          ..write('article: $article, ')
          ..write('imagePath: $imagePath, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, article, imagePath, updatedAt, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ArticleScreenshotsData &&
          other.id == this.id &&
          other.article == this.article &&
          other.imagePath == this.imagePath &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class ArticleScreenshotsCompanion
    extends UpdateCompanion<ArticleScreenshotsData> {
  final Value<int> id;
  final Value<int?> article;
  final Value<String?> imagePath;
  final Value<DateTime> updatedAt;
  final Value<DateTime> createdAt;
  const ArticleScreenshotsCompanion({
    this.id = const Value.absent(),
    this.article = const Value.absent(),
    this.imagePath = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  ArticleScreenshotsCompanion.insert({
    this.id = const Value.absent(),
    this.article = const Value.absent(),
    this.imagePath = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  static Insertable<ArticleScreenshotsData> custom({
    Expression<int>? id,
    Expression<int>? article,
    Expression<String>? imagePath,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (article != null) 'article': article,
      if (imagePath != null) 'image_path': imagePath,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  ArticleScreenshotsCompanion copyWith(
      {Value<int>? id,
      Value<int?>? article,
      Value<String?>? imagePath,
      Value<DateTime>? updatedAt,
      Value<DateTime>? createdAt}) {
    return ArticleScreenshotsCompanion(
      id: id ?? this.id,
      article: article ?? this.article,
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
    return (StringBuffer('ArticleScreenshotsCompanion(')
          ..write('id: $id, ')
          ..write('article: $article, ')
          ..write('imagePath: $imagePath, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class Tags extends Table with TableInfo<Tags, TagsData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  Tags(this.attachedDatabase, [this._alias]);
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
      'id', aliasedName, false,
      hasAutoIncrement: true,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultConstraints:
          GeneratedColumn.constraintIsAlways('PRIMARY KEY AUTOINCREMENT'));
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
      'title', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<String> icon = GeneratedColumn<String>(
      'icon', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
      'created_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  @override
  List<GeneratedColumn> get $columns => [id, title, icon, updatedAt, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'tags';
  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  TagsData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TagsData(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}id'])!,
      title: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}title']),
      icon: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}icon']),
      updatedAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}updated_at'])!,
      createdAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}created_at'])!,
    );
  }

  @override
  Tags createAlias(String alias) {
    return Tags(attachedDatabase, alias);
  }
}

class TagsData extends DataClass implements Insertable<TagsData> {
  final int id;
  final String? title;
  final String? icon;
  final DateTime updatedAt;
  final DateTime createdAt;
  const TagsData(
      {required this.id,
      this.title,
      this.icon,
      required this.updatedAt,
      required this.createdAt});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || title != null) {
      map['title'] = Variable<String>(title);
    }
    if (!nullToAbsent || icon != null) {
      map['icon'] = Variable<String>(icon);
    }
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  TagsCompanion toCompanion(bool nullToAbsent) {
    return TagsCompanion(
      id: Value(id),
      title:
          title == null && nullToAbsent ? const Value.absent() : Value(title),
      icon: icon == null && nullToAbsent ? const Value.absent() : Value(icon),
      updatedAt: Value(updatedAt),
      createdAt: Value(createdAt),
    );
  }

  factory TagsData.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TagsData(
      id: serializer.fromJson<int>(json['id']),
      title: serializer.fromJson<String?>(json['title']),
      icon: serializer.fromJson<String?>(json['icon']),
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
      'icon': serializer.toJson<String?>(icon),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  TagsData copyWith(
          {int? id,
          Value<String?> title = const Value.absent(),
          Value<String?> icon = const Value.absent(),
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      TagsData(
        id: id ?? this.id,
        title: title.present ? title.value : this.title,
        icon: icon.present ? icon.value : this.icon,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  TagsData copyWithCompanion(TagsCompanion data) {
    return TagsData(
      id: data.id.present ? data.id.value : this.id,
      title: data.title.present ? data.title.value : this.title,
      icon: data.icon.present ? data.icon.value : this.icon,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TagsData(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('icon: $icon, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, title, icon, updatedAt, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TagsData &&
          other.id == this.id &&
          other.title == this.title &&
          other.icon == this.icon &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class TagsCompanion extends UpdateCompanion<TagsData> {
  final Value<int> id;
  final Value<String?> title;
  final Value<String?> icon;
  final Value<DateTime> updatedAt;
  final Value<DateTime> createdAt;
  const TagsCompanion({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.icon = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  TagsCompanion.insert({
    this.id = const Value.absent(),
    this.title = const Value.absent(),
    this.icon = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  static Insertable<TagsData> custom({
    Expression<int>? id,
    Expression<String>? title,
    Expression<String>? icon,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (title != null) 'title': title,
      if (icon != null) 'icon': icon,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  TagsCompanion copyWith(
      {Value<int>? id,
      Value<String?>? title,
      Value<String?>? icon,
      Value<DateTime>? updatedAt,
      Value<DateTime>? createdAt}) {
    return TagsCompanion(
      id: id ?? this.id,
      title: title ?? this.title,
      icon: icon ?? this.icon,
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
    if (icon.present) {
      map['icon'] = Variable<String>(icon.value);
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
    return (StringBuffer('TagsCompanion(')
          ..write('id: $id, ')
          ..write('title: $title, ')
          ..write('icon: $icon, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class ArticleTags extends Table with TableInfo<ArticleTags, ArticleTagsData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  ArticleTags(this.attachedDatabase, [this._alias]);
  late final GeneratedColumn<int> articleId = GeneratedColumn<int>(
      'article_id', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  late final GeneratedColumn<int> tagId = GeneratedColumn<int>(
      'tag_id', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
      'updated_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
      'created_at', aliasedName, false,
      type: DriftSqlType.dateTime,
      requiredDuringInsert: false,
      defaultValue: currentDateAndTime);
  @override
  List<GeneratedColumn> get $columns =>
      [articleId, tagId, updatedAt, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'article_tags';
  @override
  Set<GeneratedColumn> get $primaryKey => const {};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
        {articleId, tagId},
      ];
  @override
  ArticleTagsData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ArticleTagsData(
      articleId: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}article_id'])!,
      tagId: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}tag_id'])!,
      updatedAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}updated_at'])!,
      createdAt: attachedDatabase.typeMapping
          .read(DriftSqlType.dateTime, data['${effectivePrefix}created_at'])!,
    );
  }

  @override
  ArticleTags createAlias(String alias) {
    return ArticleTags(attachedDatabase, alias);
  }
}

class ArticleTagsData extends DataClass implements Insertable<ArticleTagsData> {
  final int articleId;
  final int tagId;
  final DateTime updatedAt;
  final DateTime createdAt;
  const ArticleTagsData(
      {required this.articleId,
      required this.tagId,
      required this.updatedAt,
      required this.createdAt});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['article_id'] = Variable<int>(articleId);
    map['tag_id'] = Variable<int>(tagId);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ArticleTagsCompanion toCompanion(bool nullToAbsent) {
    return ArticleTagsCompanion(
      articleId: Value(articleId),
      tagId: Value(tagId),
      updatedAt: Value(updatedAt),
      createdAt: Value(createdAt),
    );
  }

  factory ArticleTagsData.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ArticleTagsData(
      articleId: serializer.fromJson<int>(json['articleId']),
      tagId: serializer.fromJson<int>(json['tagId']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'articleId': serializer.toJson<int>(articleId),
      'tagId': serializer.toJson<int>(tagId),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  ArticleTagsData copyWith(
          {int? articleId,
          int? tagId,
          DateTime? updatedAt,
          DateTime? createdAt}) =>
      ArticleTagsData(
        articleId: articleId ?? this.articleId,
        tagId: tagId ?? this.tagId,
        updatedAt: updatedAt ?? this.updatedAt,
        createdAt: createdAt ?? this.createdAt,
      );
  ArticleTagsData copyWithCompanion(ArticleTagsCompanion data) {
    return ArticleTagsData(
      articleId: data.articleId.present ? data.articleId.value : this.articleId,
      tagId: data.tagId.present ? data.tagId.value : this.tagId,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ArticleTagsData(')
          ..write('articleId: $articleId, ')
          ..write('tagId: $tagId, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(articleId, tagId, updatedAt, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ArticleTagsData &&
          other.articleId == this.articleId &&
          other.tagId == this.tagId &&
          other.updatedAt == this.updatedAt &&
          other.createdAt == this.createdAt);
}

class ArticleTagsCompanion extends UpdateCompanion<ArticleTagsData> {
  final Value<int> articleId;
  final Value<int> tagId;
  final Value<DateTime> updatedAt;
  final Value<DateTime> createdAt;
  final Value<int> rowid;
  const ArticleTagsCompanion({
    this.articleId = const Value.absent(),
    this.tagId = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ArticleTagsCompanion.insert({
    required int articleId,
    required int tagId,
    this.updatedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.rowid = const Value.absent(),
  })  : articleId = Value(articleId),
        tagId = Value(tagId);
  static Insertable<ArticleTagsData> custom({
    Expression<int>? articleId,
    Expression<int>? tagId,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? createdAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (articleId != null) 'article_id': articleId,
      if (tagId != null) 'tag_id': tagId,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (createdAt != null) 'created_at': createdAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ArticleTagsCompanion copyWith(
      {Value<int>? articleId,
      Value<int>? tagId,
      Value<DateTime>? updatedAt,
      Value<DateTime>? createdAt,
      Value<int>? rowid}) {
    return ArticleTagsCompanion(
      articleId: articleId ?? this.articleId,
      tagId: tagId ?? this.tagId,
      updatedAt: updatedAt ?? this.updatedAt,
      createdAt: createdAt ?? this.createdAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (articleId.present) {
      map['article_id'] = Variable<int>(articleId.value);
    }
    if (tagId.present) {
      map['tag_id'] = Variable<int>(tagId.value);
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
    return (StringBuffer('ArticleTagsCompanion(')
          ..write('articleId: $articleId, ')
          ..write('tagId: $tagId, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class DatabaseAtV5 extends GeneratedDatabase {
  DatabaseAtV5(QueryExecutor e) : super(e);
  late final Articles articles = Articles(this);
  late final Settings settings = Settings(this);
  late final ArticleImages articleImages = ArticleImages(this);
  late final ArticleScreenshots articleScreenshots = ArticleScreenshots(this);
  late final Tags tags = Tags(this);
  late final ArticleTags articleTags = ArticleTags(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
        articles,
        settings,
        articleImages,
        articleScreenshots,
        tags,
        articleTags
      ];
  @override
  int get schemaVersion => 5;
}
