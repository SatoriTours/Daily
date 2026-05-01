// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'ai_config_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$AIConfigControllerState {

/// 选中的功能类型
 int get selectedFunctionType;/// 是否正在加载
 bool get isLoading;/// 配置列表
 List<AIConfigModel> get configs;
/// Create a copy of AIConfigControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$AIConfigControllerStateCopyWith<AIConfigControllerState> get copyWith => _$AIConfigControllerStateCopyWithImpl<AIConfigControllerState>(this as AIConfigControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is AIConfigControllerState&&(identical(other.selectedFunctionType, selectedFunctionType) || other.selectedFunctionType == selectedFunctionType)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&const DeepCollectionEquality().equals(other.configs, configs));
}


@override
int get hashCode => Object.hash(runtimeType,selectedFunctionType,isLoading,const DeepCollectionEquality().hash(configs));

@override
String toString() {
  return 'AIConfigControllerState(selectedFunctionType: $selectedFunctionType, isLoading: $isLoading, configs: $configs)';
}


}

/// @nodoc
abstract mixin class $AIConfigControllerStateCopyWith<$Res>  {
  factory $AIConfigControllerStateCopyWith(AIConfigControllerState value, $Res Function(AIConfigControllerState) _then) = _$AIConfigControllerStateCopyWithImpl;
@useResult
$Res call({
 int selectedFunctionType, bool isLoading, List<AIConfigModel> configs
});




}
/// @nodoc
class _$AIConfigControllerStateCopyWithImpl<$Res>
    implements $AIConfigControllerStateCopyWith<$Res> {
  _$AIConfigControllerStateCopyWithImpl(this._self, this._then);

  final AIConfigControllerState _self;
  final $Res Function(AIConfigControllerState) _then;

/// Create a copy of AIConfigControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? selectedFunctionType = null,Object? isLoading = null,Object? configs = null,}) {
  return _then(_self.copyWith(
selectedFunctionType: null == selectedFunctionType ? _self.selectedFunctionType : selectedFunctionType // ignore: cast_nullable_to_non_nullable
as int,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,configs: null == configs ? _self.configs : configs // ignore: cast_nullable_to_non_nullable
as List<AIConfigModel>,
  ));
}

}


/// Adds pattern-matching-related methods to [AIConfigControllerState].
extension AIConfigControllerStatePatterns on AIConfigControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _AIConfigControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _AIConfigControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _AIConfigControllerState value)  $default,){
final _that = this;
switch (_that) {
case _AIConfigControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _AIConfigControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _AIConfigControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( int selectedFunctionType,  bool isLoading,  List<AIConfigModel> configs)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _AIConfigControllerState() when $default != null:
return $default(_that.selectedFunctionType,_that.isLoading,_that.configs);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( int selectedFunctionType,  bool isLoading,  List<AIConfigModel> configs)  $default,) {final _that = this;
switch (_that) {
case _AIConfigControllerState():
return $default(_that.selectedFunctionType,_that.isLoading,_that.configs);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( int selectedFunctionType,  bool isLoading,  List<AIConfigModel> configs)?  $default,) {final _that = this;
switch (_that) {
case _AIConfigControllerState() when $default != null:
return $default(_that.selectedFunctionType,_that.isLoading,_that.configs);case _:
  return null;

}
}

}

/// @nodoc


class _AIConfigControllerState implements AIConfigControllerState {
  const _AIConfigControllerState({this.selectedFunctionType = 0, this.isLoading = false, final  List<AIConfigModel> configs = const []}): _configs = configs;
  

/// 选中的功能类型
@override@JsonKey() final  int selectedFunctionType;
/// 是否正在加载
@override@JsonKey() final  bool isLoading;
/// 配置列表
 final  List<AIConfigModel> _configs;
/// 配置列表
@override@JsonKey() List<AIConfigModel> get configs {
  if (_configs is EqualUnmodifiableListView) return _configs;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_configs);
}


/// Create a copy of AIConfigControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$AIConfigControllerStateCopyWith<_AIConfigControllerState> get copyWith => __$AIConfigControllerStateCopyWithImpl<_AIConfigControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _AIConfigControllerState&&(identical(other.selectedFunctionType, selectedFunctionType) || other.selectedFunctionType == selectedFunctionType)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&const DeepCollectionEquality().equals(other._configs, _configs));
}


@override
int get hashCode => Object.hash(runtimeType,selectedFunctionType,isLoading,const DeepCollectionEquality().hash(_configs));

@override
String toString() {
  return 'AIConfigControllerState(selectedFunctionType: $selectedFunctionType, isLoading: $isLoading, configs: $configs)';
}


}

/// @nodoc
abstract mixin class _$AIConfigControllerStateCopyWith<$Res> implements $AIConfigControllerStateCopyWith<$Res> {
  factory _$AIConfigControllerStateCopyWith(_AIConfigControllerState value, $Res Function(_AIConfigControllerState) _then) = __$AIConfigControllerStateCopyWithImpl;
@override @useResult
$Res call({
 int selectedFunctionType, bool isLoading, List<AIConfigModel> configs
});




}
/// @nodoc
class __$AIConfigControllerStateCopyWithImpl<$Res>
    implements _$AIConfigControllerStateCopyWith<$Res> {
  __$AIConfigControllerStateCopyWithImpl(this._self, this._then);

  final _AIConfigControllerState _self;
  final $Res Function(_AIConfigControllerState) _then;

/// Create a copy of AIConfigControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? selectedFunctionType = null,Object? isLoading = null,Object? configs = null,}) {
  return _then(_AIConfigControllerState(
selectedFunctionType: null == selectedFunctionType ? _self.selectedFunctionType : selectedFunctionType // ignore: cast_nullable_to_non_nullable
as int,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,configs: null == configs ? _self._configs : configs // ignore: cast_nullable_to_non_nullable
as List<AIConfigModel>,
  ));
}


}

// dart format on
