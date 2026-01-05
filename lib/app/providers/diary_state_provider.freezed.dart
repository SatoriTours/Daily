// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'diary_state_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$DiaryStateModel {

 List<DiaryModel> get diaries; bool get isLoading; int get activeDiaryId; DiaryModel? get activeDiary; String get globalTagFilter; DateTime? get globalDateFilter; Map<int, DiaryModel> get diaryUpdates;
/// Create a copy of DiaryStateModel
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$DiaryStateModelCopyWith<DiaryStateModel> get copyWith => _$DiaryStateModelCopyWithImpl<DiaryStateModel>(this as DiaryStateModel, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is DiaryStateModel&&const DeepCollectionEquality().equals(other.diaries, diaries)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.activeDiaryId, activeDiaryId) || other.activeDiaryId == activeDiaryId)&&(identical(other.activeDiary, activeDiary) || other.activeDiary == activeDiary)&&(identical(other.globalTagFilter, globalTagFilter) || other.globalTagFilter == globalTagFilter)&&(identical(other.globalDateFilter, globalDateFilter) || other.globalDateFilter == globalDateFilter)&&const DeepCollectionEquality().equals(other.diaryUpdates, diaryUpdates));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(diaries),isLoading,activeDiaryId,activeDiary,globalTagFilter,globalDateFilter,const DeepCollectionEquality().hash(diaryUpdates));

@override
String toString() {
  return 'DiaryStateModel(diaries: $diaries, isLoading: $isLoading, activeDiaryId: $activeDiaryId, activeDiary: $activeDiary, globalTagFilter: $globalTagFilter, globalDateFilter: $globalDateFilter, diaryUpdates: $diaryUpdates)';
}


}

/// @nodoc
abstract mixin class $DiaryStateModelCopyWith<$Res>  {
  factory $DiaryStateModelCopyWith(DiaryStateModel value, $Res Function(DiaryStateModel) _then) = _$DiaryStateModelCopyWithImpl;
@useResult
$Res call({
 List<DiaryModel> diaries, bool isLoading, int activeDiaryId, DiaryModel? activeDiary, String globalTagFilter, DateTime? globalDateFilter, Map<int, DiaryModel> diaryUpdates
});




}
/// @nodoc
class _$DiaryStateModelCopyWithImpl<$Res>
    implements $DiaryStateModelCopyWith<$Res> {
  _$DiaryStateModelCopyWithImpl(this._self, this._then);

  final DiaryStateModel _self;
  final $Res Function(DiaryStateModel) _then;

/// Create a copy of DiaryStateModel
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? diaries = null,Object? isLoading = null,Object? activeDiaryId = null,Object? activeDiary = freezed,Object? globalTagFilter = null,Object? globalDateFilter = freezed,Object? diaryUpdates = null,}) {
  return _then(_self.copyWith(
diaries: null == diaries ? _self.diaries : diaries // ignore: cast_nullable_to_non_nullable
as List<DiaryModel>,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,activeDiaryId: null == activeDiaryId ? _self.activeDiaryId : activeDiaryId // ignore: cast_nullable_to_non_nullable
as int,activeDiary: freezed == activeDiary ? _self.activeDiary : activeDiary // ignore: cast_nullable_to_non_nullable
as DiaryModel?,globalTagFilter: null == globalTagFilter ? _self.globalTagFilter : globalTagFilter // ignore: cast_nullable_to_non_nullable
as String,globalDateFilter: freezed == globalDateFilter ? _self.globalDateFilter : globalDateFilter // ignore: cast_nullable_to_non_nullable
as DateTime?,diaryUpdates: null == diaryUpdates ? _self.diaryUpdates : diaryUpdates // ignore: cast_nullable_to_non_nullable
as Map<int, DiaryModel>,
  ));
}

}


/// Adds pattern-matching-related methods to [DiaryStateModel].
extension DiaryStateModelPatterns on DiaryStateModel {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _DiaryStateModel value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _DiaryStateModel() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _DiaryStateModel value)  $default,){
final _that = this;
switch (_that) {
case _DiaryStateModel():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _DiaryStateModel value)?  $default,){
final _that = this;
switch (_that) {
case _DiaryStateModel() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( List<DiaryModel> diaries,  bool isLoading,  int activeDiaryId,  DiaryModel? activeDiary,  String globalTagFilter,  DateTime? globalDateFilter,  Map<int, DiaryModel> diaryUpdates)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _DiaryStateModel() when $default != null:
return $default(_that.diaries,_that.isLoading,_that.activeDiaryId,_that.activeDiary,_that.globalTagFilter,_that.globalDateFilter,_that.diaryUpdates);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( List<DiaryModel> diaries,  bool isLoading,  int activeDiaryId,  DiaryModel? activeDiary,  String globalTagFilter,  DateTime? globalDateFilter,  Map<int, DiaryModel> diaryUpdates)  $default,) {final _that = this;
switch (_that) {
case _DiaryStateModel():
return $default(_that.diaries,_that.isLoading,_that.activeDiaryId,_that.activeDiary,_that.globalTagFilter,_that.globalDateFilter,_that.diaryUpdates);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( List<DiaryModel> diaries,  bool isLoading,  int activeDiaryId,  DiaryModel? activeDiary,  String globalTagFilter,  DateTime? globalDateFilter,  Map<int, DiaryModel> diaryUpdates)?  $default,) {final _that = this;
switch (_that) {
case _DiaryStateModel() when $default != null:
return $default(_that.diaries,_that.isLoading,_that.activeDiaryId,_that.activeDiary,_that.globalTagFilter,_that.globalDateFilter,_that.diaryUpdates);case _:
  return null;

}
}

}

/// @nodoc


class _DiaryStateModel extends DiaryStateModel {
  const _DiaryStateModel({final  List<DiaryModel> diaries = const [], this.isLoading = false, this.activeDiaryId = -1, this.activeDiary, this.globalTagFilter = '', this.globalDateFilter, final  Map<int, DiaryModel> diaryUpdates = const {}}): _diaries = diaries,_diaryUpdates = diaryUpdates,super._();
  

 final  List<DiaryModel> _diaries;
@override@JsonKey() List<DiaryModel> get diaries {
  if (_diaries is EqualUnmodifiableListView) return _diaries;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_diaries);
}

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  int activeDiaryId;
@override final  DiaryModel? activeDiary;
@override@JsonKey() final  String globalTagFilter;
@override final  DateTime? globalDateFilter;
 final  Map<int, DiaryModel> _diaryUpdates;
@override@JsonKey() Map<int, DiaryModel> get diaryUpdates {
  if (_diaryUpdates is EqualUnmodifiableMapView) return _diaryUpdates;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableMapView(_diaryUpdates);
}


/// Create a copy of DiaryStateModel
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$DiaryStateModelCopyWith<_DiaryStateModel> get copyWith => __$DiaryStateModelCopyWithImpl<_DiaryStateModel>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _DiaryStateModel&&const DeepCollectionEquality().equals(other._diaries, _diaries)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.activeDiaryId, activeDiaryId) || other.activeDiaryId == activeDiaryId)&&(identical(other.activeDiary, activeDiary) || other.activeDiary == activeDiary)&&(identical(other.globalTagFilter, globalTagFilter) || other.globalTagFilter == globalTagFilter)&&(identical(other.globalDateFilter, globalDateFilter) || other.globalDateFilter == globalDateFilter)&&const DeepCollectionEquality().equals(other._diaryUpdates, _diaryUpdates));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(_diaries),isLoading,activeDiaryId,activeDiary,globalTagFilter,globalDateFilter,const DeepCollectionEquality().hash(_diaryUpdates));

@override
String toString() {
  return 'DiaryStateModel(diaries: $diaries, isLoading: $isLoading, activeDiaryId: $activeDiaryId, activeDiary: $activeDiary, globalTagFilter: $globalTagFilter, globalDateFilter: $globalDateFilter, diaryUpdates: $diaryUpdates)';
}


}

/// @nodoc
abstract mixin class _$DiaryStateModelCopyWith<$Res> implements $DiaryStateModelCopyWith<$Res> {
  factory _$DiaryStateModelCopyWith(_DiaryStateModel value, $Res Function(_DiaryStateModel) _then) = __$DiaryStateModelCopyWithImpl;
@override @useResult
$Res call({
 List<DiaryModel> diaries, bool isLoading, int activeDiaryId, DiaryModel? activeDiary, String globalTagFilter, DateTime? globalDateFilter, Map<int, DiaryModel> diaryUpdates
});




}
/// @nodoc
class __$DiaryStateModelCopyWithImpl<$Res>
    implements _$DiaryStateModelCopyWith<$Res> {
  __$DiaryStateModelCopyWithImpl(this._self, this._then);

  final _DiaryStateModel _self;
  final $Res Function(_DiaryStateModel) _then;

/// Create a copy of DiaryStateModel
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? diaries = null,Object? isLoading = null,Object? activeDiaryId = null,Object? activeDiary = freezed,Object? globalTagFilter = null,Object? globalDateFilter = freezed,Object? diaryUpdates = null,}) {
  return _then(_DiaryStateModel(
diaries: null == diaries ? _self._diaries : diaries // ignore: cast_nullable_to_non_nullable
as List<DiaryModel>,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,activeDiaryId: null == activeDiaryId ? _self.activeDiaryId : activeDiaryId // ignore: cast_nullable_to_non_nullable
as int,activeDiary: freezed == activeDiary ? _self.activeDiary : activeDiary // ignore: cast_nullable_to_non_nullable
as DiaryModel?,globalTagFilter: null == globalTagFilter ? _self.globalTagFilter : globalTagFilter // ignore: cast_nullable_to_non_nullable
as String,globalDateFilter: freezed == globalDateFilter ? _self.globalDateFilter : globalDateFilter // ignore: cast_nullable_to_non_nullable
as DateTime?,diaryUpdates: null == diaryUpdates ? _self._diaryUpdates : diaryUpdates // ignore: cast_nullable_to_non_nullable
as Map<int, DiaryModel>,
  ));
}


}

// dart format on
