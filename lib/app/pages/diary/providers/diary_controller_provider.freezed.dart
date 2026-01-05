// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'diary_controller_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$DiaryControllerState {

 DateTime? get selectedDate; String get searchQuery; bool get isSearchVisible; DateTime? get selectedFilterDate; String get currentTag; List<String> get tags; bool get isLoadingDiaries; ScrollController? get scrollController; TextEditingController? get searchController; FocusNode? get searchFocusNode; TextEditingController? get contentController;
/// Create a copy of DiaryControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$DiaryControllerStateCopyWith<DiaryControllerState> get copyWith => _$DiaryControllerStateCopyWithImpl<DiaryControllerState>(this as DiaryControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is DiaryControllerState&&(identical(other.selectedDate, selectedDate) || other.selectedDate == selectedDate)&&(identical(other.searchQuery, searchQuery) || other.searchQuery == searchQuery)&&(identical(other.isSearchVisible, isSearchVisible) || other.isSearchVisible == isSearchVisible)&&(identical(other.selectedFilterDate, selectedFilterDate) || other.selectedFilterDate == selectedFilterDate)&&(identical(other.currentTag, currentTag) || other.currentTag == currentTag)&&const DeepCollectionEquality().equals(other.tags, tags)&&(identical(other.isLoadingDiaries, isLoadingDiaries) || other.isLoadingDiaries == isLoadingDiaries)&&(identical(other.scrollController, scrollController) || other.scrollController == scrollController)&&(identical(other.searchController, searchController) || other.searchController == searchController)&&(identical(other.searchFocusNode, searchFocusNode) || other.searchFocusNode == searchFocusNode)&&(identical(other.contentController, contentController) || other.contentController == contentController));
}


@override
int get hashCode => Object.hash(runtimeType,selectedDate,searchQuery,isSearchVisible,selectedFilterDate,currentTag,const DeepCollectionEquality().hash(tags),isLoadingDiaries,scrollController,searchController,searchFocusNode,contentController);

@override
String toString() {
  return 'DiaryControllerState(selectedDate: $selectedDate, searchQuery: $searchQuery, isSearchVisible: $isSearchVisible, selectedFilterDate: $selectedFilterDate, currentTag: $currentTag, tags: $tags, isLoadingDiaries: $isLoadingDiaries, scrollController: $scrollController, searchController: $searchController, searchFocusNode: $searchFocusNode, contentController: $contentController)';
}


}

/// @nodoc
abstract mixin class $DiaryControllerStateCopyWith<$Res>  {
  factory $DiaryControllerStateCopyWith(DiaryControllerState value, $Res Function(DiaryControllerState) _then) = _$DiaryControllerStateCopyWithImpl;
@useResult
$Res call({
 DateTime? selectedDate, String searchQuery, bool isSearchVisible, DateTime? selectedFilterDate, String currentTag, List<String> tags, bool isLoadingDiaries, ScrollController? scrollController, TextEditingController? searchController, FocusNode? searchFocusNode, TextEditingController? contentController
});




}
/// @nodoc
class _$DiaryControllerStateCopyWithImpl<$Res>
    implements $DiaryControllerStateCopyWith<$Res> {
  _$DiaryControllerStateCopyWithImpl(this._self, this._then);

  final DiaryControllerState _self;
  final $Res Function(DiaryControllerState) _then;

/// Create a copy of DiaryControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? selectedDate = freezed,Object? searchQuery = null,Object? isSearchVisible = null,Object? selectedFilterDate = freezed,Object? currentTag = null,Object? tags = null,Object? isLoadingDiaries = null,Object? scrollController = freezed,Object? searchController = freezed,Object? searchFocusNode = freezed,Object? contentController = freezed,}) {
  return _then(_self.copyWith(
selectedDate: freezed == selectedDate ? _self.selectedDate : selectedDate // ignore: cast_nullable_to_non_nullable
as DateTime?,searchQuery: null == searchQuery ? _self.searchQuery : searchQuery // ignore: cast_nullable_to_non_nullable
as String,isSearchVisible: null == isSearchVisible ? _self.isSearchVisible : isSearchVisible // ignore: cast_nullable_to_non_nullable
as bool,selectedFilterDate: freezed == selectedFilterDate ? _self.selectedFilterDate : selectedFilterDate // ignore: cast_nullable_to_non_nullable
as DateTime?,currentTag: null == currentTag ? _self.currentTag : currentTag // ignore: cast_nullable_to_non_nullable
as String,tags: null == tags ? _self.tags : tags // ignore: cast_nullable_to_non_nullable
as List<String>,isLoadingDiaries: null == isLoadingDiaries ? _self.isLoadingDiaries : isLoadingDiaries // ignore: cast_nullable_to_non_nullable
as bool,scrollController: freezed == scrollController ? _self.scrollController : scrollController // ignore: cast_nullable_to_non_nullable
as ScrollController?,searchController: freezed == searchController ? _self.searchController : searchController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,searchFocusNode: freezed == searchFocusNode ? _self.searchFocusNode : searchFocusNode // ignore: cast_nullable_to_non_nullable
as FocusNode?,contentController: freezed == contentController ? _self.contentController : contentController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,
  ));
}

}


/// Adds pattern-matching-related methods to [DiaryControllerState].
extension DiaryControllerStatePatterns on DiaryControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _DiaryControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _DiaryControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _DiaryControllerState value)  $default,){
final _that = this;
switch (_that) {
case _DiaryControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _DiaryControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _DiaryControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( DateTime? selectedDate,  String searchQuery,  bool isSearchVisible,  DateTime? selectedFilterDate,  String currentTag,  List<String> tags,  bool isLoadingDiaries,  ScrollController? scrollController,  TextEditingController? searchController,  FocusNode? searchFocusNode,  TextEditingController? contentController)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _DiaryControllerState() when $default != null:
return $default(_that.selectedDate,_that.searchQuery,_that.isSearchVisible,_that.selectedFilterDate,_that.currentTag,_that.tags,_that.isLoadingDiaries,_that.scrollController,_that.searchController,_that.searchFocusNode,_that.contentController);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( DateTime? selectedDate,  String searchQuery,  bool isSearchVisible,  DateTime? selectedFilterDate,  String currentTag,  List<String> tags,  bool isLoadingDiaries,  ScrollController? scrollController,  TextEditingController? searchController,  FocusNode? searchFocusNode,  TextEditingController? contentController)  $default,) {final _that = this;
switch (_that) {
case _DiaryControllerState():
return $default(_that.selectedDate,_that.searchQuery,_that.isSearchVisible,_that.selectedFilterDate,_that.currentTag,_that.tags,_that.isLoadingDiaries,_that.scrollController,_that.searchController,_that.searchFocusNode,_that.contentController);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( DateTime? selectedDate,  String searchQuery,  bool isSearchVisible,  DateTime? selectedFilterDate,  String currentTag,  List<String> tags,  bool isLoadingDiaries,  ScrollController? scrollController,  TextEditingController? searchController,  FocusNode? searchFocusNode,  TextEditingController? contentController)?  $default,) {final _that = this;
switch (_that) {
case _DiaryControllerState() when $default != null:
return $default(_that.selectedDate,_that.searchQuery,_that.isSearchVisible,_that.selectedFilterDate,_that.currentTag,_that.tags,_that.isLoadingDiaries,_that.scrollController,_that.searchController,_that.searchFocusNode,_that.contentController);case _:
  return null;

}
}

}

/// @nodoc


class _DiaryControllerState extends DiaryControllerState {
  const _DiaryControllerState({this.selectedDate, this.searchQuery = '', this.isSearchVisible = false, this.selectedFilterDate, this.currentTag = '', final  List<String> tags = const [], this.isLoadingDiaries = false, this.scrollController, this.searchController, this.searchFocusNode, this.contentController}): _tags = tags,super._();
  

@override final  DateTime? selectedDate;
@override@JsonKey() final  String searchQuery;
@override@JsonKey() final  bool isSearchVisible;
@override final  DateTime? selectedFilterDate;
@override@JsonKey() final  String currentTag;
 final  List<String> _tags;
@override@JsonKey() List<String> get tags {
  if (_tags is EqualUnmodifiableListView) return _tags;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_tags);
}

@override@JsonKey() final  bool isLoadingDiaries;
@override final  ScrollController? scrollController;
@override final  TextEditingController? searchController;
@override final  FocusNode? searchFocusNode;
@override final  TextEditingController? contentController;

/// Create a copy of DiaryControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$DiaryControllerStateCopyWith<_DiaryControllerState> get copyWith => __$DiaryControllerStateCopyWithImpl<_DiaryControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _DiaryControllerState&&(identical(other.selectedDate, selectedDate) || other.selectedDate == selectedDate)&&(identical(other.searchQuery, searchQuery) || other.searchQuery == searchQuery)&&(identical(other.isSearchVisible, isSearchVisible) || other.isSearchVisible == isSearchVisible)&&(identical(other.selectedFilterDate, selectedFilterDate) || other.selectedFilterDate == selectedFilterDate)&&(identical(other.currentTag, currentTag) || other.currentTag == currentTag)&&const DeepCollectionEquality().equals(other._tags, _tags)&&(identical(other.isLoadingDiaries, isLoadingDiaries) || other.isLoadingDiaries == isLoadingDiaries)&&(identical(other.scrollController, scrollController) || other.scrollController == scrollController)&&(identical(other.searchController, searchController) || other.searchController == searchController)&&(identical(other.searchFocusNode, searchFocusNode) || other.searchFocusNode == searchFocusNode)&&(identical(other.contentController, contentController) || other.contentController == contentController));
}


@override
int get hashCode => Object.hash(runtimeType,selectedDate,searchQuery,isSearchVisible,selectedFilterDate,currentTag,const DeepCollectionEquality().hash(_tags),isLoadingDiaries,scrollController,searchController,searchFocusNode,contentController);

@override
String toString() {
  return 'DiaryControllerState(selectedDate: $selectedDate, searchQuery: $searchQuery, isSearchVisible: $isSearchVisible, selectedFilterDate: $selectedFilterDate, currentTag: $currentTag, tags: $tags, isLoadingDiaries: $isLoadingDiaries, scrollController: $scrollController, searchController: $searchController, searchFocusNode: $searchFocusNode, contentController: $contentController)';
}


}

/// @nodoc
abstract mixin class _$DiaryControllerStateCopyWith<$Res> implements $DiaryControllerStateCopyWith<$Res> {
  factory _$DiaryControllerStateCopyWith(_DiaryControllerState value, $Res Function(_DiaryControllerState) _then) = __$DiaryControllerStateCopyWithImpl;
@override @useResult
$Res call({
 DateTime? selectedDate, String searchQuery, bool isSearchVisible, DateTime? selectedFilterDate, String currentTag, List<String> tags, bool isLoadingDiaries, ScrollController? scrollController, TextEditingController? searchController, FocusNode? searchFocusNode, TextEditingController? contentController
});




}
/// @nodoc
class __$DiaryControllerStateCopyWithImpl<$Res>
    implements _$DiaryControllerStateCopyWith<$Res> {
  __$DiaryControllerStateCopyWithImpl(this._self, this._then);

  final _DiaryControllerState _self;
  final $Res Function(_DiaryControllerState) _then;

/// Create a copy of DiaryControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? selectedDate = freezed,Object? searchQuery = null,Object? isSearchVisible = null,Object? selectedFilterDate = freezed,Object? currentTag = null,Object? tags = null,Object? isLoadingDiaries = null,Object? scrollController = freezed,Object? searchController = freezed,Object? searchFocusNode = freezed,Object? contentController = freezed,}) {
  return _then(_DiaryControllerState(
selectedDate: freezed == selectedDate ? _self.selectedDate : selectedDate // ignore: cast_nullable_to_non_nullable
as DateTime?,searchQuery: null == searchQuery ? _self.searchQuery : searchQuery // ignore: cast_nullable_to_non_nullable
as String,isSearchVisible: null == isSearchVisible ? _self.isSearchVisible : isSearchVisible // ignore: cast_nullable_to_non_nullable
as bool,selectedFilterDate: freezed == selectedFilterDate ? _self.selectedFilterDate : selectedFilterDate // ignore: cast_nullable_to_non_nullable
as DateTime?,currentTag: null == currentTag ? _self.currentTag : currentTag // ignore: cast_nullable_to_non_nullable
as String,tags: null == tags ? _self._tags : tags // ignore: cast_nullable_to_non_nullable
as List<String>,isLoadingDiaries: null == isLoadingDiaries ? _self.isLoadingDiaries : isLoadingDiaries // ignore: cast_nullable_to_non_nullable
as bool,scrollController: freezed == scrollController ? _self.scrollController : scrollController // ignore: cast_nullable_to_non_nullable
as ScrollController?,searchController: freezed == searchController ? _self.searchController : searchController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,searchFocusNode: freezed == searchFocusNode ? _self.searchFocusNode : searchFocusNode // ignore: cast_nullable_to_non_nullable
as FocusNode?,contentController: freezed == contentController ? _self.contentController : contentController // ignore: cast_nullable_to_non_nullable
as TextEditingController?,
  ));
}


}

// dart format on
