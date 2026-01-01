// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'settings_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$SettingsControllerState {

/// Web服务地址
 String get webServiceAddress;/// Web访问URL
 String get webAccessUrl;/// 是否正在加载页面
 bool get isPageLoading;/// 应用版本号
 String get appVersion;/// WebSocket连接状态
 bool get isWebSocketConnected;/// 是否正在下载图片
 bool get isDownloadingImages;/// 下载进度：当前已处理的文章数量
 int get downloadProgress;/// 下载进度：总文章数量
 int get downloadTotal;/// 下载成功的数量
 int get downloadSuccessCount;/// 下载失败的数量
 int get downloadFailCount;
/// Create a copy of SettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$SettingsControllerStateCopyWith<SettingsControllerState> get copyWith => _$SettingsControllerStateCopyWithImpl<SettingsControllerState>(this as SettingsControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is SettingsControllerState&&(identical(other.webServiceAddress, webServiceAddress) || other.webServiceAddress == webServiceAddress)&&(identical(other.webAccessUrl, webAccessUrl) || other.webAccessUrl == webAccessUrl)&&(identical(other.isPageLoading, isPageLoading) || other.isPageLoading == isPageLoading)&&(identical(other.appVersion, appVersion) || other.appVersion == appVersion)&&(identical(other.isWebSocketConnected, isWebSocketConnected) || other.isWebSocketConnected == isWebSocketConnected)&&(identical(other.isDownloadingImages, isDownloadingImages) || other.isDownloadingImages == isDownloadingImages)&&(identical(other.downloadProgress, downloadProgress) || other.downloadProgress == downloadProgress)&&(identical(other.downloadTotal, downloadTotal) || other.downloadTotal == downloadTotal)&&(identical(other.downloadSuccessCount, downloadSuccessCount) || other.downloadSuccessCount == downloadSuccessCount)&&(identical(other.downloadFailCount, downloadFailCount) || other.downloadFailCount == downloadFailCount));
}


@override
int get hashCode => Object.hash(runtimeType,webServiceAddress,webAccessUrl,isPageLoading,appVersion,isWebSocketConnected,isDownloadingImages,downloadProgress,downloadTotal,downloadSuccessCount,downloadFailCount);

@override
String toString() {
  return 'SettingsControllerState(webServiceAddress: $webServiceAddress, webAccessUrl: $webAccessUrl, isPageLoading: $isPageLoading, appVersion: $appVersion, isWebSocketConnected: $isWebSocketConnected, isDownloadingImages: $isDownloadingImages, downloadProgress: $downloadProgress, downloadTotal: $downloadTotal, downloadSuccessCount: $downloadSuccessCount, downloadFailCount: $downloadFailCount)';
}


}

/// @nodoc
abstract mixin class $SettingsControllerStateCopyWith<$Res>  {
  factory $SettingsControllerStateCopyWith(SettingsControllerState value, $Res Function(SettingsControllerState) _then) = _$SettingsControllerStateCopyWithImpl;
@useResult
$Res call({
 String webServiceAddress, String webAccessUrl, bool isPageLoading, String appVersion, bool isWebSocketConnected, bool isDownloadingImages, int downloadProgress, int downloadTotal, int downloadSuccessCount, int downloadFailCount
});




}
/// @nodoc
class _$SettingsControllerStateCopyWithImpl<$Res>
    implements $SettingsControllerStateCopyWith<$Res> {
  _$SettingsControllerStateCopyWithImpl(this._self, this._then);

  final SettingsControllerState _self;
  final $Res Function(SettingsControllerState) _then;

/// Create a copy of SettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? webServiceAddress = null,Object? webAccessUrl = null,Object? isPageLoading = null,Object? appVersion = null,Object? isWebSocketConnected = null,Object? isDownloadingImages = null,Object? downloadProgress = null,Object? downloadTotal = null,Object? downloadSuccessCount = null,Object? downloadFailCount = null,}) {
  return _then(_self.copyWith(
webServiceAddress: null == webServiceAddress ? _self.webServiceAddress : webServiceAddress // ignore: cast_nullable_to_non_nullable
as String,webAccessUrl: null == webAccessUrl ? _self.webAccessUrl : webAccessUrl // ignore: cast_nullable_to_non_nullable
as String,isPageLoading: null == isPageLoading ? _self.isPageLoading : isPageLoading // ignore: cast_nullable_to_non_nullable
as bool,appVersion: null == appVersion ? _self.appVersion : appVersion // ignore: cast_nullable_to_non_nullable
as String,isWebSocketConnected: null == isWebSocketConnected ? _self.isWebSocketConnected : isWebSocketConnected // ignore: cast_nullable_to_non_nullable
as bool,isDownloadingImages: null == isDownloadingImages ? _self.isDownloadingImages : isDownloadingImages // ignore: cast_nullable_to_non_nullable
as bool,downloadProgress: null == downloadProgress ? _self.downloadProgress : downloadProgress // ignore: cast_nullable_to_non_nullable
as int,downloadTotal: null == downloadTotal ? _self.downloadTotal : downloadTotal // ignore: cast_nullable_to_non_nullable
as int,downloadSuccessCount: null == downloadSuccessCount ? _self.downloadSuccessCount : downloadSuccessCount // ignore: cast_nullable_to_non_nullable
as int,downloadFailCount: null == downloadFailCount ? _self.downloadFailCount : downloadFailCount // ignore: cast_nullable_to_non_nullable
as int,
  ));
}

}


/// Adds pattern-matching-related methods to [SettingsControllerState].
extension SettingsControllerStatePatterns on SettingsControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _SettingsControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _SettingsControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _SettingsControllerState value)  $default,){
final _that = this;
switch (_that) {
case _SettingsControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _SettingsControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _SettingsControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( String webServiceAddress,  String webAccessUrl,  bool isPageLoading,  String appVersion,  bool isWebSocketConnected,  bool isDownloadingImages,  int downloadProgress,  int downloadTotal,  int downloadSuccessCount,  int downloadFailCount)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _SettingsControllerState() when $default != null:
return $default(_that.webServiceAddress,_that.webAccessUrl,_that.isPageLoading,_that.appVersion,_that.isWebSocketConnected,_that.isDownloadingImages,_that.downloadProgress,_that.downloadTotal,_that.downloadSuccessCount,_that.downloadFailCount);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( String webServiceAddress,  String webAccessUrl,  bool isPageLoading,  String appVersion,  bool isWebSocketConnected,  bool isDownloadingImages,  int downloadProgress,  int downloadTotal,  int downloadSuccessCount,  int downloadFailCount)  $default,) {final _that = this;
switch (_that) {
case _SettingsControllerState():
return $default(_that.webServiceAddress,_that.webAccessUrl,_that.isPageLoading,_that.appVersion,_that.isWebSocketConnected,_that.isDownloadingImages,_that.downloadProgress,_that.downloadTotal,_that.downloadSuccessCount,_that.downloadFailCount);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( String webServiceAddress,  String webAccessUrl,  bool isPageLoading,  String appVersion,  bool isWebSocketConnected,  bool isDownloadingImages,  int downloadProgress,  int downloadTotal,  int downloadSuccessCount,  int downloadFailCount)?  $default,) {final _that = this;
switch (_that) {
case _SettingsControllerState() when $default != null:
return $default(_that.webServiceAddress,_that.webAccessUrl,_that.isPageLoading,_that.appVersion,_that.isWebSocketConnected,_that.isDownloadingImages,_that.downloadProgress,_that.downloadTotal,_that.downloadSuccessCount,_that.downloadFailCount);case _:
  return null;

}
}

}

/// @nodoc


class _SettingsControllerState implements SettingsControllerState {
  const _SettingsControllerState({this.webServiceAddress = '', this.webAccessUrl = '', this.isPageLoading = true, this.appVersion = '', this.isWebSocketConnected = false, this.isDownloadingImages = false, this.downloadProgress = 0, this.downloadTotal = 0, this.downloadSuccessCount = 0, this.downloadFailCount = 0});
  

/// Web服务地址
@override@JsonKey() final  String webServiceAddress;
/// Web访问URL
@override@JsonKey() final  String webAccessUrl;
/// 是否正在加载页面
@override@JsonKey() final  bool isPageLoading;
/// 应用版本号
@override@JsonKey() final  String appVersion;
/// WebSocket连接状态
@override@JsonKey() final  bool isWebSocketConnected;
/// 是否正在下载图片
@override@JsonKey() final  bool isDownloadingImages;
/// 下载进度：当前已处理的文章数量
@override@JsonKey() final  int downloadProgress;
/// 下载进度：总文章数量
@override@JsonKey() final  int downloadTotal;
/// 下载成功的数量
@override@JsonKey() final  int downloadSuccessCount;
/// 下载失败的数量
@override@JsonKey() final  int downloadFailCount;

/// Create a copy of SettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$SettingsControllerStateCopyWith<_SettingsControllerState> get copyWith => __$SettingsControllerStateCopyWithImpl<_SettingsControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _SettingsControllerState&&(identical(other.webServiceAddress, webServiceAddress) || other.webServiceAddress == webServiceAddress)&&(identical(other.webAccessUrl, webAccessUrl) || other.webAccessUrl == webAccessUrl)&&(identical(other.isPageLoading, isPageLoading) || other.isPageLoading == isPageLoading)&&(identical(other.appVersion, appVersion) || other.appVersion == appVersion)&&(identical(other.isWebSocketConnected, isWebSocketConnected) || other.isWebSocketConnected == isWebSocketConnected)&&(identical(other.isDownloadingImages, isDownloadingImages) || other.isDownloadingImages == isDownloadingImages)&&(identical(other.downloadProgress, downloadProgress) || other.downloadProgress == downloadProgress)&&(identical(other.downloadTotal, downloadTotal) || other.downloadTotal == downloadTotal)&&(identical(other.downloadSuccessCount, downloadSuccessCount) || other.downloadSuccessCount == downloadSuccessCount)&&(identical(other.downloadFailCount, downloadFailCount) || other.downloadFailCount == downloadFailCount));
}


@override
int get hashCode => Object.hash(runtimeType,webServiceAddress,webAccessUrl,isPageLoading,appVersion,isWebSocketConnected,isDownloadingImages,downloadProgress,downloadTotal,downloadSuccessCount,downloadFailCount);

@override
String toString() {
  return 'SettingsControllerState(webServiceAddress: $webServiceAddress, webAccessUrl: $webAccessUrl, isPageLoading: $isPageLoading, appVersion: $appVersion, isWebSocketConnected: $isWebSocketConnected, isDownloadingImages: $isDownloadingImages, downloadProgress: $downloadProgress, downloadTotal: $downloadTotal, downloadSuccessCount: $downloadSuccessCount, downloadFailCount: $downloadFailCount)';
}


}

/// @nodoc
abstract mixin class _$SettingsControllerStateCopyWith<$Res> implements $SettingsControllerStateCopyWith<$Res> {
  factory _$SettingsControllerStateCopyWith(_SettingsControllerState value, $Res Function(_SettingsControllerState) _then) = __$SettingsControllerStateCopyWithImpl;
@override @useResult
$Res call({
 String webServiceAddress, String webAccessUrl, bool isPageLoading, String appVersion, bool isWebSocketConnected, bool isDownloadingImages, int downloadProgress, int downloadTotal, int downloadSuccessCount, int downloadFailCount
});




}
/// @nodoc
class __$SettingsControllerStateCopyWithImpl<$Res>
    implements _$SettingsControllerStateCopyWith<$Res> {
  __$SettingsControllerStateCopyWithImpl(this._self, this._then);

  final _SettingsControllerState _self;
  final $Res Function(_SettingsControllerState) _then;

/// Create a copy of SettingsControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? webServiceAddress = null,Object? webAccessUrl = null,Object? isPageLoading = null,Object? appVersion = null,Object? isWebSocketConnected = null,Object? isDownloadingImages = null,Object? downloadProgress = null,Object? downloadTotal = null,Object? downloadSuccessCount = null,Object? downloadFailCount = null,}) {
  return _then(_SettingsControllerState(
webServiceAddress: null == webServiceAddress ? _self.webServiceAddress : webServiceAddress // ignore: cast_nullable_to_non_nullable
as String,webAccessUrl: null == webAccessUrl ? _self.webAccessUrl : webAccessUrl // ignore: cast_nullable_to_non_nullable
as String,isPageLoading: null == isPageLoading ? _self.isPageLoading : isPageLoading // ignore: cast_nullable_to_non_nullable
as bool,appVersion: null == appVersion ? _self.appVersion : appVersion // ignore: cast_nullable_to_non_nullable
as String,isWebSocketConnected: null == isWebSocketConnected ? _self.isWebSocketConnected : isWebSocketConnected // ignore: cast_nullable_to_non_nullable
as bool,isDownloadingImages: null == isDownloadingImages ? _self.isDownloadingImages : isDownloadingImages // ignore: cast_nullable_to_non_nullable
as bool,downloadProgress: null == downloadProgress ? _self.downloadProgress : downloadProgress // ignore: cast_nullable_to_non_nullable
as int,downloadTotal: null == downloadTotal ? _self.downloadTotal : downloadTotal // ignore: cast_nullable_to_non_nullable
as int,downloadSuccessCount: null == downloadSuccessCount ? _self.downloadSuccessCount : downloadSuccessCount // ignore: cast_nullable_to_non_nullable
as int,downloadFailCount: null == downloadFailCount ? _self.downloadFailCount : downloadFailCount // ignore: cast_nullable_to_non_nullable
as int,
  ));
}


}

// dart format on
