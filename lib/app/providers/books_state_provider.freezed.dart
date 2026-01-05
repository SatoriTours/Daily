// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'books_state_provider.dart';

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$BooksStateModel {

 List<BookViewpointModel> get viewpoints; List<BookModel> get allBooks; bool get isLoading; int get currentViewpointIndex; int get filterBookID; bool get isProcessing; DisplayMode get mode; int? get deepLinkSeedViewpointId; BookModel? get selectedBook;
/// Create a copy of BooksStateModel
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$BooksStateModelCopyWith<BooksStateModel> get copyWith => _$BooksStateModelCopyWithImpl<BooksStateModel>(this as BooksStateModel, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is BooksStateModel&&const DeepCollectionEquality().equals(other.viewpoints, viewpoints)&&const DeepCollectionEquality().equals(other.allBooks, allBooks)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.currentViewpointIndex, currentViewpointIndex) || other.currentViewpointIndex == currentViewpointIndex)&&(identical(other.filterBookID, filterBookID) || other.filterBookID == filterBookID)&&(identical(other.isProcessing, isProcessing) || other.isProcessing == isProcessing)&&(identical(other.mode, mode) || other.mode == mode)&&(identical(other.deepLinkSeedViewpointId, deepLinkSeedViewpointId) || other.deepLinkSeedViewpointId == deepLinkSeedViewpointId)&&(identical(other.selectedBook, selectedBook) || other.selectedBook == selectedBook));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(viewpoints),const DeepCollectionEquality().hash(allBooks),isLoading,currentViewpointIndex,filterBookID,isProcessing,mode,deepLinkSeedViewpointId,selectedBook);

@override
String toString() {
  return 'BooksStateModel(viewpoints: $viewpoints, allBooks: $allBooks, isLoading: $isLoading, currentViewpointIndex: $currentViewpointIndex, filterBookID: $filterBookID, isProcessing: $isProcessing, mode: $mode, deepLinkSeedViewpointId: $deepLinkSeedViewpointId, selectedBook: $selectedBook)';
}


}

/// @nodoc
abstract mixin class $BooksStateModelCopyWith<$Res>  {
  factory $BooksStateModelCopyWith(BooksStateModel value, $Res Function(BooksStateModel) _then) = _$BooksStateModelCopyWithImpl;
@useResult
$Res call({
 List<BookViewpointModel> viewpoints, List<BookModel> allBooks, bool isLoading, int currentViewpointIndex, int filterBookID, bool isProcessing, DisplayMode mode, int? deepLinkSeedViewpointId, BookModel? selectedBook
});




}
/// @nodoc
class _$BooksStateModelCopyWithImpl<$Res>
    implements $BooksStateModelCopyWith<$Res> {
  _$BooksStateModelCopyWithImpl(this._self, this._then);

  final BooksStateModel _self;
  final $Res Function(BooksStateModel) _then;

/// Create a copy of BooksStateModel
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? viewpoints = null,Object? allBooks = null,Object? isLoading = null,Object? currentViewpointIndex = null,Object? filterBookID = null,Object? isProcessing = null,Object? mode = null,Object? deepLinkSeedViewpointId = freezed,Object? selectedBook = freezed,}) {
  return _then(_self.copyWith(
viewpoints: null == viewpoints ? _self.viewpoints : viewpoints // ignore: cast_nullable_to_non_nullable
as List<BookViewpointModel>,allBooks: null == allBooks ? _self.allBooks : allBooks // ignore: cast_nullable_to_non_nullable
as List<BookModel>,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,currentViewpointIndex: null == currentViewpointIndex ? _self.currentViewpointIndex : currentViewpointIndex // ignore: cast_nullable_to_non_nullable
as int,filterBookID: null == filterBookID ? _self.filterBookID : filterBookID // ignore: cast_nullable_to_non_nullable
as int,isProcessing: null == isProcessing ? _self.isProcessing : isProcessing // ignore: cast_nullable_to_non_nullable
as bool,mode: null == mode ? _self.mode : mode // ignore: cast_nullable_to_non_nullable
as DisplayMode,deepLinkSeedViewpointId: freezed == deepLinkSeedViewpointId ? _self.deepLinkSeedViewpointId : deepLinkSeedViewpointId // ignore: cast_nullable_to_non_nullable
as int?,selectedBook: freezed == selectedBook ? _self.selectedBook : selectedBook // ignore: cast_nullable_to_non_nullable
as BookModel?,
  ));
}

}


/// Adds pattern-matching-related methods to [BooksStateModel].
extension BooksStateModelPatterns on BooksStateModel {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _BooksStateModel value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _BooksStateModel() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _BooksStateModel value)  $default,){
final _that = this;
switch (_that) {
case _BooksStateModel():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _BooksStateModel value)?  $default,){
final _that = this;
switch (_that) {
case _BooksStateModel() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( List<BookViewpointModel> viewpoints,  List<BookModel> allBooks,  bool isLoading,  int currentViewpointIndex,  int filterBookID,  bool isProcessing,  DisplayMode mode,  int? deepLinkSeedViewpointId,  BookModel? selectedBook)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _BooksStateModel() when $default != null:
return $default(_that.viewpoints,_that.allBooks,_that.isLoading,_that.currentViewpointIndex,_that.filterBookID,_that.isProcessing,_that.mode,_that.deepLinkSeedViewpointId,_that.selectedBook);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( List<BookViewpointModel> viewpoints,  List<BookModel> allBooks,  bool isLoading,  int currentViewpointIndex,  int filterBookID,  bool isProcessing,  DisplayMode mode,  int? deepLinkSeedViewpointId,  BookModel? selectedBook)  $default,) {final _that = this;
switch (_that) {
case _BooksStateModel():
return $default(_that.viewpoints,_that.allBooks,_that.isLoading,_that.currentViewpointIndex,_that.filterBookID,_that.isProcessing,_that.mode,_that.deepLinkSeedViewpointId,_that.selectedBook);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( List<BookViewpointModel> viewpoints,  List<BookModel> allBooks,  bool isLoading,  int currentViewpointIndex,  int filterBookID,  bool isProcessing,  DisplayMode mode,  int? deepLinkSeedViewpointId,  BookModel? selectedBook)?  $default,) {final _that = this;
switch (_that) {
case _BooksStateModel() when $default != null:
return $default(_that.viewpoints,_that.allBooks,_that.isLoading,_that.currentViewpointIndex,_that.filterBookID,_that.isProcessing,_that.mode,_that.deepLinkSeedViewpointId,_that.selectedBook);case _:
  return null;

}
}

}

/// @nodoc


class _BooksStateModel extends BooksStateModel {
  const _BooksStateModel({final  List<BookViewpointModel> viewpoints = const [], final  List<BookModel> allBooks = const [], this.isLoading = false, this.currentViewpointIndex = 0, this.filterBookID = -1, this.isProcessing = false, this.mode = DisplayMode.allRandom, this.deepLinkSeedViewpointId, this.selectedBook}): _viewpoints = viewpoints,_allBooks = allBooks,super._();
  

 final  List<BookViewpointModel> _viewpoints;
@override@JsonKey() List<BookViewpointModel> get viewpoints {
  if (_viewpoints is EqualUnmodifiableListView) return _viewpoints;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_viewpoints);
}

 final  List<BookModel> _allBooks;
@override@JsonKey() List<BookModel> get allBooks {
  if (_allBooks is EqualUnmodifiableListView) return _allBooks;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_allBooks);
}

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  int currentViewpointIndex;
@override@JsonKey() final  int filterBookID;
@override@JsonKey() final  bool isProcessing;
@override@JsonKey() final  DisplayMode mode;
@override final  int? deepLinkSeedViewpointId;
@override final  BookModel? selectedBook;

/// Create a copy of BooksStateModel
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$BooksStateModelCopyWith<_BooksStateModel> get copyWith => __$BooksStateModelCopyWithImpl<_BooksStateModel>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _BooksStateModel&&const DeepCollectionEquality().equals(other._viewpoints, _viewpoints)&&const DeepCollectionEquality().equals(other._allBooks, _allBooks)&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.currentViewpointIndex, currentViewpointIndex) || other.currentViewpointIndex == currentViewpointIndex)&&(identical(other.filterBookID, filterBookID) || other.filterBookID == filterBookID)&&(identical(other.isProcessing, isProcessing) || other.isProcessing == isProcessing)&&(identical(other.mode, mode) || other.mode == mode)&&(identical(other.deepLinkSeedViewpointId, deepLinkSeedViewpointId) || other.deepLinkSeedViewpointId == deepLinkSeedViewpointId)&&(identical(other.selectedBook, selectedBook) || other.selectedBook == selectedBook));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(_viewpoints),const DeepCollectionEquality().hash(_allBooks),isLoading,currentViewpointIndex,filterBookID,isProcessing,mode,deepLinkSeedViewpointId,selectedBook);

@override
String toString() {
  return 'BooksStateModel(viewpoints: $viewpoints, allBooks: $allBooks, isLoading: $isLoading, currentViewpointIndex: $currentViewpointIndex, filterBookID: $filterBookID, isProcessing: $isProcessing, mode: $mode, deepLinkSeedViewpointId: $deepLinkSeedViewpointId, selectedBook: $selectedBook)';
}


}

/// @nodoc
abstract mixin class _$BooksStateModelCopyWith<$Res> implements $BooksStateModelCopyWith<$Res> {
  factory _$BooksStateModelCopyWith(_BooksStateModel value, $Res Function(_BooksStateModel) _then) = __$BooksStateModelCopyWithImpl;
@override @useResult
$Res call({
 List<BookViewpointModel> viewpoints, List<BookModel> allBooks, bool isLoading, int currentViewpointIndex, int filterBookID, bool isProcessing, DisplayMode mode, int? deepLinkSeedViewpointId, BookModel? selectedBook
});




}
/// @nodoc
class __$BooksStateModelCopyWithImpl<$Res>
    implements _$BooksStateModelCopyWith<$Res> {
  __$BooksStateModelCopyWithImpl(this._self, this._then);

  final _BooksStateModel _self;
  final $Res Function(_BooksStateModel) _then;

/// Create a copy of BooksStateModel
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? viewpoints = null,Object? allBooks = null,Object? isLoading = null,Object? currentViewpointIndex = null,Object? filterBookID = null,Object? isProcessing = null,Object? mode = null,Object? deepLinkSeedViewpointId = freezed,Object? selectedBook = freezed,}) {
  return _then(_BooksStateModel(
viewpoints: null == viewpoints ? _self._viewpoints : viewpoints // ignore: cast_nullable_to_non_nullable
as List<BookViewpointModel>,allBooks: null == allBooks ? _self._allBooks : allBooks // ignore: cast_nullable_to_non_nullable
as List<BookModel>,isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,currentViewpointIndex: null == currentViewpointIndex ? _self.currentViewpointIndex : currentViewpointIndex // ignore: cast_nullable_to_non_nullable
as int,filterBookID: null == filterBookID ? _self.filterBookID : filterBookID // ignore: cast_nullable_to_non_nullable
as int,isProcessing: null == isProcessing ? _self.isProcessing : isProcessing // ignore: cast_nullable_to_non_nullable
as bool,mode: null == mode ? _self.mode : mode // ignore: cast_nullable_to_non_nullable
as DisplayMode,deepLinkSeedViewpointId: freezed == deepLinkSeedViewpointId ? _self.deepLinkSeedViewpointId : deepLinkSeedViewpointId // ignore: cast_nullable_to_non_nullable
as int?,selectedBook: freezed == selectedBook ? _self.selectedBook : selectedBook // ignore: cast_nullable_to_non_nullable
as BookModel?,
  ));
}


}

// dart format on
