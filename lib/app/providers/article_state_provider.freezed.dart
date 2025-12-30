// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'article_state_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$ArticleUpdateEvent {





@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleUpdateEvent);
}


@override
int get hashCode => runtimeType.hashCode;

@override
String toString() {
  return 'ArticleUpdateEvent()';
}


}

/// @nodoc
class $ArticleUpdateEventCopyWith<$Res>  {
$ArticleUpdateEventCopyWith(ArticleUpdateEvent _, $Res Function(ArticleUpdateEvent) __);
}


/// Adds pattern-matching-related methods to [ArticleUpdateEvent].
extension ArticleUpdateEventPatterns on ArticleUpdateEvent {
/// A variant of `map` that fallback to returning `orElse`.
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case final Subclass value:
///     return ...;
///   case _:
///     return orElse();
/// }
/// ```

@optionalTypeArgs TResult maybeMap<TResult extends Object?>({TResult Function( ArticleUpdateEventNone value)?  none,TResult Function( ArticleUpdateEventCreated value)?  created,TResult Function( ArticleUpdateEventUpdated value)?  updated,TResult Function( ArticleUpdateEventDeleted value)?  deleted,required TResult orElse(),}){
final _that = this;
switch (_that) {
case ArticleUpdateEventNone() when none != null:
return none(_that);case ArticleUpdateEventCreated() when created != null:
return created(_that);case ArticleUpdateEventUpdated() when updated != null:
return updated(_that);case ArticleUpdateEventDeleted() when deleted != null:
return deleted(_that);case _:
  return orElse();

}
}
/// A `switch`-like method, using callbacks.
///
/// Callbacks receives the raw object, upcasted.
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case final Subclass value:
///     return ...;
///   case final Subclass2 value:
///     return ...;
/// }
/// ```

@optionalTypeArgs TResult map<TResult extends Object?>({required TResult Function( ArticleUpdateEventNone value)  none,required TResult Function( ArticleUpdateEventCreated value)  created,required TResult Function( ArticleUpdateEventUpdated value)  updated,required TResult Function( ArticleUpdateEventDeleted value)  deleted,}){
final _that = this;
switch (_that) {
case ArticleUpdateEventNone():
return none(_that);case ArticleUpdateEventCreated():
return created(_that);case ArticleUpdateEventUpdated():
return updated(_that);case ArticleUpdateEventDeleted():
return deleted(_that);case _:
  throw StateError('Unexpected subclass');

}
}
/// A variant of `map` that fallback to returning `null`.
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case final Subclass value:
///     return ...;
///   case _:
///     return null;
/// }
/// ```

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>({TResult? Function( ArticleUpdateEventNone value)?  none,TResult? Function( ArticleUpdateEventCreated value)?  created,TResult? Function( ArticleUpdateEventUpdated value)?  updated,TResult? Function( ArticleUpdateEventDeleted value)?  deleted,}){
final _that = this;
switch (_that) {
case ArticleUpdateEventNone() when none != null:
return none(_that);case ArticleUpdateEventCreated() when created != null:
return created(_that);case ArticleUpdateEventUpdated() when updated != null:
return updated(_that);case ArticleUpdateEventDeleted() when deleted != null:
return deleted(_that);case _:
  return null;

}
}
/// A variant of `when` that fallback to an `orElse` callback.
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case Subclass(:final field):
///     return ...;
///   case _:
///     return orElse();
/// }
/// ```

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>({TResult Function()?  none,TResult Function( ArticleModel article)?  created,TResult Function( ArticleModel article)?  updated,TResult Function( int articleId)?  deleted,required TResult orElse(),}) {final _that = this;
switch (_that) {
case ArticleUpdateEventNone() when none != null:
return none();case ArticleUpdateEventCreated() when created != null:
return created(_that.article);case ArticleUpdateEventUpdated() when updated != null:
return updated(_that.article);case ArticleUpdateEventDeleted() when deleted != null:
return deleted(_that.articleId);case _:
  return orElse();

}
}
/// A `switch`-like method, using callbacks.
///
/// As opposed to `map`, this offers destructuring.
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case Subclass(:final field):
///     return ...;
///   case Subclass2(:final field2):
///     return ...;
/// }
/// ```

@optionalTypeArgs TResult when<TResult extends Object?>({required TResult Function()  none,required TResult Function( ArticleModel article)  created,required TResult Function( ArticleModel article)  updated,required TResult Function( int articleId)  deleted,}) {final _that = this;
switch (_that) {
case ArticleUpdateEventNone():
return none();case ArticleUpdateEventCreated():
return created(_that.article);case ArticleUpdateEventUpdated():
return updated(_that.article);case ArticleUpdateEventDeleted():
return deleted(_that.articleId);case _:
  throw StateError('Unexpected subclass');

}
}
/// A variant of `when` that fallback to returning `null`
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case Subclass(:final field):
///     return ...;
///   case _:
///     return null;
/// }
/// ```

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>({TResult? Function()?  none,TResult? Function( ArticleModel article)?  created,TResult? Function( ArticleModel article)?  updated,TResult? Function( int articleId)?  deleted,}) {final _that = this;
switch (_that) {
case ArticleUpdateEventNone() when none != null:
return none();case ArticleUpdateEventCreated() when created != null:
return created(_that.article);case ArticleUpdateEventUpdated() when updated != null:
return updated(_that.article);case ArticleUpdateEventDeleted() when deleted != null:
return deleted(_that.articleId);case _:
  return null;

}
}

}

/// @nodoc


class ArticleUpdateEventNone implements ArticleUpdateEvent {
  const ArticleUpdateEventNone();
  






@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleUpdateEventNone);
}


@override
int get hashCode => runtimeType.hashCode;

@override
String toString() {
  return 'ArticleUpdateEvent.none()';
}


}




/// @nodoc


class ArticleUpdateEventCreated implements ArticleUpdateEvent {
  const ArticleUpdateEventCreated(this.article);
  

 final  ArticleModel article;

/// Create a copy of ArticleUpdateEvent
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ArticleUpdateEventCreatedCopyWith<ArticleUpdateEventCreated> get copyWith => _$ArticleUpdateEventCreatedCopyWithImpl<ArticleUpdateEventCreated>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleUpdateEventCreated&&(identical(other.article, article) || other.article == article));
}


@override
int get hashCode => Object.hash(runtimeType,article);

@override
String toString() {
  return 'ArticleUpdateEvent.created(article: $article)';
}


}

/// @nodoc
abstract mixin class $ArticleUpdateEventCreatedCopyWith<$Res> implements $ArticleUpdateEventCopyWith<$Res> {
  factory $ArticleUpdateEventCreatedCopyWith(ArticleUpdateEventCreated value, $Res Function(ArticleUpdateEventCreated) _then) = _$ArticleUpdateEventCreatedCopyWithImpl;
@useResult
$Res call({
 ArticleModel article
});




}
/// @nodoc
class _$ArticleUpdateEventCreatedCopyWithImpl<$Res>
    implements $ArticleUpdateEventCreatedCopyWith<$Res> {
  _$ArticleUpdateEventCreatedCopyWithImpl(this._self, this._then);

  final ArticleUpdateEventCreated _self;
  final $Res Function(ArticleUpdateEventCreated) _then;

/// Create a copy of ArticleUpdateEvent
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') $Res call({Object? article = null,}) {
  return _then(ArticleUpdateEventCreated(
null == article ? _self.article : article // ignore: cast_nullable_to_non_nullable
as ArticleModel,
  ));
}


}

/// @nodoc


class ArticleUpdateEventUpdated implements ArticleUpdateEvent {
  const ArticleUpdateEventUpdated(this.article);
  

 final  ArticleModel article;

/// Create a copy of ArticleUpdateEvent
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ArticleUpdateEventUpdatedCopyWith<ArticleUpdateEventUpdated> get copyWith => _$ArticleUpdateEventUpdatedCopyWithImpl<ArticleUpdateEventUpdated>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleUpdateEventUpdated&&(identical(other.article, article) || other.article == article));
}


@override
int get hashCode => Object.hash(runtimeType,article);

@override
String toString() {
  return 'ArticleUpdateEvent.updated(article: $article)';
}


}

/// @nodoc
abstract mixin class $ArticleUpdateEventUpdatedCopyWith<$Res> implements $ArticleUpdateEventCopyWith<$Res> {
  factory $ArticleUpdateEventUpdatedCopyWith(ArticleUpdateEventUpdated value, $Res Function(ArticleUpdateEventUpdated) _then) = _$ArticleUpdateEventUpdatedCopyWithImpl;
@useResult
$Res call({
 ArticleModel article
});




}
/// @nodoc
class _$ArticleUpdateEventUpdatedCopyWithImpl<$Res>
    implements $ArticleUpdateEventUpdatedCopyWith<$Res> {
  _$ArticleUpdateEventUpdatedCopyWithImpl(this._self, this._then);

  final ArticleUpdateEventUpdated _self;
  final $Res Function(ArticleUpdateEventUpdated) _then;

/// Create a copy of ArticleUpdateEvent
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') $Res call({Object? article = null,}) {
  return _then(ArticleUpdateEventUpdated(
null == article ? _self.article : article // ignore: cast_nullable_to_non_nullable
as ArticleModel,
  ));
}


}

/// @nodoc


class ArticleUpdateEventDeleted implements ArticleUpdateEvent {
  const ArticleUpdateEventDeleted(this.articleId);
  

 final  int articleId;

/// Create a copy of ArticleUpdateEvent
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ArticleUpdateEventDeletedCopyWith<ArticleUpdateEventDeleted> get copyWith => _$ArticleUpdateEventDeletedCopyWithImpl<ArticleUpdateEventDeleted>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleUpdateEventDeleted&&(identical(other.articleId, articleId) || other.articleId == articleId));
}


@override
int get hashCode => Object.hash(runtimeType,articleId);

@override
String toString() {
  return 'ArticleUpdateEvent.deleted(articleId: $articleId)';
}


}

/// @nodoc
abstract mixin class $ArticleUpdateEventDeletedCopyWith<$Res> implements $ArticleUpdateEventCopyWith<$Res> {
  factory $ArticleUpdateEventDeletedCopyWith(ArticleUpdateEventDeleted value, $Res Function(ArticleUpdateEventDeleted) _then) = _$ArticleUpdateEventDeletedCopyWithImpl;
@useResult
$Res call({
 int articleId
});




}
/// @nodoc
class _$ArticleUpdateEventDeletedCopyWithImpl<$Res>
    implements $ArticleUpdateEventDeletedCopyWith<$Res> {
  _$ArticleUpdateEventDeletedCopyWithImpl(this._self, this._then);

  final ArticleUpdateEventDeleted _self;
  final $Res Function(ArticleUpdateEventDeleted) _then;

/// Create a copy of ArticleUpdateEvent
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') $Res call({Object? articleId = null,}) {
  return _then(ArticleUpdateEventDeleted(
null == articleId ? _self.articleId : articleId // ignore: cast_nullable_to_non_nullable
as int,
  ));
}


}

/// @nodoc
mixin _$ArticleStateModel {

 List<ArticleModel> get articles; bool get isLoading; int get activeArticleId; ArticleModel? get activeArticle; ArticleUpdateEvent get articleUpdateEvent; String get globalSearchQuery; bool get isGlobalSearchActive;
/// Create a copy of ArticleStateModel
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ArticleStateModelCopyWith<ArticleStateModel> get copyWith => _$ArticleStateModelCopyWithImpl<ArticleStateModel>(this as ArticleStateModel, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleStateModel&&const DeepCollectionEquality().equals(other.articles, articles)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.activeArticleId, activeArticleId) || other.activeArticleId == activeArticleId)&&(identical(other.activeArticle, activeArticle) || other.activeArticle == activeArticle)&&(identical(other.articleUpdateEvent, articleUpdateEvent) || other.articleUpdateEvent == articleUpdateEvent)&&(identical(other.globalSearchQuery, globalSearchQuery) || other.globalSearchQuery == globalSearchQuery)&&(identical(other.isGlobalSearchActive, isGlobalSearchActive) || other.isGlobalSearchActive == isGlobalSearchActive));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(articles),isLoading,activeArticleId,activeArticle,articleUpdateEvent,globalSearchQuery,isGlobalSearchActive);

@override
String toString() {
  return 'ArticleStateModel(articles: $articles, isLoading: $isLoading, activeArticleId: $activeArticleId, activeArticle: $activeArticle, articleUpdateEvent: $articleUpdateEvent, globalSearchQuery: $globalSearchQuery, isGlobalSearchActive: $isGlobalSearchActive)';
}


}

/// @nodoc
abstract mixin class $ArticleStateModelCopyWith<$Res>  {
  factory $ArticleStateModelCopyWith(ArticleStateModel value, $Res Function(ArticleStateModel) _then) = _$ArticleStateModelCopyWithImpl;
@useResult
$Res call({
 List<ArticleModel> articles, bool isLoading, int activeArticleId, ArticleModel? activeArticle, ArticleUpdateEvent articleUpdateEvent, String globalSearchQuery, bool isGlobalSearchActive
});


$ArticleUpdateEventCopyWith<$Res> get articleUpdateEvent;

}
/// @nodoc
class _$ArticleStateModelCopyWithImpl<$Res>
    implements $ArticleStateModelCopyWith<$Res> {
  _$ArticleStateModelCopyWithImpl(this._self, this._then);

  final ArticleStateModel _self;
  final $Res Function(ArticleStateModel) _then;

/// Create a copy of ArticleStateModel
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? articles = null,Object? isLoading = null,Object? activeArticleId = null,Object? activeArticle = freezed,Object? articleUpdateEvent = null,Object? globalSearchQuery = null,Object? isGlobalSearchActive = null,}) {
  return _then(_self.copyWith(
articles: null == articles ? _self.articles : articles // ignore: cast_nullable_to_non_nullable
as List<ArticleModel>,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,activeArticleId: null == activeArticleId ? _self.activeArticleId : activeArticleId // ignore: cast_nullable_to_non_nullable
as int,activeArticle: freezed == activeArticle ? _self.activeArticle : activeArticle // ignore: cast_nullable_to_non_nullable
as ArticleModel?,articleUpdateEvent: null == articleUpdateEvent ? _self.articleUpdateEvent : articleUpdateEvent // ignore: cast_nullable_to_non_nullable
as ArticleUpdateEvent,globalSearchQuery: null == globalSearchQuery ? _self.globalSearchQuery : globalSearchQuery // ignore: cast_nullable_to_non_nullable
as String,isGlobalSearchActive: null == isGlobalSearchActive ? _self.isGlobalSearchActive : isGlobalSearchActive // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}
/// Create a copy of ArticleStateModel
/// with the given fields replaced by the non-null parameter values.
@override
@pragma('vm:prefer-inline')
$ArticleUpdateEventCopyWith<$Res> get articleUpdateEvent {
  
  return $ArticleUpdateEventCopyWith<$Res>(_self.articleUpdateEvent, (value) {
    return _then(_self.copyWith(articleUpdateEvent: value));
  });
}
}


/// Adds pattern-matching-related methods to [ArticleStateModel].
extension ArticleStateModelPatterns on ArticleStateModel {
/// A variant of `map` that fallback to returning `orElse`.
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case final Subclass value:
///     return ...;
///   case _:
///     return orElse();
/// }
/// ```

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _ArticleStateModel value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _ArticleStateModel() when $default != null:
return $default(_that);case _:
  return orElse();

}
}
/// A `switch`-like method, using callbacks.
///
/// Callbacks receives the raw object, upcasted.
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case final Subclass value:
///     return ...;
///   case final Subclass2 value:
///     return ...;
/// }
/// ```

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _ArticleStateModel value)  $default,){
final _that = this;
switch (_that) {
case _ArticleStateModel():
return $default(_that);case _:
  throw StateError('Unexpected subclass');

}
}
/// A variant of `map` that fallback to returning `null`.
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case final Subclass value:
///     return ...;
///   case _:
///     return null;
/// }
/// ```

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _ArticleStateModel value)?  $default,){
final _that = this;
switch (_that) {
case _ArticleStateModel() when $default != null:
return $default(_that);case _:
  return null;

}
}
/// A variant of `when` that fallback to an `orElse` callback.
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case Subclass(:final field):
///     return ...;
///   case _:
///     return orElse();
/// }
/// ```

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( List<ArticleModel> articles,  bool isLoading,  int activeArticleId,  ArticleModel? activeArticle,  ArticleUpdateEvent articleUpdateEvent,  String globalSearchQuery,  bool isGlobalSearchActive)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _ArticleStateModel() when $default != null:
return $default(_that.articles,_that.isLoading,_that.activeArticleId,_that.activeArticle,_that.articleUpdateEvent,_that.globalSearchQuery,_that.isGlobalSearchActive);case _:
  return orElse();

}
}
/// A `switch`-like method, using callbacks.
///
/// As opposed to `map`, this offers destructuring.
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case Subclass(:final field):
///     return ...;
///   case Subclass2(:final field2):
///     return ...;
/// }
/// ```

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( List<ArticleModel> articles,  bool isLoading,  int activeArticleId,  ArticleModel? activeArticle,  ArticleUpdateEvent articleUpdateEvent,  String globalSearchQuery,  bool isGlobalSearchActive)  $default,) {final _that = this;
switch (_that) {
case _ArticleStateModel():
return $default(_that.articles,_that.isLoading,_that.activeArticleId,_that.activeArticle,_that.articleUpdateEvent,_that.globalSearchQuery,_that.isGlobalSearchActive);case _:
  throw StateError('Unexpected subclass');

}
}
/// A variant of `when` that fallback to returning `null`
///
/// It is equivalent to doing:
/// ```dart
/// switch (sealedClass) {
///   case Subclass(:final field):
///     return ...;
///   case _:
///     return null;
/// }
/// ```

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( List<ArticleModel> articles,  bool isLoading,  int activeArticleId,  ArticleModel? activeArticle,  ArticleUpdateEvent articleUpdateEvent,  String globalSearchQuery,  bool isGlobalSearchActive)?  $default,) {final _that = this;
switch (_that) {
case _ArticleStateModel() when $default != null:
return $default(_that.articles,_that.isLoading,_that.activeArticleId,_that.activeArticle,_that.articleUpdateEvent,_that.globalSearchQuery,_that.isGlobalSearchActive);case _:
  return null;

}
}

}

/// @nodoc


class _ArticleStateModel extends ArticleStateModel {
  const _ArticleStateModel({final  List<ArticleModel> articles = const [], this.isLoading = false, this.activeArticleId = -1, this.activeArticle, this.articleUpdateEvent = const ArticleUpdateEvent.none(), this.globalSearchQuery = '', this.isGlobalSearchActive = false}): _articles = articles,super._();
  

 final  List<ArticleModel> _articles;
@override@JsonKey() List<ArticleModel> get articles {
  if (_articles is EqualUnmodifiableListView) return _articles;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_articles);
}

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  int activeArticleId;
@override final  ArticleModel? activeArticle;
@override@JsonKey() final  ArticleUpdateEvent articleUpdateEvent;
@override@JsonKey() final  String globalSearchQuery;
@override@JsonKey() final  bool isGlobalSearchActive;

/// Create a copy of ArticleStateModel
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$ArticleStateModelCopyWith<_ArticleStateModel> get copyWith => __$ArticleStateModelCopyWithImpl<_ArticleStateModel>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _ArticleStateModel&&const DeepCollectionEquality().equals(other._articles, _articles)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.activeArticleId, activeArticleId) || other.activeArticleId == activeArticleId)&&(identical(other.activeArticle, activeArticle) || other.activeArticle == activeArticle)&&(identical(other.articleUpdateEvent, articleUpdateEvent) || other.articleUpdateEvent == articleUpdateEvent)&&(identical(other.globalSearchQuery, globalSearchQuery) || other.globalSearchQuery == globalSearchQuery)&&(identical(other.isGlobalSearchActive, isGlobalSearchActive) || other.isGlobalSearchActive == isGlobalSearchActive));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(_articles),isLoading,activeArticleId,activeArticle,articleUpdateEvent,globalSearchQuery,isGlobalSearchActive);

@override
String toString() {
  return 'ArticleStateModel(articles: $articles, isLoading: $isLoading, activeArticleId: $activeArticleId, activeArticle: $activeArticle, articleUpdateEvent: $articleUpdateEvent, globalSearchQuery: $globalSearchQuery, isGlobalSearchActive: $isGlobalSearchActive)';
}


}

/// @nodoc
abstract mixin class _$ArticleStateModelCopyWith<$Res> implements $ArticleStateModelCopyWith<$Res> {
  factory _$ArticleStateModelCopyWith(_ArticleStateModel value, $Res Function(_ArticleStateModel) _then) = __$ArticleStateModelCopyWithImpl;
@override @useResult
$Res call({
 List<ArticleModel> articles, bool isLoading, int activeArticleId, ArticleModel? activeArticle, ArticleUpdateEvent articleUpdateEvent, String globalSearchQuery, bool isGlobalSearchActive
});


@override $ArticleUpdateEventCopyWith<$Res> get articleUpdateEvent;

}
/// @nodoc
class __$ArticleStateModelCopyWithImpl<$Res>
    implements _$ArticleStateModelCopyWith<$Res> {
  __$ArticleStateModelCopyWithImpl(this._self, this._then);

  final _ArticleStateModel _self;
  final $Res Function(_ArticleStateModel) _then;

/// Create a copy of ArticleStateModel
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? articles = null,Object? isLoading = null,Object? activeArticleId = null,Object? activeArticle = freezed,Object? articleUpdateEvent = null,Object? globalSearchQuery = null,Object? isGlobalSearchActive = null,}) {
  return _then(_ArticleStateModel(
articles: null == articles ? _self._articles : articles // ignore: cast_nullable_to_non_nullable
as List<ArticleModel>,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,activeArticleId: null == activeArticleId ? _self.activeArticleId : activeArticleId // ignore: cast_nullable_to_non_nullable
as int,activeArticle: freezed == activeArticle ? _self.activeArticle : activeArticle // ignore: cast_nullable_to_non_nullable
as ArticleModel?,articleUpdateEvent: null == articleUpdateEvent ? _self.articleUpdateEvent : articleUpdateEvent // ignore: cast_nullable_to_non_nullable
as ArticleUpdateEvent,globalSearchQuery: null == globalSearchQuery ? _self.globalSearchQuery : globalSearchQuery // ignore: cast_nullable_to_non_nullable
as String,isGlobalSearchActive: null == isGlobalSearchActive ? _self.isGlobalSearchActive : isGlobalSearchActive // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}

/// Create a copy of ArticleStateModel
/// with the given fields replaced by the non-null parameter values.
@override
@pragma('vm:prefer-inline')
$ArticleUpdateEventCopyWith<$Res> get articleUpdateEvent {
  
  return $ArticleUpdateEventCopyWith<$Res>(_self.articleUpdateEvent, (value) {
    return _then(_self.copyWith(articleUpdateEvent: value));
  });
}
}

// dart format on
