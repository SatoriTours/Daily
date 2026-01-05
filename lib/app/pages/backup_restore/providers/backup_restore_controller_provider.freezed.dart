// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'backup_restore_controller_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$BackupRestoreControllerState {

 bool get isLoading; bool get isRestoring; List<FileSystemEntity> get backupList; int get selectedBackupIndex; String get errorMessage; String get backupPath;
/// Create a copy of BackupRestoreControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$BackupRestoreControllerStateCopyWith<BackupRestoreControllerState> get copyWith => _$BackupRestoreControllerStateCopyWithImpl<BackupRestoreControllerState>(this as BackupRestoreControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is BackupRestoreControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.isRestoring, isRestoring) || other.isRestoring == isRestoring)&&const DeepCollectionEquality().equals(other.backupList, backupList)&&(identical(other.selectedBackupIndex, selectedBackupIndex) || other.selectedBackupIndex == selectedBackupIndex)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.backupPath, backupPath) || other.backupPath == backupPath));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,isRestoring,const DeepCollectionEquality().hash(backupList),selectedBackupIndex,errorMessage,backupPath);

@override
String toString() {
  return 'BackupRestoreControllerState(isLoading: $isLoading, isRestoring: $isRestoring, backupList: $backupList, selectedBackupIndex: $selectedBackupIndex, errorMessage: $errorMessage, backupPath: $backupPath)';
}


}

/// @nodoc
abstract mixin class $BackupRestoreControllerStateCopyWith<$Res>  {
  factory $BackupRestoreControllerStateCopyWith(BackupRestoreControllerState value, $Res Function(BackupRestoreControllerState) _then) = _$BackupRestoreControllerStateCopyWithImpl;
@useResult
$Res call({
 bool isLoading, bool isRestoring, List<FileSystemEntity> backupList, int selectedBackupIndex, String errorMessage, String backupPath
});




}
/// @nodoc
class _$BackupRestoreControllerStateCopyWithImpl<$Res>
    implements $BackupRestoreControllerStateCopyWith<$Res> {
  _$BackupRestoreControllerStateCopyWithImpl(this._self, this._then);

  final BackupRestoreControllerState _self;
  final $Res Function(BackupRestoreControllerState) _then;

/// Create a copy of BackupRestoreControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? isLoading = null,Object? isRestoring = null,Object? backupList = null,Object? selectedBackupIndex = null,Object? errorMessage = null,Object? backupPath = null,}) {
  return _then(_self.copyWith(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,isRestoring: null == isRestoring ? _self.isRestoring : isRestoring // ignore: cast_nullable_to_non_nullable
as bool,backupList: null == backupList ? _self.backupList : backupList // ignore: cast_nullable_to_non_nullable
as List<FileSystemEntity>,selectedBackupIndex: null == selectedBackupIndex ? _self.selectedBackupIndex : selectedBackupIndex // ignore: cast_nullable_to_non_nullable
as int,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,backupPath: null == backupPath ? _self.backupPath : backupPath // ignore: cast_nullable_to_non_nullable
as String,
  ));
}

}


/// Adds pattern-matching-related methods to [BackupRestoreControllerState].
extension BackupRestoreControllerStatePatterns on BackupRestoreControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _BackupRestoreControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _BackupRestoreControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _BackupRestoreControllerState value)  $default,){
final _that = this;
switch (_that) {
case _BackupRestoreControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _BackupRestoreControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _BackupRestoreControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool isLoading,  bool isRestoring,  List<FileSystemEntity> backupList,  int selectedBackupIndex,  String errorMessage,  String backupPath)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _BackupRestoreControllerState() when $default != null:
return $default(_that.isLoading,_that.isRestoring,_that.backupList,_that.selectedBackupIndex,_that.errorMessage,_that.backupPath);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool isLoading,  bool isRestoring,  List<FileSystemEntity> backupList,  int selectedBackupIndex,  String errorMessage,  String backupPath)  $default,) {final _that = this;
switch (_that) {
case _BackupRestoreControllerState():
return $default(_that.isLoading,_that.isRestoring,_that.backupList,_that.selectedBackupIndex,_that.errorMessage,_that.backupPath);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool isLoading,  bool isRestoring,  List<FileSystemEntity> backupList,  int selectedBackupIndex,  String errorMessage,  String backupPath)?  $default,) {final _that = this;
switch (_that) {
case _BackupRestoreControllerState() when $default != null:
return $default(_that.isLoading,_that.isRestoring,_that.backupList,_that.selectedBackupIndex,_that.errorMessage,_that.backupPath);case _:
  return null;

}
}

}

/// @nodoc


class _BackupRestoreControllerState implements BackupRestoreControllerState {
  const _BackupRestoreControllerState({this.isLoading = false, this.isRestoring = false, final  List<FileSystemEntity> backupList = const [], this.selectedBackupIndex = -1, this.errorMessage = '', this.backupPath = ''}): _backupList = backupList;
  

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  bool isRestoring;
 final  List<FileSystemEntity> _backupList;
@override@JsonKey() List<FileSystemEntity> get backupList {
  if (_backupList is EqualUnmodifiableListView) return _backupList;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_backupList);
}

@override@JsonKey() final  int selectedBackupIndex;
@override@JsonKey() final  String errorMessage;
@override@JsonKey() final  String backupPath;

/// Create a copy of BackupRestoreControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$BackupRestoreControllerStateCopyWith<_BackupRestoreControllerState> get copyWith => __$BackupRestoreControllerStateCopyWithImpl<_BackupRestoreControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _BackupRestoreControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.isRestoring, isRestoring) || other.isRestoring == isRestoring)&&const DeepCollectionEquality().equals(other._backupList, _backupList)&&(identical(other.selectedBackupIndex, selectedBackupIndex) || other.selectedBackupIndex == selectedBackupIndex)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.backupPath, backupPath) || other.backupPath == backupPath));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,isRestoring,const DeepCollectionEquality().hash(_backupList),selectedBackupIndex,errorMessage,backupPath);

@override
String toString() {
  return 'BackupRestoreControllerState(isLoading: $isLoading, isRestoring: $isRestoring, backupList: $backupList, selectedBackupIndex: $selectedBackupIndex, errorMessage: $errorMessage, backupPath: $backupPath)';
}


}

/// @nodoc
abstract mixin class _$BackupRestoreControllerStateCopyWith<$Res> implements $BackupRestoreControllerStateCopyWith<$Res> {
  factory _$BackupRestoreControllerStateCopyWith(_BackupRestoreControllerState value, $Res Function(_BackupRestoreControllerState) _then) = __$BackupRestoreControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool isLoading, bool isRestoring, List<FileSystemEntity> backupList, int selectedBackupIndex, String errorMessage, String backupPath
});




}
/// @nodoc
class __$BackupRestoreControllerStateCopyWithImpl<$Res>
    implements _$BackupRestoreControllerStateCopyWith<$Res> {
  __$BackupRestoreControllerStateCopyWithImpl(this._self, this._then);

  final _BackupRestoreControllerState _self;
  final $Res Function(_BackupRestoreControllerState) _then;

/// Create a copy of BackupRestoreControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? isLoading = null,Object? isRestoring = null,Object? backupList = null,Object? selectedBackupIndex = null,Object? errorMessage = null,Object? backupPath = null,}) {
  return _then(_BackupRestoreControllerState(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,isRestoring: null == isRestoring ? _self.isRestoring : isRestoring // ignore: cast_nullable_to_non_nullable
as bool,backupList: null == backupList ? _self._backupList : backupList // ignore: cast_nullable_to_non_nullable
as List<FileSystemEntity>,selectedBackupIndex: null == selectedBackupIndex ? _self.selectedBackupIndex : selectedBackupIndex // ignore: cast_nullable_to_non_nullable
as int,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,backupPath: null == backupPath ? _self.backupPath : backupPath // ignore: cast_nullable_to_non_nullable
as String,
  ));
}


}

// dart format on
