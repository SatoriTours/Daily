// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'book_search_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$BookSearchControllerState {

 bool get isLoading; bool get isSearching; List<BookSearchResult> get searchResults; String get errorMessage; String get searchKeyword; String get initialKeyword;
/// Create a copy of BookSearchControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$BookSearchControllerStateCopyWith<BookSearchControllerState> get copyWith => _$BookSearchControllerStateCopyWithImpl<BookSearchControllerState>(this as BookSearchControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is BookSearchControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.isSearching, isSearching) || other.isSearching == isSearching)&&const DeepCollectionEquality().equals(other.searchResults, searchResults)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.searchKeyword, searchKeyword) || other.searchKeyword == searchKeyword)&&(identical(other.initialKeyword, initialKeyword) || other.initialKeyword == initialKeyword));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,isSearching,const DeepCollectionEquality().hash(searchResults),errorMessage,searchKeyword,initialKeyword);

@override
String toString() {
  return 'BookSearchControllerState(isLoading: $isLoading, isSearching: $isSearching, searchResults: $searchResults, errorMessage: $errorMessage, searchKeyword: $searchKeyword, initialKeyword: $initialKeyword)';
}


}

/// @nodoc
abstract mixin class $BookSearchControllerStateCopyWith<$Res>  {
  factory $BookSearchControllerStateCopyWith(BookSearchControllerState value, $Res Function(BookSearchControllerState) _then) = _$BookSearchControllerStateCopyWithImpl;
@useResult
$Res call({
 bool isLoading, bool isSearching, List<BookSearchResult> searchResults, String errorMessage, String searchKeyword, String initialKeyword
});




}
/// @nodoc
class _$BookSearchControllerStateCopyWithImpl<$Res>
    implements $BookSearchControllerStateCopyWith<$Res> {
  _$BookSearchControllerStateCopyWithImpl(this._self, this._then);

  final BookSearchControllerState _self;
  final $Res Function(BookSearchControllerState) _then;

/// Create a copy of BookSearchControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? isLoading = null,Object? isSearching = null,Object? searchResults = null,Object? errorMessage = null,Object? searchKeyword = null,Object? initialKeyword = null,}) {
  return _then(_self.copyWith(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,isSearching: null == isSearching ? _self.isSearching : isSearching // ignore: cast_nullable_to_non_nullable
as bool,searchResults: null == searchResults ? _self.searchResults : searchResults // ignore: cast_nullable_to_non_nullable
as List<BookSearchResult>,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,searchKeyword: null == searchKeyword ? _self.searchKeyword : searchKeyword // ignore: cast_nullable_to_non_nullable
as String,initialKeyword: null == initialKeyword ? _self.initialKeyword : initialKeyword // ignore: cast_nullable_to_non_nullable
as String,
  ));
}

}


/// Adds pattern-matching-related methods to [BookSearchControllerState].
extension BookSearchControllerStatePatterns on BookSearchControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _BookSearchControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _BookSearchControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _BookSearchControllerState value)  $default,){
final _that = this;
switch (_that) {
case _BookSearchControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _BookSearchControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _BookSearchControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( bool isLoading,  bool isSearching,  List<BookSearchResult> searchResults,  String errorMessage,  String searchKeyword,  String initialKeyword)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _BookSearchControllerState() when $default != null:
return $default(_that.isLoading,_that.isSearching,_that.searchResults,_that.errorMessage,_that.searchKeyword,_that.initialKeyword);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( bool isLoading,  bool isSearching,  List<BookSearchResult> searchResults,  String errorMessage,  String searchKeyword,  String initialKeyword)  $default,) {final _that = this;
switch (_that) {
case _BookSearchControllerState():
return $default(_that.isLoading,_that.isSearching,_that.searchResults,_that.errorMessage,_that.searchKeyword,_that.initialKeyword);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( bool isLoading,  bool isSearching,  List<BookSearchResult> searchResults,  String errorMessage,  String searchKeyword,  String initialKeyword)?  $default,) {final _that = this;
switch (_that) {
case _BookSearchControllerState() when $default != null:
return $default(_that.isLoading,_that.isSearching,_that.searchResults,_that.errorMessage,_that.searchKeyword,_that.initialKeyword);case _:
  return null;

}
}

}

/// @nodoc


class _BookSearchControllerState implements BookSearchControllerState {
  const _BookSearchControllerState({this.isLoading = false, this.isSearching = false, final  List<BookSearchResult> searchResults = const [], this.errorMessage = '', this.searchKeyword = '', this.initialKeyword = ''}): _searchResults = searchResults;
  

@override@JsonKey() final  bool isLoading;
@override@JsonKey() final  bool isSearching;
 final  List<BookSearchResult> _searchResults;
@override@JsonKey() List<BookSearchResult> get searchResults {
  if (_searchResults is EqualUnmodifiableListView) return _searchResults;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_searchResults);
}

@override@JsonKey() final  String errorMessage;
@override@JsonKey() final  String searchKeyword;
@override@JsonKey() final  String initialKeyword;

/// Create a copy of BookSearchControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$BookSearchControllerStateCopyWith<_BookSearchControllerState> get copyWith => __$BookSearchControllerStateCopyWithImpl<_BookSearchControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _BookSearchControllerState&&(identical(other.isLoading, isLoading) || other.isLoading == isLoading)&&(identical(other.isSearching, isSearching) || other.isSearching == isSearching)&&const DeepCollectionEquality().equals(other._searchResults, _searchResults)&&(identical(other.errorMessage, errorMessage) || other.errorMessage == errorMessage)&&(identical(other.searchKeyword, searchKeyword) || other.searchKeyword == searchKeyword)&&(identical(other.initialKeyword, initialKeyword) || other.initialKeyword == initialKeyword));
}


@override
int get hashCode => Object.hash(runtimeType,isLoading,isSearching,const DeepCollectionEquality().hash(_searchResults),errorMessage,searchKeyword,initialKeyword);

@override
String toString() {
  return 'BookSearchControllerState(isLoading: $isLoading, isSearching: $isSearching, searchResults: $searchResults, errorMessage: $errorMessage, searchKeyword: $searchKeyword, initialKeyword: $initialKeyword)';
}


}

/// @nodoc
abstract mixin class _$BookSearchControllerStateCopyWith<$Res> implements $BookSearchControllerStateCopyWith<$Res> {
  factory _$BookSearchControllerStateCopyWith(_BookSearchControllerState value, $Res Function(_BookSearchControllerState) _then) = __$BookSearchControllerStateCopyWithImpl;
@override @useResult
$Res call({
 bool isLoading, bool isSearching, List<BookSearchResult> searchResults, String errorMessage, String searchKeyword, String initialKeyword
});




}
/// @nodoc
class __$BookSearchControllerStateCopyWithImpl<$Res>
    implements _$BookSearchControllerStateCopyWith<$Res> {
  __$BookSearchControllerStateCopyWithImpl(this._self, this._then);

  final _BookSearchControllerState _self;
  final $Res Function(_BookSearchControllerState) _then;

/// Create a copy of BookSearchControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? isLoading = null,Object? isSearching = null,Object? searchResults = null,Object? errorMessage = null,Object? searchKeyword = null,Object? initialKeyword = null,}) {
  return _then(_BookSearchControllerState(
isLoading: null == isLoading ? _self.isLoading : isLoading // ignore: cast_nullable_to_non_nullable
as bool,isSearching: null == isSearching ? _self.isSearching : isSearching // ignore: cast_nullable_to_non_nullable
as bool,searchResults: null == searchResults ? _self._searchResults : searchResults // ignore: cast_nullable_to_non_nullable
as List<BookSearchResult>,errorMessage: null == errorMessage ? _self.errorMessage : errorMessage // ignore: cast_nullable_to_non_nullable
as String,searchKeyword: null == searchKeyword ? _self.searchKeyword : searchKeyword // ignore: cast_nullable_to_non_nullable
as String,initialKeyword: null == initialKeyword ? _self.initialKeyword : initialKeyword // ignore: cast_nullable_to_non_nullable
as String,
  ));
}


}

// dart format on
