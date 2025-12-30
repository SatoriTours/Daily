// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'share_dialog_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$ShareDialogControllerState {

/// 分享URL
 String get shareURL;/// 是否是更新模式
 bool get isUpdate;/// 是否从剪切板来的
 bool get fromClipboard;/// 文章ID
 int get articleID;/// 文章标题
 String get articleTitle;/// 文章标签
 String get articleTags;/// 标签列表
 List<String> get tagList;/// 是否重新抓取并AI分析
 bool get refreshAndAnalyze;/// 标题是否编辑过
 bool get titleEdited;
/// Create a copy of ShareDialogControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ShareDialogControllerStateCopyWith<ShareDialogControllerState> get copyWith => _$ShareDialogControllerStateCopyWithImpl<ShareDialogControllerState>(this as ShareDialogControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ShareDialogControllerState&&(identical(other.shareURL, shareURL) || other.shareURL == shareURL)&&(identical(other.isUpdate, isUpdate) || other.isUpdate == isUpdate)&&(identical(other.fromClipboard, fromClipboard) || other.fromClipboard == fromClipboard)&&(identical(other.articleID, articleID) || other.articleID == articleID)&&(identical(other.articleTitle, articleTitle) || other.articleTitle == articleTitle)&&(identical(other.articleTags, articleTags) || other.articleTags == articleTags)&&const DeepCollectionEquality().equals(other.tagList, tagList)&&(identical(other.refreshAndAnalyze, refreshAndAnalyze) || other.refreshAndAnalyze == refreshAndAnalyze)&&(identical(other.titleEdited, titleEdited) || other.titleEdited == titleEdited));
}


@override
int get hashCode => Object.hash(runtimeType,shareURL,isUpdate,fromClipboard,articleID,articleTitle,articleTags,const DeepCollectionEquality().hash(tagList),refreshAndAnalyze,titleEdited);

@override
String toString() {
  return 'ShareDialogControllerState(shareURL: $shareURL, isUpdate: $isUpdate, fromClipboard: $fromClipboard, articleID: $articleID, articleTitle: $articleTitle, articleTags: $articleTags, tagList: $tagList, refreshAndAnalyze: $refreshAndAnalyze, titleEdited: $titleEdited)';
}


}

/// @nodoc
abstract mixin class $ShareDialogControllerStateCopyWith<$Res>  {
  factory $ShareDialogControllerStateCopyWith(ShareDialogControllerState value, $Res Function(ShareDialogControllerState) _then) = _$ShareDialogControllerStateCopyWithImpl;
@useResult
$Res call({
 String shareURL, bool isUpdate, bool fromClipboard, int articleID, String articleTitle, String articleTags, List<String> tagList, bool refreshAndAnalyze, bool titleEdited
});




}
/// @nodoc
class _$ShareDialogControllerStateCopyWithImpl<$Res>
    implements $ShareDialogControllerStateCopyWith<$Res> {
  _$ShareDialogControllerStateCopyWithImpl(this._self, this._then);

  final ShareDialogControllerState _self;
  final $Res Function(ShareDialogControllerState) _then;

/// Create a copy of ShareDialogControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? shareURL = null,Object? isUpdate = null,Object? fromClipboard = null,Object? articleID = null,Object? articleTitle = null,Object? articleTags = null,Object? tagList = null,Object? refreshAndAnalyze = null,Object? titleEdited = null,}) {
  return _then(_self.copyWith(
shareURL: null == shareURL ? _self.shareURL : shareURL // ignore: cast_nullable_to_non_nullable
as String,isUpdate: null == isUpdate ? _self.isUpdate : isUpdate // ignore: cast_nullable_to_non_nullable
as bool,fromClipboard: null == fromClipboard ? _self.fromClipboard : fromClipboard // ignore: cast_nullable_to_non_nullable
as bool,articleID: null == articleID ? _self.articleID : articleID // ignore: cast_nullable_to_non_nullable
as int,articleTitle: null == articleTitle ? _self.articleTitle : articleTitle // ignore: cast_nullable_to_non_nullable
as String,articleTags: null == articleTags ? _self.articleTags : articleTags // ignore: cast_nullable_to_non_nullable
as String,tagList: null == tagList ? _self.tagList : tagList // ignore: cast_nullable_to_non_nullable
as List<String>,refreshAndAnalyze: null == refreshAndAnalyze ? _self.refreshAndAnalyze : refreshAndAnalyze // ignore: cast_nullable_to_non_nullable
as bool,titleEdited: null == titleEdited ? _self.titleEdited : titleEdited // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}

}


/// Adds pattern-matching-related methods to [ShareDialogControllerState].
extension ShareDialogControllerStatePatterns on ShareDialogControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _ShareDialogControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _ShareDialogControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _ShareDialogControllerState value)  $default,){
final _that = this;
switch (_that) {
case _ShareDialogControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _ShareDialogControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _ShareDialogControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( String shareURL,  bool isUpdate,  bool fromClipboard,  int articleID,  String articleTitle,  String articleTags,  List<String> tagList,  bool refreshAndAnalyze,  bool titleEdited)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _ShareDialogControllerState() when $default != null:
return $default(_that.shareURL,_that.isUpdate,_that.fromClipboard,_that.articleID,_that.articleTitle,_that.articleTags,_that.tagList,_that.refreshAndAnalyze,_that.titleEdited);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( String shareURL,  bool isUpdate,  bool fromClipboard,  int articleID,  String articleTitle,  String articleTags,  List<String> tagList,  bool refreshAndAnalyze,  bool titleEdited)  $default,) {final _that = this;
switch (_that) {
case _ShareDialogControllerState():
return $default(_that.shareURL,_that.isUpdate,_that.fromClipboard,_that.articleID,_that.articleTitle,_that.articleTags,_that.tagList,_that.refreshAndAnalyze,_that.titleEdited);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( String shareURL,  bool isUpdate,  bool fromClipboard,  int articleID,  String articleTitle,  String articleTags,  List<String> tagList,  bool refreshAndAnalyze,  bool titleEdited)?  $default,) {final _that = this;
switch (_that) {
case _ShareDialogControllerState() when $default != null:
return $default(_that.shareURL,_that.isUpdate,_that.fromClipboard,_that.articleID,_that.articleTitle,_that.articleTags,_that.tagList,_that.refreshAndAnalyze,_that.titleEdited);case _:
  return null;

}
}

}

/// @nodoc


class _ShareDialogControllerState implements ShareDialogControllerState {
  const _ShareDialogControllerState({this.shareURL = '', this.isUpdate = false, this.fromClipboard = false, this.articleID = 0, this.articleTitle = '', this.articleTags = '', final  List<String> tagList = const [], this.refreshAndAnalyze = true, this.titleEdited = false}): _tagList = tagList;
  

/// 分享URL
@override@JsonKey() final  String shareURL;
/// 是否是更新模式
@override@JsonKey() final  bool isUpdate;
/// 是否从剪切板来的
@override@JsonKey() final  bool fromClipboard;
/// 文章ID
@override@JsonKey() final  int articleID;
/// 文章标题
@override@JsonKey() final  String articleTitle;
/// 文章标签
@override@JsonKey() final  String articleTags;
/// 标签列表
 final  List<String> _tagList;
/// 标签列表
@override@JsonKey() List<String> get tagList {
  if (_tagList is EqualUnmodifiableListView) return _tagList;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_tagList);
}

/// 是否重新抓取并AI分析
@override@JsonKey() final  bool refreshAndAnalyze;
/// 标题是否编辑过
@override@JsonKey() final  bool titleEdited;

/// Create a copy of ShareDialogControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$ShareDialogControllerStateCopyWith<_ShareDialogControllerState> get copyWith => __$ShareDialogControllerStateCopyWithImpl<_ShareDialogControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _ShareDialogControllerState&&(identical(other.shareURL, shareURL) || other.shareURL == shareURL)&&(identical(other.isUpdate, isUpdate) || other.isUpdate == isUpdate)&&(identical(other.fromClipboard, fromClipboard) || other.fromClipboard == fromClipboard)&&(identical(other.articleID, articleID) || other.articleID == articleID)&&(identical(other.articleTitle, articleTitle) || other.articleTitle == articleTitle)&&(identical(other.articleTags, articleTags) || other.articleTags == articleTags)&&const DeepCollectionEquality().equals(other._tagList, _tagList)&&(identical(other.refreshAndAnalyze, refreshAndAnalyze) || other.refreshAndAnalyze == refreshAndAnalyze)&&(identical(other.titleEdited, titleEdited) || other.titleEdited == titleEdited));
}


@override
int get hashCode => Object.hash(runtimeType,shareURL,isUpdate,fromClipboard,articleID,articleTitle,articleTags,const DeepCollectionEquality().hash(_tagList),refreshAndAnalyze,titleEdited);

@override
String toString() {
  return 'ShareDialogControllerState(shareURL: $shareURL, isUpdate: $isUpdate, fromClipboard: $fromClipboard, articleID: $articleID, articleTitle: $articleTitle, articleTags: $articleTags, tagList: $tagList, refreshAndAnalyze: $refreshAndAnalyze, titleEdited: $titleEdited)';
}


}

/// @nodoc
abstract mixin class _$ShareDialogControllerStateCopyWith<$Res> implements $ShareDialogControllerStateCopyWith<$Res> {
  factory _$ShareDialogControllerStateCopyWith(_ShareDialogControllerState value, $Res Function(_ShareDialogControllerState) _then) = __$ShareDialogControllerStateCopyWithImpl;
@override @useResult
$Res call({
 String shareURL, bool isUpdate, bool fromClipboard, int articleID, String articleTitle, String articleTags, List<String> tagList, bool refreshAndAnalyze, bool titleEdited
});




}
/// @nodoc
class __$ShareDialogControllerStateCopyWithImpl<$Res>
    implements _$ShareDialogControllerStateCopyWith<$Res> {
  __$ShareDialogControllerStateCopyWithImpl(this._self, this._then);

  final _ShareDialogControllerState _self;
  final $Res Function(_ShareDialogControllerState) _then;

/// Create a copy of ShareDialogControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? shareURL = null,Object? isUpdate = null,Object? fromClipboard = null,Object? articleID = null,Object? articleTitle = null,Object? articleTags = null,Object? tagList = null,Object? refreshAndAnalyze = null,Object? titleEdited = null,}) {
  return _then(_ShareDialogControllerState(
shareURL: null == shareURL ? _self.shareURL : shareURL // ignore: cast_nullable_to_non_nullable
as String,isUpdate: null == isUpdate ? _self.isUpdate : isUpdate // ignore: cast_nullable_to_non_nullable
as bool,fromClipboard: null == fromClipboard ? _self.fromClipboard : fromClipboard // ignore: cast_nullable_to_non_nullable
as bool,articleID: null == articleID ? _self.articleID : articleID // ignore: cast_nullable_to_non_nullable
as int,articleTitle: null == articleTitle ? _self.articleTitle : articleTitle // ignore: cast_nullable_to_non_nullable
as String,articleTags: null == articleTags ? _self.articleTags : articleTags // ignore: cast_nullable_to_non_nullable
as String,tagList: null == tagList ? _self._tagList : tagList // ignore: cast_nullable_to_non_nullable
as List<String>,refreshAndAnalyze: null == refreshAndAnalyze ? _self.refreshAndAnalyze : refreshAndAnalyze // ignore: cast_nullable_to_non_nullable
as bool,titleEdited: null == titleEdited ? _self.titleEdited : titleEdited // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}


}

// dart format on
