// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'weekly_summary_controller_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$WeeklySummaryControllerState {

 List<WeeklySummaryModel> get summaries; WeeklySummaryModel? get currentSummary; bool get isGenerating; String get generatingMessage; bool get isLoading;
/// Create a copy of WeeklySummaryControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$WeeklySummaryControllerStateCopyWith<WeeklySummaryControllerState> get copyWith => _$WeeklySummaryControllerStateCopyWithImpl<WeeklySummaryControllerState>(this as WeeklySummaryControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is WeeklySummaryControllerState&&const DeepCollectionEquality().equals(other.summaries, summaries)&&(identical(other.currentSummary, currentSummary) || other.currentSummary == currentSummary)&&(identical(other.isGenerating, isGenerating) || other.isGenerating == isGenerating)&&(identical(other.generatingMessage, generatingMessage) || other.generatingMessage == generatingMessage)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(summaries),currentSummary,isGenerating,generatingMessage,isLoading);

@override
String toString() {
  return 'WeeklySummaryControllerState(summaries: $summaries, currentSummary: $currentSummary, isGenerating: $isGenerating, generatingMessage: $generatingMessage, isLoading: $isLoading)';
}


}

/// @nodoc
abstract mixin class $WeeklySummaryControllerStateCopyWith<$Res>  {
  factory $WeeklySummaryControllerStateCopyWith(WeeklySummaryControllerState value, $Res Function(WeeklySummaryControllerState) _then) = _$WeeklySummaryControllerStateCopyWithImpl;
@useResult
$Res call({
 List<WeeklySummaryModel> summaries, WeeklySummaryModel? currentSummary, bool isGenerating, String generatingMessage, bool isLoading
});




}
/// @nodoc
class _$WeeklySummaryControllerStateCopyWithImpl<$Res>
    implements $WeeklySummaryControllerStateCopyWith<$Res> {
  _$WeeklySummaryControllerStateCopyWithImpl(this._self, this._then);

  final WeeklySummaryControllerState _self;
  final $Res Function(WeeklySummaryControllerState) _then;

/// Create a copy of WeeklySummaryControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? summaries = null,Object? currentSummary = freezed,Object? isGenerating = null,Object? generatingMessage = null,Object? isLoading = null,}) {
  return _then(_self.copyWith(
summaries: null == summaries ? _self.summaries : summaries // ignore: cast_nullable_to_non_nullable
as List<WeeklySummaryModel>,currentSummary: freezed == currentSummary ? _self.currentSummary : currentSummary // ignore: cast_nullable_to_non_nullable
as WeeklySummaryModel?,isGenerating: null == isGenerating ? _self.isGenerating : isGenerating // ignore: cast_nullable_to_non_nullable
as bool,generatingMessage: null == generatingMessage ? _self.generatingMessage : generatingMessage // ignore: cast_nullable_to_non_nullable
as String,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}

}


/// Adds pattern-matching-related methods to [WeeklySummaryControllerState].
extension WeeklySummaryControllerStatePatterns on WeeklySummaryControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _WeeklySummaryControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _WeeklySummaryControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _WeeklySummaryControllerState value)  $default,){
final _that = this;
switch (_that) {
case _WeeklySummaryControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _WeeklySummaryControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _WeeklySummaryControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( List<WeeklySummaryModel> summaries,  WeeklySummaryModel? currentSummary,  bool isGenerating,  String generatingMessage,  bool isLoading)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _WeeklySummaryControllerState() when $default != null:
return $default(_that.summaries,_that.currentSummary,_that.isGenerating,_that.generatingMessage,_that.isLoading);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( List<WeeklySummaryModel> summaries,  WeeklySummaryModel? currentSummary,  bool isGenerating,  String generatingMessage,  bool isLoading)  $default,) {final _that = this;
switch (_that) {
case _WeeklySummaryControllerState():
return $default(_that.summaries,_that.currentSummary,_that.isGenerating,_that.generatingMessage,_that.isLoading);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( List<WeeklySummaryModel> summaries,  WeeklySummaryModel? currentSummary,  bool isGenerating,  String generatingMessage,  bool isLoading)?  $default,) {final _that = this;
switch (_that) {
case _WeeklySummaryControllerState() when $default != null:
return $default(_that.summaries,_that.currentSummary,_that.isGenerating,_that.generatingMessage,_that.isLoading);case _:
  return null;

}
}

}

/// @nodoc


class _WeeklySummaryControllerState implements WeeklySummaryControllerState {
  const _WeeklySummaryControllerState({final  List<WeeklySummaryModel> summaries = const [], this.currentSummary, this.isGenerating = false, this.generatingMessage = '', this.isLoading = false}): _summaries = summaries;
  

 final  List<WeeklySummaryModel> _summaries;
@override@JsonKey() List<WeeklySummaryModel> get summaries {
  if (_summaries is EqualUnmodifiableListView) return _summaries;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_summaries);
}

@override final  WeeklySummaryModel? currentSummary;
@override@JsonKey() final  bool isGenerating;
@override@JsonKey() final  String generatingMessage;
@override@JsonKey() final  bool isLoading;

/// Create a copy of WeeklySummaryControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$WeeklySummaryControllerStateCopyWith<_WeeklySummaryControllerState> get copyWith => __$WeeklySummaryControllerStateCopyWithImpl<_WeeklySummaryControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _WeeklySummaryControllerState&&const DeepCollectionEquality().equals(other._summaries, _summaries)&&(identical(other.currentSummary, currentSummary) || other.currentSummary == currentSummary)&&(identical(other.isGenerating, isGenerating) || other.isGenerating == isGenerating)&&(identical(other.generatingMessage, generatingMessage) || other.generatingMessage == generatingMessage)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(_summaries),currentSummary,isGenerating,generatingMessage,isLoading);

@override
String toString() {
  return 'WeeklySummaryControllerState(summaries: $summaries, currentSummary: $currentSummary, isGenerating: $isGenerating, generatingMessage: $generatingMessage, isLoading: $isLoading)';
}


}

/// @nodoc
abstract mixin class _$WeeklySummaryControllerStateCopyWith<$Res> implements $WeeklySummaryControllerStateCopyWith<$Res> {
  factory _$WeeklySummaryControllerStateCopyWith(_WeeklySummaryControllerState value, $Res Function(_WeeklySummaryControllerState) _then) = __$WeeklySummaryControllerStateCopyWithImpl;
@override @useResult
$Res call({
 List<WeeklySummaryModel> summaries, WeeklySummaryModel? currentSummary, bool isGenerating, String generatingMessage, bool isLoading
});




}
/// @nodoc
class __$WeeklySummaryControllerStateCopyWithImpl<$Res>
    implements _$WeeklySummaryControllerStateCopyWith<$Res> {
  __$WeeklySummaryControllerStateCopyWithImpl(this._self, this._then);

  final _WeeklySummaryControllerState _self;
  final $Res Function(_WeeklySummaryControllerState) _then;

/// Create a copy of WeeklySummaryControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? summaries = null,Object? currentSummary = freezed,Object? isGenerating = null,Object? generatingMessage = null,Object? isLoading = null,}) {
  return _then(_WeeklySummaryControllerState(
summaries: null == summaries ? _self._summaries : summaries // ignore: cast_nullable_to_non_nullable
as List<WeeklySummaryModel>,currentSummary: freezed == currentSummary ? _self.currentSummary : currentSummary // ignore: cast_nullable_to_non_nullable
as WeeklySummaryModel?,isGenerating: null == isGenerating ? _self.isGenerating : isGenerating // ignore: cast_nullable_to_non_nullable
as bool,generatingMessage: null == generatingMessage ? _self.generatingMessage : generatingMessage // ignore: cast_nullable_to_non_nullable
as String,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,
  ));
}


}

// dart format on
