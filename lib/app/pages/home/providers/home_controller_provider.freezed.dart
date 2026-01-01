// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'home_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;

/// @nodoc
mixin _$HomeControllerState implements DiagnosticableTreeMixin {

/// 当前选中的页面索引
/// 0: 文章页面
/// 1: 日记页面
/// 2: 读书页面
/// 3: AI助手页面
/// 4: 设置页面
 int get currentIndex;
/// Create a copy of HomeControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$HomeControllerStateCopyWith<HomeControllerState> get copyWith => _$HomeControllerStateCopyWithImpl<HomeControllerState>(this as HomeControllerState, _$identity);

  /// Serializes this HomeControllerState to a JSON map.
  Map<String, dynamic> toJson();

@override
void debugFillProperties(DiagnosticPropertiesBuilder properties) {
  properties
    ..add(DiagnosticsProperty('type', 'HomeControllerState'))
    ..add(DiagnosticsProperty('currentIndex', currentIndex));
}

@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is HomeControllerState&&(identical(other.currentIndex, currentIndex) || other.currentIndex == currentIndex));
}

@JsonKey(includeFromJson: false, includeToJson: false)
@override
int get hashCode => Object.hash(runtimeType,currentIndex);

@override
String toString({ DiagnosticLevel minLevel = DiagnosticLevel.info }) {
  return 'HomeControllerState(currentIndex: $currentIndex)';
}


}

/// @nodoc
abstract mixin class $HomeControllerStateCopyWith<$Res>  {
  factory $HomeControllerStateCopyWith(HomeControllerState value, $Res Function(HomeControllerState) _then) = _$HomeControllerStateCopyWithImpl;
@useResult
$Res call({
 int currentIndex
});




}
/// @nodoc
class _$HomeControllerStateCopyWithImpl<$Res>
    implements $HomeControllerStateCopyWith<$Res> {
  _$HomeControllerStateCopyWithImpl(this._self, this._then);

  final HomeControllerState _self;
  final $Res Function(HomeControllerState) _then;

/// Create a copy of HomeControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? currentIndex = null,}) {
  return _then(_self.copyWith(
currentIndex: null == currentIndex ? _self.currentIndex : currentIndex // ignore: cast_nullable_to_non_nullable
as int,
  ));
}

}


/// Adds pattern-matching-related methods to [HomeControllerState].
extension HomeControllerStatePatterns on HomeControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _HomeControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _HomeControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _HomeControllerState value)  $default,){
final _that = this;
switch (_that) {
case _HomeControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _HomeControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _HomeControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( int currentIndex)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _HomeControllerState() when $default != null:
return $default(_that.currentIndex);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( int currentIndex)  $default,) {final _that = this;
switch (_that) {
case _HomeControllerState():
return $default(_that.currentIndex);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( int currentIndex)?  $default,) {final _that = this;
switch (_that) {
case _HomeControllerState() when $default != null:
return $default(_that.currentIndex);case _:
  return null;

}
}

}

/// @nodoc
@JsonSerializable()

class _HomeControllerState with DiagnosticableTreeMixin implements HomeControllerState {
  const _HomeControllerState({this.currentIndex = 0});
  factory _HomeControllerState.fromJson(Map<String, dynamic> json) => _$HomeControllerStateFromJson(json);

/// 当前选中的页面索引
/// 0: 文章页面
/// 1: 日记页面
/// 2: 读书页面
/// 3: AI助手页面
/// 4: 设置页面
@override@JsonKey() final  int currentIndex;

/// Create a copy of HomeControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$HomeControllerStateCopyWith<_HomeControllerState> get copyWith => __$HomeControllerStateCopyWithImpl<_HomeControllerState>(this, _$identity);

@override
Map<String, dynamic> toJson() {
  return _$HomeControllerStateToJson(this, );
}
@override
void debugFillProperties(DiagnosticPropertiesBuilder properties) {
  properties
    ..add(DiagnosticsProperty('type', 'HomeControllerState'))
    ..add(DiagnosticsProperty('currentIndex', currentIndex));
}

@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _HomeControllerState&&(identical(other.currentIndex, currentIndex) || other.currentIndex == currentIndex));
}

@JsonKey(includeFromJson: false, includeToJson: false)
@override
int get hashCode => Object.hash(runtimeType,currentIndex);

@override
String toString({ DiagnosticLevel minLevel = DiagnosticLevel.info }) {
  return 'HomeControllerState(currentIndex: $currentIndex)';
}


}

/// @nodoc
abstract mixin class _$HomeControllerStateCopyWith<$Res> implements $HomeControllerStateCopyWith<$Res> {
  factory _$HomeControllerStateCopyWith(_HomeControllerState value, $Res Function(_HomeControllerState) _then) = __$HomeControllerStateCopyWithImpl;
@override @useResult
$Res call({
 int currentIndex
});




}
/// @nodoc
class __$HomeControllerStateCopyWithImpl<$Res>
    implements _$HomeControllerStateCopyWith<$Res> {
  __$HomeControllerStateCopyWithImpl(this._self, this._then);

  final _HomeControllerState _self;
  final $Res Function(_HomeControllerState) _then;

/// Create a copy of HomeControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? currentIndex = null,}) {
  return _then(_HomeControllerState(
currentIndex: null == currentIndex ? _self.currentIndex : currentIndex // ignore: cast_nullable_to_non_nullable
as int,
  ));
}


}

// dart format on
