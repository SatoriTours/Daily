// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'app_state_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$AppStateModel {

 int get currentNavIndex; bool get isAppInBackground; DateTime get lastActiveTime; bool get isGlobalLoading; String get globalErrorMessage; String get globalSuccessMessage; String get globalInfoMessage; bool get isSearchBarVisible; String get currentPage;
/// Create a copy of AppStateModel
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$AppStateModelCopyWith<AppStateModel> get copyWith => _$AppStateModelCopyWithImpl<AppStateModel>(this as AppStateModel, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is AppStateModel&&(identical(other.currentNavIndex, currentNavIndex) || other.currentNavIndex == currentNavIndex)&&(identical(other.isAppInBackground, isAppInBackground) || other.isAppInBackground == isAppInBackground)&&(identical(other.lastActiveTime, lastActiveTime) || other.lastActiveTime == lastActiveTime)&&(identical(other.isGlobalLoading, isGlobalLoading) || other.isGlobalLoading == isGlobalLoading)&&(identical(other.globalErrorMessage, globalErrorMessage) || other.globalErrorMessage == globalErrorMessage)&&(identical(other.globalSuccessMessage, globalSuccessMessage) || other.globalSuccessMessage == globalSuccessMessage)&&(identical(other.globalInfoMessage, globalInfoMessage) || other.globalInfoMessage == globalInfoMessage)&&(identical(other.isSearchBarVisible, isSearchBarVisible) || other.isSearchBarVisible == isSearchBarVisible)&&(identical(other.currentPage, currentPage) || other.currentPage == currentPage));
}


@override
int get hashCode => Object.hash(runtimeType,currentNavIndex,isAppInBackground,lastActiveTime,isGlobalLoading,globalErrorMessage,globalSuccessMessage,globalInfoMessage,isSearchBarVisible,currentPage);

@override
String toString() {
  return 'AppStateModel(currentNavIndex: $currentNavIndex, isAppInBackground: $isAppInBackground, lastActiveTime: $lastActiveTime, isGlobalLoading: $isGlobalLoading, globalErrorMessage: $globalErrorMessage, globalSuccessMessage: $globalSuccessMessage, globalInfoMessage: $globalInfoMessage, isSearchBarVisible: $isSearchBarVisible, currentPage: $currentPage)';
}


}

/// @nodoc
abstract mixin class $AppStateModelCopyWith<$Res>  {
  factory $AppStateModelCopyWith(AppStateModel value, $Res Function(AppStateModel) _then) = _$AppStateModelCopyWithImpl;
@useResult
$Res call({
 int currentNavIndex, bool isAppInBackground, DateTime lastActiveTime, bool isGlobalLoading, String globalErrorMessage, String globalSuccessMessage, String globalInfoMessage, bool isSearchBarVisible, String currentPage
});




}
/// @nodoc
class _$AppStateModelCopyWithImpl<$Res>
    implements $AppStateModelCopyWith<$Res> {
  _$AppStateModelCopyWithImpl(this._self, this._then);

  final AppStateModel _self;
  final $Res Function(AppStateModel) _then;

/// Create a copy of AppStateModel
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? currentNavIndex = null,Object? isAppInBackground = null,Object? lastActiveTime = null,Object? isGlobalLoading = null,Object? globalErrorMessage = null,Object? globalSuccessMessage = null,Object? globalInfoMessage = null,Object? isSearchBarVisible = null,Object? currentPage = null,}) {
  return _then(_self.copyWith(
currentNavIndex: null == currentNavIndex ? _self.currentNavIndex : currentNavIndex // ignore: cast_nullable_to_non_nullable
as int,isAppInBackground: null == isAppInBackground ? _self.isAppInBackground : isAppInBackground // ignore: cast_nullable_to_non_nullable
as bool,lastActiveTime: null == lastActiveTime ? _self.lastActiveTime : lastActiveTime // ignore: cast_nullable_to_non_nullable
as DateTime,isGlobalLoading: null == isGlobalLoading ? _self.isGlobalLoading : isGlobalLoading // ignore: cast_nullable_to_non_nullable
as bool,globalErrorMessage: null == globalErrorMessage ? _self.globalErrorMessage : globalErrorMessage // ignore: cast_nullable_to_non_nullable
as String,globalSuccessMessage: null == globalSuccessMessage ? _self.globalSuccessMessage : globalSuccessMessage // ignore: cast_nullable_to_non_nullable
as String,globalInfoMessage: null == globalInfoMessage ? _self.globalInfoMessage : globalInfoMessage // ignore: cast_nullable_to_non_nullable
as String,isSearchBarVisible: null == isSearchBarVisible ? _self.isSearchBarVisible : isSearchBarVisible // ignore: cast_nullable_to_non_nullable
as bool,currentPage: null == currentPage ? _self.currentPage : currentPage // ignore: cast_nullable_to_non_nullable
as String,
  ));
}

}


/// Adds pattern-matching-related methods to [AppStateModel].
extension AppStateModelPatterns on AppStateModel {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _AppStateModel value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _AppStateModel() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _AppStateModel value)  $default,){
final _that = this;
switch (_that) {
case _AppStateModel():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _AppStateModel value)?  $default,){
final _that = this;
switch (_that) {
case _AppStateModel() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( int currentNavIndex,  bool isAppInBackground,  DateTime lastActiveTime,  bool isGlobalLoading,  String globalErrorMessage,  String globalSuccessMessage,  String globalInfoMessage,  bool isSearchBarVisible,  String currentPage)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _AppStateModel() when $default != null:
return $default(_that.currentNavIndex,_that.isAppInBackground,_that.lastActiveTime,_that.isGlobalLoading,_that.globalErrorMessage,_that.globalSuccessMessage,_that.globalInfoMessage,_that.isSearchBarVisible,_that.currentPage);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( int currentNavIndex,  bool isAppInBackground,  DateTime lastActiveTime,  bool isGlobalLoading,  String globalErrorMessage,  String globalSuccessMessage,  String globalInfoMessage,  bool isSearchBarVisible,  String currentPage)  $default,) {final _that = this;
switch (_that) {
case _AppStateModel():
return $default(_that.currentNavIndex,_that.isAppInBackground,_that.lastActiveTime,_that.isGlobalLoading,_that.globalErrorMessage,_that.globalSuccessMessage,_that.globalInfoMessage,_that.isSearchBarVisible,_that.currentPage);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( int currentNavIndex,  bool isAppInBackground,  DateTime lastActiveTime,  bool isGlobalLoading,  String globalErrorMessage,  String globalSuccessMessage,  String globalInfoMessage,  bool isSearchBarVisible,  String currentPage)?  $default,) {final _that = this;
switch (_that) {
case _AppStateModel() when $default != null:
return $default(_that.currentNavIndex,_that.isAppInBackground,_that.lastActiveTime,_that.isGlobalLoading,_that.globalErrorMessage,_that.globalSuccessMessage,_that.globalInfoMessage,_that.isSearchBarVisible,_that.currentPage);case _:
  return null;

}
}

}

/// @nodoc


class _AppStateModel extends AppStateModel {
  const _AppStateModel({this.currentNavIndex = 0, this.isAppInBackground = false, required this.lastActiveTime, this.isGlobalLoading = false, this.globalErrorMessage = '', this.globalSuccessMessage = '', this.globalInfoMessage = '', this.isSearchBarVisible = false, this.currentPage = ''}): super._();
  

@override@JsonKey() final  int currentNavIndex;
@override@JsonKey() final  bool isAppInBackground;
@override final  DateTime lastActiveTime;
@override@JsonKey() final  bool isGlobalLoading;
@override@JsonKey() final  String globalErrorMessage;
@override@JsonKey() final  String globalSuccessMessage;
@override@JsonKey() final  String globalInfoMessage;
@override@JsonKey() final  bool isSearchBarVisible;
@override@JsonKey() final  String currentPage;

/// Create a copy of AppStateModel
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$AppStateModelCopyWith<_AppStateModel> get copyWith => __$AppStateModelCopyWithImpl<_AppStateModel>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _AppStateModel&&(identical(other.currentNavIndex, currentNavIndex) || other.currentNavIndex == currentNavIndex)&&(identical(other.isAppInBackground, isAppInBackground) || other.isAppInBackground == isAppInBackground)&&(identical(other.lastActiveTime, lastActiveTime) || other.lastActiveTime == lastActiveTime)&&(identical(other.isGlobalLoading, isGlobalLoading) || other.isGlobalLoading == isGlobalLoading)&&(identical(other.globalErrorMessage, globalErrorMessage) || other.globalErrorMessage == globalErrorMessage)&&(identical(other.globalSuccessMessage, globalSuccessMessage) || other.globalSuccessMessage == globalSuccessMessage)&&(identical(other.globalInfoMessage, globalInfoMessage) || other.globalInfoMessage == globalInfoMessage)&&(identical(other.isSearchBarVisible, isSearchBarVisible) || other.isSearchBarVisible == isSearchBarVisible)&&(identical(other.currentPage, currentPage) || other.currentPage == currentPage));
}


@override
int get hashCode => Object.hash(runtimeType,currentNavIndex,isAppInBackground,lastActiveTime,isGlobalLoading,globalErrorMessage,globalSuccessMessage,globalInfoMessage,isSearchBarVisible,currentPage);

@override
String toString() {
  return 'AppStateModel(currentNavIndex: $currentNavIndex, isAppInBackground: $isAppInBackground, lastActiveTime: $lastActiveTime, isGlobalLoading: $isGlobalLoading, globalErrorMessage: $globalErrorMessage, globalSuccessMessage: $globalSuccessMessage, globalInfoMessage: $globalInfoMessage, isSearchBarVisible: $isSearchBarVisible, currentPage: $currentPage)';
}


}

/// @nodoc
abstract mixin class _$AppStateModelCopyWith<$Res> implements $AppStateModelCopyWith<$Res> {
  factory _$AppStateModelCopyWith(_AppStateModel value, $Res Function(_AppStateModel) _then) = __$AppStateModelCopyWithImpl;
@override @useResult
$Res call({
 int currentNavIndex, bool isAppInBackground, DateTime lastActiveTime, bool isGlobalLoading, String globalErrorMessage, String globalSuccessMessage, String globalInfoMessage, bool isSearchBarVisible, String currentPage
});




}
/// @nodoc
class __$AppStateModelCopyWithImpl<$Res>
    implements _$AppStateModelCopyWith<$Res> {
  __$AppStateModelCopyWithImpl(this._self, this._then);

  final _AppStateModel _self;
  final $Res Function(_AppStateModel) _then;

/// Create a copy of AppStateModel
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? currentNavIndex = null,Object? isAppInBackground = null,Object? lastActiveTime = null,Object? isGlobalLoading = null,Object? globalErrorMessage = null,Object? globalSuccessMessage = null,Object? globalInfoMessage = null,Object? isSearchBarVisible = null,Object? currentPage = null,}) {
  return _then(_AppStateModel(
currentNavIndex: null == currentNavIndex ? _self.currentNavIndex : currentNavIndex // ignore: cast_nullable_to_non_nullable
as int,isAppInBackground: null == isAppInBackground ? _self.isAppInBackground : isAppInBackground // ignore: cast_nullable_to_non_nullable
as bool,lastActiveTime: null == lastActiveTime ? _self.lastActiveTime : lastActiveTime // ignore: cast_nullable_to_non_nullable
as DateTime,isGlobalLoading: null == isGlobalLoading ? _self.isGlobalLoading : isGlobalLoading // ignore: cast_nullable_to_non_nullable
as bool,globalErrorMessage: null == globalErrorMessage ? _self.globalErrorMessage : globalErrorMessage // ignore: cast_nullable_to_non_nullable
as String,globalSuccessMessage: null == globalSuccessMessage ? _self.globalSuccessMessage : globalSuccessMessage // ignore: cast_nullable_to_non_nullable
as String,globalInfoMessage: null == globalInfoMessage ? _self.globalInfoMessage : globalInfoMessage // ignore: cast_nullable_to_non_nullable
as String,isSearchBarVisible: null == isSearchBarVisible ? _self.isSearchBarVisible : isSearchBarVisible // ignore: cast_nullable_to_non_nullable
as bool,currentPage: null == currentPage ? _self.currentPage : currentPage // ignore: cast_nullable_to_non_nullable
as String,
  ));
}


}

// dart format on
