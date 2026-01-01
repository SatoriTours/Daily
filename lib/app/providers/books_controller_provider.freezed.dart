// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'books_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;

/// @nodoc
mixin _$BooksControllerState {

/// 最后刷新时间
 DateTime? get lastRefreshTime;/// PageController (不在freezed中管理)
// ignore: invalid_annotation_target
@JsonKey(includeToJson: false, includeFromJson: false) PageController? get pageController;/// TextEditingController for content (不在freezed中管理)
// ignore: invalid_annotation_target
@JsonKey(includeToJson: false, includeFromJson: false) TextEditingController? get contentController;
/// Create a copy of BooksControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$BooksControllerStateCopyWith<BooksControllerState> get copyWith => _$BooksControllerStateCopyWithImpl<BooksControllerState>(this as BooksControllerState, _$identity);

  /// Serializes this BooksControllerState to a JSON map.
  Map<String, dynamic> toJson();


@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is BooksControllerState&&(identical(other.lastRefreshTime, lastRefreshTime) || other.lastRefreshTime == lastRefreshTime)&&(identical(other.pageController, pageController) || other.pageController == pageController)&&(identical(other.contentController, contentController) || other.contentController == contentController));
}

@JsonKey(includeFromJson: false, includeToJson: false)
@override
int get hashCode => Object.hash(runtimeType,lastRefreshTime,pageController,contentController);

@override
String toString() {
  return 'BooksControllerState(lastRefreshTime: $lastRefreshTime, pageController: $pageController, contentController: $contentController)';
}


}

/// @nodoc
abstract mixin class $BooksControllerStateCopyWith<$Res>  {
  factory $BooksControllerStateCopyWith(BooksControllerState value, $Res Function(BooksControllerState) _then) = _$BooksControllerStateCopyWithImpl;
@useResult
$Res call({
 DateTime? lastRefreshTime,@JsonKey(includeToJson: false, includeFromJson: false) PageController? pageController,@JsonKey(includeToJson: false, includeFromJson: false) TextEditingController? contentController
});




}
/// @nodoc
class _$BooksControllerStateCopyWithImpl<$Res>
    implements $BooksControllerStateCopyWith<$Res> {
  _$BooksControllerStateCopyWithImpl(this._self, this._then);

  final BooksControllerState _self;
  final $Res Function(BooksControllerState) _then;

/// Create a copy of BooksControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? lastRefreshTime = freezed,Object? pageController = freezed,Object? contentController = freezed,}) {
  return _then(_self.copyWith(
lastRefreshTime: freezed == lastRefreshTime ? _self.lastRefreshTime : lastRefreshTime // ignore: cast_nullable_to_non_nullable
as DateTime?,pageController: freezed == pageController ? _self.pageController : pageController // ignore: cast_nullable_to_non_nullable
as PageController?,contentController: freezed == contentController ? _self.contentController : contentController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,
  ));
}

}


/// Adds pattern-matching-related methods to [BooksControllerState].
extension BooksControllerStatePatterns on BooksControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _BooksControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _BooksControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _BooksControllerState value)  $default,){
final _that = this;
switch (_that) {
case _BooksControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _BooksControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _BooksControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( DateTime? lastRefreshTime, @JsonKey(includeToJson: false, includeFromJson: false)  PageController? pageController, @JsonKey(includeToJson: false, includeFromJson: false)  TextEditingController? contentController)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _BooksControllerState() when $default != null:
return $default(_that.lastRefreshTime,_that.pageController,_that.contentController);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( DateTime? lastRefreshTime, @JsonKey(includeToJson: false, includeFromJson: false)  PageController? pageController, @JsonKey(includeToJson: false, includeFromJson: false)  TextEditingController? contentController)  $default,) {final _that = this;
switch (_that) {
case _BooksControllerState():
return $default(_that.lastRefreshTime,_that.pageController,_that.contentController);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( DateTime? lastRefreshTime, @JsonKey(includeToJson: false, includeFromJson: false)  PageController? pageController, @JsonKey(includeToJson: false, includeFromJson: false)  TextEditingController? contentController)?  $default,) {final _that = this;
switch (_that) {
case _BooksControllerState() when $default != null:
return $default(_that.lastRefreshTime,_that.pageController,_that.contentController);case _:
  return null;

}
}

}

/// @nodoc
@JsonSerializable()

class _BooksControllerState extends BooksControllerState {
  const _BooksControllerState({this.lastRefreshTime, @JsonKey(includeToJson: false, includeFromJson: false) this.pageController, @JsonKey(includeToJson: false, includeFromJson: false) this.contentController}): super._();
  factory _BooksControllerState.fromJson(Map<String, dynamic> json) => _$BooksControllerStateFromJson(json);

/// 最后刷新时间
@override final  DateTime? lastRefreshTime;
/// PageController (不在freezed中管理)
// ignore: invalid_annotation_target
@override@JsonKey(includeToJson: false, includeFromJson: false) final  PageController? pageController;
/// TextEditingController for content (不在freezed中管理)
// ignore: invalid_annotation_target
@override@JsonKey(includeToJson: false, includeFromJson: false) final  TextEditingController? contentController;

/// Create a copy of BooksControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$BooksControllerStateCopyWith<_BooksControllerState> get copyWith => __$BooksControllerStateCopyWithImpl<_BooksControllerState>(this, _$identity);

@override
Map<String, dynamic> toJson() {
  return _$BooksControllerStateToJson(this, );
}

@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _BooksControllerState&&(identical(other.lastRefreshTime, lastRefreshTime) || other.lastRefreshTime == lastRefreshTime)&&(identical(other.pageController, pageController) || other.pageController == pageController)&&(identical(other.contentController, contentController) || other.contentController == contentController));
}

@JsonKey(includeFromJson: false, includeToJson: false)
@override
int get hashCode => Object.hash(runtimeType,lastRefreshTime,pageController,contentController);

@override
String toString() {
  return 'BooksControllerState(lastRefreshTime: $lastRefreshTime, pageController: $pageController, contentController: $contentController)';
}


}

/// @nodoc
abstract mixin class _$BooksControllerStateCopyWith<$Res> implements $BooksControllerStateCopyWith<$Res> {
  factory _$BooksControllerStateCopyWith(_BooksControllerState value, $Res Function(_BooksControllerState) _then) = __$BooksControllerStateCopyWithImpl;
@override @useResult
$Res call({
 DateTime? lastRefreshTime,@JsonKey(includeToJson: false, includeFromJson: false) PageController? pageController,@JsonKey(includeToJson: false, includeFromJson: false) TextEditingController? contentController
});




}
/// @nodoc
class __$BooksControllerStateCopyWithImpl<$Res>
    implements _$BooksControllerStateCopyWith<$Res> {
  __$BooksControllerStateCopyWithImpl(this._self, this._then);

  final _BooksControllerState _self;
  final $Res Function(_BooksControllerState) _then;

/// Create a copy of BooksControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? lastRefreshTime = freezed,Object? pageController = freezed,Object? contentController = freezed,}) {
  return _then(_BooksControllerState(
lastRefreshTime: freezed == lastRefreshTime ? _self.lastRefreshTime : lastRefreshTime // ignore: cast_nullable_to_non_nullable
as DateTime?,pageController: freezed == pageController ? _self.pageController : pageController // ignore: cast_nullable_to_non_nullable
as PageController?,contentController: freezed == contentController ? _self.contentController : contentController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,
  ));
}


}

// dart format on
