// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'left_bar_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$LeftBarControllerState {

/// 标签列表是否展开
 bool get isTagsExpanded;/// 所有标签列表
 List<TagModel> get tags;
/// Create a copy of LeftBarControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$LeftBarControllerStateCopyWith<LeftBarControllerState> get copyWith => _$LeftBarControllerStateCopyWithImpl<LeftBarControllerState>(this as LeftBarControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is LeftBarControllerState&&(identical(other.isTagsExpanded, isTagsExpanded) || other.isTagsExpanded == isTagsExpanded)&&const DeepCollectionEquality().equals(other.tags, tags));
}


@override
int get hashCode => Object.hash(runtimeType,isTagsExpanded,const DeepCollectionEquality().hash(tags));

@override
String toString() {
  return 'LeftBarControllerState(isTagsExpanded: $isTagsExpanded, tags: $tags)';
}


}

/// @nodoc
abstract mixin class $LeftBarControllerStateCopyWith<$Res>  {
  factory $LeftBarControllerStateCopyWith(LeftBarControllerState value, $Res Function(LeftBarControllerState) _then) = _$LeftBarControllerStateCopyWithImpl;
@useResult
$Res call({
 bool isTagsExpanded, List<TagModel> tags
});




}
/// @nodoc
class _$LeftBarControllerStateCopyWithImpl<$Res>
    implements $LeftBarControllerStateCopyWith<$Res> {
  _$LeftBarControllerStateCopyWithImpl(this._self, this._then);

  final LeftBarControllerState _self;
  final $Res Function(LeftBarControllerState) _then;

/// Create a copy of LeftBarControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? isTagsExpanded = null,Object? tags = null,}) {
  return _then(_self.copyWith(
isTagsExpanded: null == isTagsExpanded ? _self.isTagsExpanded : isTagsExpanded // ignore: cast_nullable_to_non_nullable
as bool,tags: null == tags ? _self.tags : tags // ignore: cast_nullable_to_non_nullable
as List<TagModel>,
  ));
}

}


/// Adds pattern-matching-related methods to [LeftBarControllerState].
extension LeftBarControllerStatePatterns on LeftBarControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _LeftBarControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _LeftBarControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _LeftBarControllerState value)  $default,){
final _that = this;
switch (_that) {
case _LeftBarControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _LeftBarControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _LeftBarControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool isTagsExpanded,  List<TagModel> tags)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _LeftBarControllerState() when $default != null:
return $default(_that.isTagsExpanded,_that.tags);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool isTagsExpanded,  List<TagModel> tags)  $default,) {final _that = this;
switch (_that) {
case _LeftBarControllerState():
return $default(_that.isTagsExpanded,_that.tags);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool isTagsExpanded,  List<TagModel> tags)?  $default,) {final _that = this;
switch (_that) {
case _LeftBarControllerState() when $default != null:
return $default(_that.isTagsExpanded,_that.tags);case _:
  return null;

}
}

}

/// @nodoc


class _LeftBarControllerState implements LeftBarControllerState {
  const _LeftBarControllerState({this.isTagsExpanded = true, required final  List<TagModel> tags}): _tags = tags;
  

/// 标签列表是否展开
@override@JsonKey() final  bool isTagsExpanded;
/// 所有标签列表
 final  List<TagModel> _tags;
/// 所有标签列表
@override List<TagModel> get tags {
  if (_tags is EqualUnmodifiableListView) return _tags;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_tags);
}


/// Create a copy of LeftBarControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$LeftBarControllerStateCopyWith<_LeftBarControllerState> get copyWith => __$LeftBarControllerStateCopyWithImpl<_LeftBarControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _LeftBarControllerState&&(identical(other.isTagsExpanded, isTagsExpanded) || other.isTagsExpanded == isTagsExpanded)&&const DeepCollectionEquality().equals(other._tags, _tags));
}


@override
int get hashCode => Object.hash(runtimeType,isTagsExpanded,const DeepCollectionEquality().hash(_tags));

@override
String toString() {
  return 'LeftBarControllerState(isTagsExpanded: $isTagsExpanded, tags: $tags)';
}


}

/// @nodoc
abstract mixin class _$LeftBarControllerStateCopyWith<$Res> implements $LeftBarControllerStateCopyWith<$Res> {
  factory _$LeftBarControllerStateCopyWith(_LeftBarControllerState value, $Res Function(_LeftBarControllerState) _then) = __$LeftBarControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool isTagsExpanded, List<TagModel> tags
});




}
/// @nodoc
class __$LeftBarControllerStateCopyWithImpl<$Res>
    implements _$LeftBarControllerStateCopyWith<$Res> {
  __$LeftBarControllerStateCopyWithImpl(this._self, this._then);

  final _LeftBarControllerState _self;
  final $Res Function(_LeftBarControllerState) _then;

/// Create a copy of LeftBarControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? isTagsExpanded = null,Object? tags = null,}) {
  return _then(_LeftBarControllerState(
isTagsExpanded: null == isTagsExpanded ? _self.isTagsExpanded : isTagsExpanded // ignore: cast_nullable_to_non_nullable
as bool,tags: null == tags ? _self._tags : tags // ignore: cast_nullable_to_non_nullable
as List<TagModel>,
  ));
}


}

// dart format on
