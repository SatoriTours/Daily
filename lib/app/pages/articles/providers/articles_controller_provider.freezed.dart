// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'articles_controller_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$ArticlesControllerState {

/// 是否只显示收藏文章
 bool get onlyFavorite;/// 标签ID（-1表示未选择）
 int get tagId;/// 标签名称
 String get tagName;/// 选中的过滤日期
 DateTime? get selectedFilterDate;/// 最后刷新时间
 DateTime? get lastRefreshTime;/// ScrollController
 ScrollController? get scrollController;/// TextEditingController
 TextEditingController? get searchController;/// FocusNode
 FocusNode? get searchFocusNode;
/// Create a copy of ArticlesControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$ArticlesControllerStateCopyWith<ArticlesControllerState> get copyWith => _$ArticlesControllerStateCopyWithImpl<ArticlesControllerState>(this as ArticlesControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is ArticlesControllerState&&(identical(other.onlyFavorite, onlyFavorite) || other.onlyFavorite == onlyFavorite)&&(identical(other.tagId, tagId) || other.tagId == tagId)&&(identical(other.tagName, tagName) || other.tagName == tagName)&&(identical(other.selectedFilterDate, selectedFilterDate) || other.selectedFilterDate == selectedFilterDate)&&(identical(other.lastRefreshTime, lastRefreshTime) || other.lastRefreshTime == lastRefreshTime)&&(identical(other.scrollController, scrollController) || other.scrollController == scrollController)&&(identical(other.searchController, searchController) || other.searchController == searchController)&&(identical(other.searchFocusNode, searchFocusNode) || other.searchFocusNode == searchFocusNode));
}


@override
int get hashCode => Object.hash(runtimeType,onlyFavorite,tagId,tagName,selectedFilterDate,lastRefreshTime,scrollController,searchController,searchFocusNode);

@override
String toString() {
  return 'ArticlesControllerState(onlyFavorite: $onlyFavorite, tagId: $tagId, tagName: $tagName, selectedFilterDate: $selectedFilterDate, lastRefreshTime: $lastRefreshTime, scrollController: $scrollController, searchController: $searchController, searchFocusNode: $searchFocusNode)';
}


}

/// @nodoc
abstract mixin class $ArticlesControllerStateCopyWith<$Res>  {
  factory $ArticlesControllerStateCopyWith(ArticlesControllerState value, $Res Function(ArticlesControllerState) _then) = _$ArticlesControllerStateCopyWithImpl;
@useResult
$Res call({
 bool onlyFavorite, int tagId, String tagName, DateTime? selectedFilterDate, DateTime? lastRefreshTime, ScrollController? scrollController, TextEditingController? searchController, FocusNode? searchFocusNode
});




}
/// @nodoc
class _$ArticlesControllerStateCopyWithImpl<$Res>
    implements $ArticlesControllerStateCopyWith<$Res> {
  _$ArticlesControllerStateCopyWithImpl(this._self, this._then);

  final ArticlesControllerState _self;
  final $Res Function(ArticlesControllerState) _then;

/// Create a copy of ArticlesControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? onlyFavorite = null,Object? tagId = null,Object? tagName = null,Object? selectedFilterDate = freezed,Object? lastRefreshTime = freezed,Object? scrollController = freezed,Object? searchController = freezed,Object? searchFocusNode = freezed,}) {
  return _then(_self.copyWith(
onlyFavorite: null == onlyFavorite ? _self.onlyFavorite : onlyFavorite // ignore: cast_nullable_to_non_nullable
as bool,tagId: null == tagId ? _self.tagId : tagId // ignore: cast_nullable_to_non_nullable
as int,tagName: null == tagName ? _self.tagName : tagName // ignore: cast_nullable_to_non_nullable
as String,selectedFilterDate: freezed == selectedFilterDate ? _self.selectedFilterDate : selectedFilterDate // ignore: cast_nullable_to_non_nullable
as DateTime?,lastRefreshTime: freezed == lastRefreshTime ? _self.lastRefreshTime : lastRefreshTime // ignore: cast_nullable_to_non_nullable
as DateTime?,scrollController: freezed == scrollController ? _self.scrollController : scrollController // ignore: cast_nullable_to_non_nullable
as ScrollController?,searchController: freezed == searchController ? _self.searchController : searchController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,searchFocusNode: freezed == searchFocusNode ? _self.searchFocusNode : searchFocusNode // ignore: cast_nullable_to_non_nullable
as FocusNode?,
  ));
}

}


/// Adds pattern-matching-related methods to [ArticlesControllerState].
extension ArticlesControllerStatePatterns on ArticlesControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _ArticlesControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _ArticlesControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _ArticlesControllerState value)  $default,){
final _that = this;
switch (_that) {
case _ArticlesControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _ArticlesControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _ArticlesControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool onlyFavorite,  int tagId,  String tagName,  DateTime? selectedFilterDate,  DateTime? lastRefreshTime,  ScrollController? scrollController,  TextEditingController? searchController,  FocusNode? searchFocusNode)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _ArticlesControllerState() when $default != null:
return $default(_that.onlyFavorite,_that.tagId,_that.tagName,_that.selectedFilterDate,_that.lastRefreshTime,_that.scrollController,_that.searchController,_that.searchFocusNode);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool onlyFavorite,  int tagId,  String tagName,  DateTime? selectedFilterDate,  DateTime? lastRefreshTime,  ScrollController? scrollController,  TextEditingController? searchController,  FocusNode? searchFocusNode)  $default,) {final _that = this;
switch (_that) {
case _ArticlesControllerState():
return $default(_that.onlyFavorite,_that.tagId,_that.tagName,_that.selectedFilterDate,_that.lastRefreshTime,_that.scrollController,_that.searchController,_that.searchFocusNode);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool onlyFavorite,  int tagId,  String tagName,  DateTime? selectedFilterDate,  DateTime? lastRefreshTime,  ScrollController? scrollController,  TextEditingController? searchController,  FocusNode? searchFocusNode)?  $default,) {final _that = this;
switch (_that) {
case _ArticlesControllerState() when $default != null:
return $default(_that.onlyFavorite,_that.tagId,_that.tagName,_that.selectedFilterDate,_that.lastRefreshTime,_that.scrollController,_that.searchController,_that.searchFocusNode);case _:
  return null;

}
}

}

/// @nodoc


class _ArticlesControllerState extends ArticlesControllerState {
  const _ArticlesControllerState({this.onlyFavorite = false, this.tagId = -1, this.tagName = '', this.selectedFilterDate, this.lastRefreshTime, this.scrollController, this.searchController, this.searchFocusNode}): super._();
  

/// 是否只显示收藏文章
@override@JsonKey() final  bool onlyFavorite;
/// 标签ID（-1表示未选择）
@override@JsonKey() final  int tagId;
/// 标签名称
@override@JsonKey() final  String tagName;
/// 选中的过滤日期
@override final  DateTime? selectedFilterDate;
/// 最后刷新时间
@override final  DateTime? lastRefreshTime;
/// ScrollController
@override final  ScrollController? scrollController;
/// TextEditingController
@override final  TextEditingController? searchController;
/// FocusNode
@override final  FocusNode? searchFocusNode;

/// Create a copy of ArticlesControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$ArticlesControllerStateCopyWith<_ArticlesControllerState> get copyWith => __$ArticlesControllerStateCopyWithImpl<_ArticlesControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _ArticlesControllerState&&(identical(other.onlyFavorite, onlyFavorite) || other.onlyFavorite == onlyFavorite)&&(identical(other.tagId, tagId) || other.tagId == tagId)&&(identical(other.tagName, tagName) || other.tagName == tagName)&&(identical(other.selectedFilterDate, selectedFilterDate) || other.selectedFilterDate == selectedFilterDate)&&(identical(other.lastRefreshTime, lastRefreshTime) || other.lastRefreshTime == lastRefreshTime)&&(identical(other.scrollController, scrollController) || other.scrollController == scrollController)&&(identical(other.searchController, searchController) || other.searchController == searchController)&&(identical(other.searchFocusNode, searchFocusNode) || other.searchFocusNode == searchFocusNode));
}


@override
int get hashCode => Object.hash(runtimeType,onlyFavorite,tagId,tagName,selectedFilterDate,lastRefreshTime,scrollController,searchController,searchFocusNode);

@override
String toString() {
  return 'ArticlesControllerState(onlyFavorite: $onlyFavorite, tagId: $tagId, tagName: $tagName, selectedFilterDate: $selectedFilterDate, lastRefreshTime: $lastRefreshTime, scrollController: $scrollController, searchController: $searchController, searchFocusNode: $searchFocusNode)';
}


}

/// @nodoc
abstract mixin class _$ArticlesControllerStateCopyWith<$Res> implements $ArticlesControllerStateCopyWith<$Res> {
  factory _$ArticlesControllerStateCopyWith(_ArticlesControllerState value, $Res Function(_ArticlesControllerState) _then) = __$ArticlesControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool onlyFavorite, int tagId, String tagName, DateTime? selectedFilterDate, DateTime? lastRefreshTime, ScrollController? scrollController, TextEditingController? searchController, FocusNode? searchFocusNode
});




}
/// @nodoc
class __$ArticlesControllerStateCopyWithImpl<$Res>
    implements _$ArticlesControllerStateCopyWith<$Res> {
  __$ArticlesControllerStateCopyWithImpl(this._self, this._then);

  final _ArticlesControllerState _self;
  final $Res Function(_ArticlesControllerState) _then;

/// Create a copy of ArticlesControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? onlyFavorite = null,Object? tagId = null,Object? tagName = null,Object? selectedFilterDate = freezed,Object? lastRefreshTime = freezed,Object? scrollController = freezed,Object? searchController = freezed,Object? searchFocusNode = freezed,}) {
  return _then(_ArticlesControllerState(
onlyFavorite: null == onlyFavorite ? _self.onlyFavorite : onlyFavorite // ignore: cast_nullable_to_non_nullable
as bool,tagId: null == tagId ? _self.tagId : tagId // ignore: cast_nullable_to_non_nullable
as int,tagName: null == tagName ? _self.tagName : tagName // ignore: cast_nullable_to_non_nullable
as String,selectedFilterDate: freezed == selectedFilterDate ? _self.selectedFilterDate : selectedFilterDate // ignore: cast_nullable_to_non_nullable
as DateTime?,lastRefreshTime: freezed == lastRefreshTime ? _self.lastRefreshTime : lastRefreshTime // ignore: cast_nullable_to_non_nullable
as DateTime?,scrollController: freezed == scrollController ? _self.scrollController : scrollController // ignore: cast_nullable_to_non_nullable
as ScrollController?,searchController: freezed == searchController ? _self.searchController : searchController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,searchFocusNode: freezed == searchFocusNode ? _self.searchFocusNode : searchFocusNode // ignore: cast_nullable_to_non_nullable
as FocusNode?,
  ));
}


}

// dart format on
