// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'ai_config_edit_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$AIConfigEditControllerState {

 bool get isLoading; bool get isSaving; String get errorMessage; AIConfig? get config; String get name; String get apiAddress; String get apiToken; String get modelName; int get functionType; bool get inheritFromGeneral; bool get isDefault;
/// Create a copy of AIConfigEditControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$AIConfigEditControllerStateCopyWith<AIConfigEditControllerState> get copyWith => _$AIConfigEditControllerStateCopyWithImpl<AIConfigEditControllerState>(this as AIConfigEditControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is AIConfigEditControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.isSaving, isSaving) || other.isSaving == isSaving)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.config, config) || other.config == config)&&(identical(other.name, name) || other.name == name)&&(identical(other.apiAddress, apiAddress) || other.apiAddress == apiAddress)&&(identical(other.apiToken, apiToken) || other.apiToken == apiToken)&&(identical(other.modelName, modelName) || other.modelName == modelName)&&(identical(other.functionType, functionType) || other.functionType == functionType)&&(identical(other.inheritFromGeneral, inheritFromGeneral) || other.inheritFromGeneral == inheritFromGeneral)&&(identical(other.isDefault, isDefault) || other.isDefault == isDefault));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,isSaving,errorMessage,config,name,apiAddress,apiToken,modelName,functionType,inheritFromGeneral,isDefault);

@override
String toString() {
  return 'AIConfigEditControllerState(isLoading: $isLoading, isSaving: $isSaving, errorMessage: $errorMessage, config: $config, name: $name, apiAddress: $apiAddress, apiToken: $apiToken, modelName: $modelName, functionType: $functionType, inheritFromGeneral: $inheritFromGeneral, isDefault: $isDefault)';
}


}

/// @nodoc
abstract mixin class $AIConfigEditControllerStateCopyWith<$Res>  {
  factory $AIConfigEditControllerStateCopyWith(AIConfigEditControllerState value, $Res Function(AIConfigEditControllerState) _then) = _$AIConfigEditControllerStateCopyWithImpl;
@useResult
$Res call({
 bool isLoading, bool isSaving, String errorMessage, AIConfig? config, String name, String apiAddress, String apiToken, String modelName, int functionType, bool inheritFromGeneral, bool isDefault
});




}
/// @nodoc
class _$AIConfigEditControllerStateCopyWithImpl<$Res>
    implements $AIConfigEditControllerStateCopyWith<$Res> {
  _$AIConfigEditControllerStateCopyWithImpl(this._self, this._then);

  final AIConfigEditControllerState _self;
  final $Res Function(AIConfigEditControllerState) _then;

/// Create a copy of AIConfigEditControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? isLoading = null,Object? isSaving = null,Object? errorMessage = null,Object? config = freezed,Object? name = null,Object? apiAddress = null,Object? apiToken = null,Object? modelName = null,Object? functionType = null,Object? inheritFromGeneral = null,Object? isDefault = null,}) {
  return _then(_self.copyWith(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,isSaving: null == isSaving ? _self.isSaving : isSaving // ignore: cast_nullable_to_non_nullable
as bool,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,config: freezed == config ? _self.config : config // ignore: cast_nullable_to_non_nullable
as AIConfig?,name: null == name ? _self.name : name // ignore: cast_nullable_to_non_nullable
as String,apiAddress: null == apiAddress ? _self.apiAddress : apiAddress // ignore: cast_nullable_to_non_nullable
as String,apiToken: null == apiToken ? _self.apiToken : apiToken // ignore: cast_nullable_to_non_nullable
as String,modelName: null == modelName ? _self.modelName : modelName // ignore: cast_nullable_to_non_nullable
as String,functionType: null == functionType ? _self.functionType : functionType // ignore: cast_nullable_to_non_nullable
as int,inheritFromGeneral: null == inheritFromGeneral ? _self.inheritFromGeneral : inheritFromGeneral // ignore: cast_nullable_to_non_nullable
as bool,isDefault: null == isDefault ? _self.isDefault : isDefault // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}

}


/// Adds pattern-matching-related methods to [AIConfigEditControllerState].
extension AIConfigEditControllerStatePatterns on AIConfigEditControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _AIConfigEditControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _AIConfigEditControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _AIConfigEditControllerState value)  $default,){
final _that = this;
switch (_that) {
case _AIConfigEditControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _AIConfigEditControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _AIConfigEditControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool isLoading,  bool isSaving,  String errorMessage,  AIConfig? config,  String name,  String apiAddress,  String apiToken,  String modelName,  int functionType,  bool inheritFromGeneral,  bool isDefault)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _AIConfigEditControllerState() when $default != null:
return $default(_that.isLoading,_that.isSaving,_that.errorMessage,_that.config,_that.name,_that.apiAddress,_that.apiToken,_that.modelName,_that.functionType,_that.inheritFromGeneral,_that.isDefault);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool isLoading,  bool isSaving,  String errorMessage,  AIConfig? config,  String name,  String apiAddress,  String apiToken,  String modelName,  int functionType,  bool inheritFromGeneral,  bool isDefault)  $default,) {final _that = this;
switch (_that) {
case _AIConfigEditControllerState():
return $default(_that.isLoading,_that.isSaving,_that.errorMessage,_that.config,_that.name,_that.apiAddress,_that.apiToken,_that.modelName,_that.functionType,_that.inheritFromGeneral,_that.isDefault);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool isLoading,  bool isSaving,  String errorMessage,  AIConfig? config,  String name,  String apiAddress,  String apiToken,  String modelName,  int functionType,  bool inheritFromGeneral,  bool isDefault)?  $default,) {final _that = this;
switch (_that) {
case _AIConfigEditControllerState() when $default != null:
return $default(_that.isLoading,_that.isSaving,_that.errorMessage,_that.config,_that.name,_that.apiAddress,_that.apiToken,_that.modelName,_that.functionType,_that.inheritFromGeneral,_that.isDefault);case _:
  return null;

}
}

}

/// @nodoc


class _AIConfigEditControllerState implements AIConfigEditControllerState {
  const _AIConfigEditControllerState({this.isLoading = false, this.isSaving = false, this.errorMessage = '', this.config, this.name = '', this.apiAddress = '', this.apiToken = '', this.modelName = '', this.functionType = 0, this.inheritFromGeneral = false, this.isDefault = false});
  

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  bool isSaving;
@override@JsonKey() final  String errorMessage;
@override final  AIConfig? config;
@override@JsonKey() final  String name;
@override@JsonKey() final  String apiAddress;
@override@JsonKey() final  String apiToken;
@override@JsonKey() final  String modelName;
@override@JsonKey() final  int functionType;
@override@JsonKey() final  bool inheritFromGeneral;
@override@JsonKey() final  bool isDefault;

/// Create a copy of AIConfigEditControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$AIConfigEditControllerStateCopyWith<_AIConfigEditControllerState> get copyWith => __$AIConfigEditControllerStateCopyWithImpl<_AIConfigEditControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _AIConfigEditControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.isSaving, isSaving) || other.isSaving == isSaving)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.config, config) || other.config == config)&&(identical(other.name, name) || other.name == name)&&(identical(other.apiAddress, apiAddress) || other.apiAddress == apiAddress)&&(identical(other.apiToken, apiToken) || other.apiToken == apiToken)&&(identical(other.modelName, modelName) || other.modelName == modelName)&&(identical(other.functionType, functionType) || other.functionType == functionType)&&(identical(other.inheritFromGeneral, inheritFromGeneral) || other.inheritFromGeneral == inheritFromGeneral)&&(identical(other.isDefault, isDefault) || other.isDefault == isDefault));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,isSaving,errorMessage,config,name,apiAddress,apiToken,modelName,functionType,inheritFromGeneral,isDefault);

@override
String toString() {
  return 'AIConfigEditControllerState(isLoading: $isLoading, isSaving: $isSaving, errorMessage: $errorMessage, config: $config, name: $name, apiAddress: $apiAddress, apiToken: $apiToken, modelName: $modelName, functionType: $functionType, inheritFromGeneral: $inheritFromGeneral, isDefault: $isDefault)';
}


}

/// @nodoc
abstract mixin class _$AIConfigEditControllerStateCopyWith<$Res> implements $AIConfigEditControllerStateCopyWith<$Res> {
  factory _$AIConfigEditControllerStateCopyWith(_AIConfigEditControllerState value, $Res Function(_AIConfigEditControllerState) _then) = __$AIConfigEditControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool isLoading, bool isSaving, String errorMessage, AIConfig? config, String name, String apiAddress, String apiToken, String modelName, int functionType, bool inheritFromGeneral, bool isDefault
});




}
/// @nodoc
class __$AIConfigEditControllerStateCopyWithImpl<$Res>
    implements _$AIConfigEditControllerStateCopyWith<$Res> {
  __$AIConfigEditControllerStateCopyWithImpl(this._self, this._then);

  final _AIConfigEditControllerState _self;
  final $Res Function(_AIConfigEditControllerState) _then;

/// Create a copy of AIConfigEditControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? isLoading = null,Object? isSaving = null,Object? errorMessage = null,Object? config = freezed,Object? name = null,Object? apiAddress = null,Object? apiToken = null,Object? modelName = null,Object? functionType = null,Object? inheritFromGeneral = null,Object? isDefault = null,}) {
  return _then(_AIConfigEditControllerState(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,isSaving: null == isSaving ? _self.isSaving : isSaving // ignore: cast_nullable_to_non_nullable
as bool,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,config: freezed == config ? _self.config : config // ignore: cast_nullable_to_non_nullable
as AIConfig?,name: null == name ? _self.name : name // ignore: cast_nullable_to_non_nullable
as String,apiAddress: null == apiAddress ? _self.apiAddress : apiAddress // ignore: cast_nullable_to_non_nullable
as String,apiToken: null == apiToken ? _self.apiToken : apiToken // ignore: cast_nullable_to_non_nullable
as String,modelName: null == modelName ? _self.modelName : modelName // ignore: cast_nullable_to_non_nullable
as String,functionType: null == functionType ? _self.functionType : functionType // ignore: cast_nullable_to_non_nullable
as int,inheritFromGeneral: null == inheritFromGeneral ? _self.inheritFromGeneral : inheritFromGeneral // ignore: cast_nullable_to_non_nullable
as bool,isDefault: null == isDefault ? _self.isDefault : isDefault // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}


}

// dart format on
