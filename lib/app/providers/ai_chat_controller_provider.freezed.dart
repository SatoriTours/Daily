// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'ai_chat_controller_provider.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;
/// @nodoc
mixin _$AIChatControllerState {

/// 消息列表
 List<ChatMessage> get messages;/// 是否正在处理
 bool get isProcessing;/// 当前处理步骤
 String get currentStep;/// 会话ID
 String get sessionId;
/// Create a copy of AIChatControllerState
/// with the given fields replaced by the non-null parameter values.
@JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
$AIChatControllerStateCopyWith<AIChatControllerState> get copyWith => _$AIChatControllerStateCopyWithImpl<AIChatControllerState>(this as AIChatControllerState, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is AIChatControllerState&&const DeepCollectionEquality().equals(other.messages, messages)&&(identical(other.isProcessing, isProcessing) || other.isProcessing == isProcessing)&&(identical(other.currentStep, currentStep) || other.currentStep == currentStep)&&(identical(other.sessionId, sessionId) || other.sessionId == sessionId));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(messages),isProcessing,currentStep,sessionId);

@override
String toString() {
  return 'AIChatControllerState(messages: $messages, isProcessing: $isProcessing, currentStep: $currentStep, sessionId: $sessionId)';
}


}

/// @nodoc
abstract mixin class $AIChatControllerStateCopyWith<$Res>  {
  factory $AIChatControllerStateCopyWith(AIChatControllerState value, $Res Function(AIChatControllerState) _then) = _$AIChatControllerStateCopyWithImpl;
@useResult
$Res call({
 List<ChatMessage> messages, bool isProcessing, String currentStep, String sessionId
});




}
/// @nodoc
class _$AIChatControllerStateCopyWithImpl<$Res>
    implements $AIChatControllerStateCopyWith<$Res> {
  _$AIChatControllerStateCopyWithImpl(this._self, this._then);

  final AIChatControllerState _self;
  final $Res Function(AIChatControllerState) _then;

/// Create a copy of AIChatControllerState
/// with the given fields replaced by the non-null parameter values.
@pragma('vm:prefer-inline') @override $Res call({Object? messages = null,Object? isProcessing = null,Object? currentStep = null,Object? sessionId = null,}) {
  return _then(_self.copyWith(
messages: null == messages ? _self.messages : messages // ignore: cast_nullable_to_non_nullable
as List<ChatMessage>,isProcessing: null == isProcessing ? _self.isProcessing : isProcessing // ignore: cast_nullable_to_non_nullable
as bool,currentStep: null == currentStep ? _self.currentStep : currentStep // ignore: cast_nullable_to_non_nullable
as String,sessionId: null == sessionId ? _self.sessionId : sessionId // ignore: cast_nullable_to_non_nullable
as String,
  ));
}

}


/// Adds pattern-matching-related methods to [AIChatControllerState].
extension AIChatControllerStatePatterns on AIChatControllerState {
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

@optionalTypeArgs TResult maybeMap<TResult extends Object?>(TResult Function( _AIChatControllerState value)?  $default,{required TResult orElse(),}){
final _that = this;
switch (_that) {
case _AIChatControllerState() when $default != null:
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

@optionalTypeArgs TResult map<TResult extends Object?>(TResult Function( _AIChatControllerState value)  $default,){
final _that = this;
switch (_that) {
case _AIChatControllerState():
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

@optionalTypeArgs TResult? mapOrNull<TResult extends Object?>(TResult? Function( _AIChatControllerState value)?  $default,){
final _that = this;
switch (_that) {
case _AIChatControllerState() when $default != null:
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

@optionalTypeArgs TResult maybeWhen<TResult extends Object?>(TResult Function( List<ChatMessage> messages,  bool isProcessing,  String currentStep,  String sessionId)?  $default,{required TResult orElse(),}) {final _that = this;
switch (_that) {
case _AIChatControllerState() when $default != null:
return $default(_that.messages,_that.isProcessing,_that.currentStep,_that.sessionId);case _:
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

@optionalTypeArgs TResult when<TResult extends Object?>(TResult Function( List<ChatMessage> messages,  bool isProcessing,  String currentStep,  String sessionId)  $default,) {final _that = this;
switch (_that) {
case _AIChatControllerState():
return $default(_that.messages,_that.isProcessing,_that.currentStep,_that.sessionId);case _:
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

@optionalTypeArgs TResult? whenOrNull<TResult extends Object?>(TResult? Function( List<ChatMessage> messages,  bool isProcessing,  String currentStep,  String sessionId)?  $default,) {final _that = this;
switch (_that) {
case _AIChatControllerState() when $default != null:
return $default(_that.messages,_that.isProcessing,_that.currentStep,_that.sessionId);case _:
  return null;

}
}

}

/// @nodoc


class _AIChatControllerState implements AIChatControllerState {
  const _AIChatControllerState({final  List<ChatMessage> messages = const [], this.isProcessing = false, this.currentStep = '', this.sessionId = ''}): _messages = messages;
  

/// 消息列表
 final  List<ChatMessage> _messages;
/// 消息列表
@override@JsonKey() List<ChatMessage> get messages {
  if (_messages is EqualUnmodifiableListView) return _messages;
  // ignore: implicit_dynamic_type
  return EqualUnmodifiableListView(_messages);
}

/// 是否正在处理
@override@JsonKey() final  bool isProcessing;
/// 当前处理步骤
@override@JsonKey() final  String currentStep;
/// 会话ID
@override@JsonKey() final  String sessionId;

/// Create a copy of AIChatControllerState
/// with the given fields replaced by the non-null parameter values.
@override @JsonKey(includeFromJson: false, includeToJson: false)
@pragma('vm:prefer-inline')
_$AIChatControllerStateCopyWith<_AIChatControllerState> get copyWith => __$AIChatControllerStateCopyWithImpl<_AIChatControllerState>(this, _$identity);



@override
bool operator ==(Object other) {
  return identical(this, other) || (other.runtimeType == runtimeType&&other is _AIChatControllerState&&const DeepCollectionEquality().equals(other._messages, _messages)&&(identical(other.isProcessing, isProcessing) || other.isProcessing == isProcessing)&&(identical(other.currentStep, currentStep) || other.currentStep == currentStep)&&(identical(other.sessionId, sessionId) || other.sessionId == sessionId));
}


@override
int get hashCode => Object.hash(runtimeType,const DeepCollectionEquality().hash(_messages),isProcessing,currentStep,sessionId);

@override
String toString() {
  return 'AIChatControllerState(messages: $messages, isProcessing: $isProcessing, currentStep: $currentStep, sessionId: $sessionId)';
}


}

/// @nodoc
abstract mixin class _$AIChatControllerStateCopyWith<$Res> implements $AIChatControllerStateCopyWith<$Res> {
  factory _$AIChatControllerStateCopyWith(_AIChatControllerState value, $Res Function(_AIChatControllerState) _then) = __$AIChatControllerStateCopyWithImpl;
@override @useResult
$Res call({
 List<ChatMessage> messages, bool isProcessing, String currentStep, String sessionId
});




}
/// @nodoc
class __$AIChatControllerStateCopyWithImpl<$Res>
    implements _$AIChatControllerStateCopyWith<$Res> {
  __$AIChatControllerStateCopyWithImpl(this._self, this._then);

  final _AIChatControllerState _self;
  final $Res Function(_AIChatControllerState) _then;

/// Create a copy of AIChatControllerState
/// with the given fields replaced by the non-null parameter values.
@override @pragma('vm:prefer-inline') $Res call({Object? messages = null,Object? isProcessing = null,Object? currentStep = null,Object? sessionId = null,}) {
  return _then(_AIChatControllerState(
messages: null == messages ? _self._messages : messages // ignore: cast_nullable_to_non_nullable
as List<ChatMessage>,isProcessing: null == isProcessing ? _self.isProcessing : isProcessing // ignore: cast_nullable_to_non_nullable
as bool,currentStep: null == currentStep ? _self.currentStep : currentStep // ignore: cast_nullable_to_non_nullable
as String,sessionId: null == sessionId ? _self.sessionId : sessionId // ignore: cast_nullable_to_non_nullable
as String,
  ));
}


}

// dart format on
