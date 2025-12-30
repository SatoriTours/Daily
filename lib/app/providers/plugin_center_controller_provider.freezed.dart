// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'plugin_center_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$PluginCenterControllerState {

 bool get isLoading; String get updatingPluginId; String get pluginServerUrl;
/// Create a copy of PluginCenterControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$PluginCenterControllerStateCopyWith<PluginCenterControllerState> get copyWith => _$PluginCenterControllerStateCopyWithImpl<PluginCenterControllerState>(this as PluginCenterControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is PluginCenterControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.updatingPluginId, updatingPluginId) || other.updatingPluginId == updatingPluginId)&&(identical(other.pluginServerUrl, pluginServerUrl) || other.pluginServerUrl == pluginServerUrl));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,updatingPluginId,pluginServerUrl);

@override
String toString() {
  return 'PluginCenterControllerState(isLoading: $isLoading, updatingPluginId: $updatingPluginId, pluginServerUrl: $pluginServerUrl)';
}


}

/// @nodoc
abstract mixin class $PluginCenterControllerStateCopyWith<$Res>  {
  factory $PluginCenterControllerStateCopyWith(PluginCenterControllerState value, $Res Function(PluginCenterControllerState) _then) = _$PluginCenterControllerStateCopyWithImpl;
@useResult
$Res call({
 bool isLoading, String updatingPluginId, String pluginServerUrl
});




}
/// @nodoc
class _$PluginCenterControllerStateCopyWithImpl<$Res>
    implements $PluginCenterControllerStateCopyWith<$Res> {
  _$PluginCenterControllerStateCopyWithImpl(this._self, this._then);

  final PluginCenterControllerState _self;
  final $Res Function(PluginCenterControllerState) _then;

/// Create a copy of PluginCenterControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? isLoading = null,Object? updatingPluginId = null,Object? pluginServerUrl = null,}) {
  return _then(_self.copyWith(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,updatingPluginId: null == updatingPluginId ? _self.updatingPluginId : updatingPluginId // ignore: cast_nullable_to_non_nullable
as String,pluginServerUrl: null == pluginServerUrl ? _self.pluginServerUrl : pluginServerUrl // ignore: cast_nullable_to_non_nullable
as String,
  ));
}

}


/// Adds pattern-matching-related methods to [PluginCenterControllerState].
extension PluginCenterControllerStatePatterns on PluginCenterControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _PluginCenterControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _PluginCenterControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _PluginCenterControllerState value)  $default,){
final _that = this;
switch (_that) {
case _PluginCenterControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _PluginCenterControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _PluginCenterControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool isLoading,  String updatingPluginId,  String pluginServerUrl)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _PluginCenterControllerState() when $default != null:
return $default(_that.isLoading,_that.updatingPluginId,_that.pluginServerUrl);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool isLoading,  String updatingPluginId,  String pluginServerUrl)  $default,) {final _that = this;
switch (_that) {
case _PluginCenterControllerState():
return $default(_that.isLoading,_that.updatingPluginId,_that.pluginServerUrl);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool isLoading,  String updatingPluginId,  String pluginServerUrl)?  $default,) {final _that = this;
switch (_that) {
case _PluginCenterControllerState() when $default != null:
return $default(_that.isLoading,_that.updatingPluginId,_that.pluginServerUrl);case _:
  return null;

}
}

}

/// @nodoc


class _PluginCenterControllerState implements PluginCenterControllerState {
  const _PluginCenterControllerState({this.isLoading = false, this.updatingPluginId = '', this.pluginServerUrl = ''});
  

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  String updatingPluginId;
@override@JsonKey() final  String pluginServerUrl;

/// Create a copy of PluginCenterControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$PluginCenterControllerStateCopyWith<_PluginCenterControllerState> get copyWith => __$PluginCenterControllerStateCopyWithImpl<_PluginCenterControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _PluginCenterControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.updatingPluginId, updatingPluginId) || other.updatingPluginId == updatingPluginId)&&(identical(other.pluginServerUrl, pluginServerUrl) || other.pluginServerUrl == pluginServerUrl));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,updatingPluginId,pluginServerUrl);

@override
String toString() {
  return 'PluginCenterControllerState(isLoading: $isLoading, updatingPluginId: $updatingPluginId, pluginServerUrl: $pluginServerUrl)';
}


}

/// @nodoc
abstract mixin class _$PluginCenterControllerStateCopyWith<$Res> implements $PluginCenterControllerStateCopyWith<$Res> {
  factory _$PluginCenterControllerStateCopyWith(_PluginCenterControllerState value, $Res Function(_PluginCenterControllerState) _then) = __$PluginCenterControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool isLoading, String updatingPluginId, String pluginServerUrl
});




}
/// @nodoc
class __$PluginCenterControllerStateCopyWithImpl<$Res>
    implements _$PluginCenterControllerStateCopyWith<$Res> {
  __$PluginCenterControllerStateCopyWithImpl(this._self, this._then);

  final _PluginCenterControllerState _self;
  final $Res Function(_PluginCenterControllerState) _then;

/// Create a copy of PluginCenterControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? isLoading = null,Object? updatingPluginId = null,Object? pluginServerUrl = null,}) {
  return _then(_PluginCenterControllerState(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,updatingPluginId: null == updatingPluginId ? _self.updatingPluginId : updatingPluginId // ignore: cast_nullable_to_non_nullable
as String,pluginServerUrl: null == pluginServerUrl ? _self.pluginServerUrl : pluginServerUrl // ignore: cast_nullable_to_non_nullable
as String,
  ));
}


}

// dart format on
