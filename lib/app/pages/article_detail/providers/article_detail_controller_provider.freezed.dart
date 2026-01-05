// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'article_detail_controller_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$ArticleDetailControllerState {

/// 当前文章模型
 ArticleModel? get articleModel;/// 文章标签字符串,以逗号分隔
 String get tags;
/// Create a copy of ArticleDetailControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ArticleDetailControllerStateCopyWith<ArticleDetailControllerState> get copyWith => _$ArticleDetailControllerStateCopyWithImpl<ArticleDetailControllerState>(this as ArticleDetailControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticleDetailControllerState&&(identical(other.articleModel, articleModel) || other.articleModel == articleModel)&&(identical(other.tags, tags) || other.tags == tags));
}


@override
int get hashCode => Object.hash(runtimeType,articleModel,tags);

@override
String toString() {
  return 'ArticleDetailControllerState(articleModel: $articleModel, tags: $tags)';
}


}

/// @nodoc
abstract mixin class $ArticleDetailControllerStateCopyWith<$Res>  {
  factory $ArticleDetailControllerStateCopyWith(ArticleDetailControllerState value, $Res Function(ArticleDetailControllerState) _then) = _$ArticleDetailControllerStateCopyWithImpl;
@useResult
$Res call({
 ArticleModel? articleModel, String tags
});




}
/// @nodoc
class _$ArticleDetailControllerStateCopyWithImpl<$Res>
    implements $ArticleDetailControllerStateCopyWith<$Res> {
  _$ArticleDetailControllerStateCopyWithImpl(this._self, this._then);

  final ArticleDetailControllerState _self;
  final $Res Function(ArticleDetailControllerState) _then;

/// Create a copy of ArticleDetailControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? articleModel = freezed,Object? tags = null,}) {
  return _then(_self.copyWith(
articleModel: freezed == articleModel ? _self.articleModel : articleModel // ignore: cast_nullable_to_non_nullable
as ArticleModel?,tags: null == tags ? _self.tags : tags // ignore: cast_nullable_to_non_nullable
as String,
  ));
}

}


/// Adds pattern-matching-related methods to [ArticleDetailControllerState].
extension ArticleDetailControllerStatePatterns on ArticleDetailControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _ArticleDetailControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _ArticleDetailControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _ArticleDetailControllerState value)  $default,){
final _that = this;
switch (_that) {
case _ArticleDetailControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _ArticleDetailControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _ArticleDetailControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( ArticleModel? articleModel,  String tags)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _ArticleDetailControllerState() when $default != null:
return $default(_that.articleModel,_that.tags);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( ArticleModel? articleModel,  String tags)  $default,) {final _that = this;
switch (_that) {
case _ArticleDetailControllerState():
return $default(_that.articleModel,_that.tags);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( ArticleModel? articleModel,  String tags)?  $default,) {final _that = this;
switch (_that) {
case _ArticleDetailControllerState() when $default != null:
return $default(_that.articleModel,_that.tags);case _:
  return null;

}
}

}

/// @nodoc


class _ArticleDetailControllerState extends ArticleDetailControllerState {
  const _ArticleDetailControllerState({this.articleModel, this.tags = ''}): super._();
  

/// 当前文章模型
@override final  ArticleModel? articleModel;
/// 文章标签字符串,以逗号分隔
@override@JsonKey() final  String tags;

/// Create a copy of ArticleDetailControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$ArticleDetailControllerStateCopyWith<_ArticleDetailControllerState> get copyWith => __$ArticleDetailControllerStateCopyWithImpl<_ArticleDetailControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _ArticleDetailControllerState&&(identical(other.articleModel, articleModel) || other.articleModel == articleModel)&&(identical(other.tags, tags) || other.tags == tags));
}


@override
int get hashCode => Object.hash(runtimeType,articleModel,tags);

@override
String toString() {
  return 'ArticleDetailControllerState(articleModel: $articleModel, tags: $tags)';
}


}

/// @nodoc
abstract mixin class _$ArticleDetailControllerStateCopyWith<$Res> implements $ArticleDetailControllerStateCopyWith<$Res> {
  factory _$ArticleDetailControllerStateCopyWith(_ArticleDetailControllerState value, $Res Function(_ArticleDetailControllerState) _then) = __$ArticleDetailControllerStateCopyWithImpl;
@override @useResult
$Res call({
 ArticleModel? articleModel, String tags
});




}
/// @nodoc
class __$ArticleDetailControllerStateCopyWithImpl<$Res>
    implements _$ArticleDetailControllerStateCopyWith<$Res> {
  __$ArticleDetailControllerStateCopyWithImpl(this._self, this._then);

  final _ArticleDetailControllerState _self;
  final $Res Function(_ArticleDetailControllerState) _then;

/// Create a copy of ArticleDetailControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? articleModel = freezed,Object? tags = null,}) {
  return _then(_ArticleDetailControllerState(
articleModel: freezed == articleModel ? _self.articleModel : articleModel // ignore: cast_nullable_to_non_nullable
as ArticleModel?,tags: null == tags ? _self.tags : tags // ignore: cast_nullable_to_non_nullable
as String,
  ));
}


}

// dart format on
