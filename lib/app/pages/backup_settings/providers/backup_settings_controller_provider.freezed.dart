// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'backup_settings_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$BackupSettingsControllerState {

 bool get isLoading; String get errorMessage; String get backupDirectory; double get backupProgress; bool get isBackingUp;
/// Create a copy of BackupSettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$BackupSettingsControllerStateCopyWith<BackupSettingsControllerState> get copyWith => _$BackupSettingsControllerStateCopyWithImpl<BackupSettingsControllerState>(this as BackupSettingsControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is BackupSettingsControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.backupDirectory, backupDirectory) || other.backupDirectory == backupDirectory)&&(identical(other.backupProgress, backupProgress) || other.backupProgress == backupProgress)&&(identical(other.isBackingUp, isBackingUp) || other.isBackingUp == isBackingUp));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,errorMessage,backupDirectory,backupProgress,isBackingUp);

@override
String toString() {
  return 'BackupSettingsControllerState(isLoading: $isLoading, errorMessage: $errorMessage, backupDirectory: $backupDirectory, backupProgress: $backupProgress, isBackingUp: $isBackingUp)';
}


}

/// @nodoc
abstract mixin class $BackupSettingsControllerStateCopyWith<$Res>  {
  factory $BackupSettingsControllerStateCopyWith(BackupSettingsControllerState value, $Res Function(BackupSettingsControllerState) _then) = _$BackupSettingsControllerStateCopyWithImpl;
@useResult
$Res call({
 bool isLoading, String errorMessage, String backupDirectory, double backupProgress, bool isBackingUp
});




}
/// @nodoc
class _$BackupSettingsControllerStateCopyWithImpl<$Res>
    implements $BackupSettingsControllerStateCopyWith<$Res> {
  _$BackupSettingsControllerStateCopyWithImpl(this._self, this._then);

  final BackupSettingsControllerState _self;
  final $Res Function(BackupSettingsControllerState) _then;

/// Create a copy of BackupSettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? isLoading = null,Object? errorMessage = null,Object? backupDirectory = null,Object? backupProgress = null,Object? isBackingUp = null,}) {
  return _then(_self.copyWith(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,backupDirectory: null == backupDirectory ? _self.backupDirectory : backupDirectory // ignore: cast_nullable_to_non_nullable
as String,backupProgress: null == backupProgress ? _self.backupProgress : backupProgress // ignore: cast_nullable_to_non_nullable
as double,isBackingUp: null == isBackingUp ? _self.isBackingUp : isBackingUp // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}

}


/// Adds pattern-matching-related methods to [BackupSettingsControllerState].
extension BackupSettingsControllerStatePatterns on BackupSettingsControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _BackupSettingsControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _BackupSettingsControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _BackupSettingsControllerState value)  $default,){
final _that = this;
switch (_that) {
case _BackupSettingsControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _BackupSettingsControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _BackupSettingsControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool isLoading,  String errorMessage,  String backupDirectory,  double backupProgress,  bool isBackingUp)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _BackupSettingsControllerState() when $default != null:
return $default(_that.isLoading,_that.errorMessage,_that.backupDirectory,_that.backupProgress,_that.isBackingUp);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool isLoading,  String errorMessage,  String backupDirectory,  double backupProgress,  bool isBackingUp)  $default,) {final _that = this;
switch (_that) {
case _BackupSettingsControllerState():
return $default(_that.isLoading,_that.errorMessage,_that.backupDirectory,_that.backupProgress,_that.isBackingUp);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool isLoading,  String errorMessage,  String backupDirectory,  double backupProgress,  bool isBackingUp)?  $default,) {final _that = this;
switch (_that) {
case _BackupSettingsControllerState() when $default != null:
return $default(_that.isLoading,_that.errorMessage,_that.backupDirectory,_that.backupProgress,_that.isBackingUp);case _:
  return null;

}
}

}

/// @nodoc


class _BackupSettingsControllerState implements BackupSettingsControllerState {
  const _BackupSettingsControllerState({this.isLoading = false, this.errorMessage = '', this.backupDirectory = '', this.backupProgress = 0.0, this.isBackingUp = false});
  

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  String errorMessage;
@override@JsonKey() final  String backupDirectory;
@override@JsonKey() final  double backupProgress;
@override@JsonKey() final  bool isBackingUp;

/// Create a copy of BackupSettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$BackupSettingsControllerStateCopyWith<_BackupSettingsControllerState> get copyWith => __$BackupSettingsControllerStateCopyWithImpl<_BackupSettingsControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _BackupSettingsControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.backupDirectory, backupDirectory) || other.backupDirectory == backupDirectory)&&(identical(other.backupProgress, backupProgress) || other.backupProgress == backupProgress)&&(identical(other.isBackingUp, isBackingUp) || other.isBackingUp == isBackingUp));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,errorMessage,backupDirectory,backupProgress,isBackingUp);

@override
String toString() {
  return 'BackupSettingsControllerState(isLoading: $isLoading, errorMessage: $errorMessage, backupDirectory: $backupDirectory, backupProgress: $backupProgress, isBackingUp: $isBackingUp)';
}


}

/// @nodoc
abstract mixin class _$BackupSettingsControllerStateCopyWith<$Res> implements $BackupSettingsControllerStateCopyWith<$Res> {
  factory _$BackupSettingsControllerStateCopyWith(_BackupSettingsControllerState value, $Res Function(_BackupSettingsControllerState) _then) = __$BackupSettingsControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool isLoading, String errorMessage, String backupDirectory, double backupProgress, bool isBackingUp
});




}
/// @nodoc
class __$BackupSettingsControllerStateCopyWithImpl<$Res>
    implements _$BackupSettingsControllerStateCopyWith<$Res> {
  __$BackupSettingsControllerStateCopyWithImpl(this._self, this._then);

  final _BackupSettingsControllerState _self;
  final $Res Function(_BackupSettingsControllerState) _then;

/// Create a copy of BackupSettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? isLoading = null,Object? errorMessage = null,Object? backupDirectory = null,Object? backupProgress = null,Object? isBackingUp = null,}) {
  return _then(_BackupSettingsControllerState(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,backupDirectory: null == backupDirectory ? _self.backupDirectory : backupDirectory // ignore: cast_nullable_to_non_nullable
as String,backupProgress: null == backupProgress ? _self.backupProgress : backupProgress // ignore: cast_nullable_to_non_nullable
as double,isBackingUp: null == isBackingUp ? _self.isBackingUp : isBackingUp // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}


}

// dart format on
