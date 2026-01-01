/// AI Chat Controller Provider
library;

import 'dart:async';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/components/ai_chat/chat_message.dart';
import 'package:daily_satori/app/pages/ai_chat/services/mcp/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

part 'ai_chat_controller_provider.freezed.dart';
part 'ai_chat_controller_provider.g.dart';

@freezed
abstract class AIChatControllerState with _$AIChatControllerState {
  const factory AIChatControllerState({
    @Default([]) List<ChatMessage> messages,
    @Default(false) bool isProcessing,
    @Default('') String currentStep,
    @Default('') String sessionId,
  }) = _AIChatControllerState;
}

@riverpod
class AIChatController extends _$AIChatController {
  final MCPAgentService _mcpAgentService = MCPAgentService.i;
  int _messageCounter = 0;

  @override
  AIChatControllerState build() {
    final sessionId = 'chat_${DateTime.now().millisecondsSinceEpoch}';
    final welcomeMessage = ChatMessage.assistant(
      id: 'msg_${sessionId}_${++_messageCounter}',
      content: 'ai_chat.welcome_message'.t,
    );
    return AIChatControllerState(sessionId: sessionId, messages: [welcomeMessage]);
  }

  Future<void> sendMessage(String content) async {
    final trimmedContent = content.trim();
    if (trimmedContent.isEmpty || state.isProcessing) return;

    try {
      _addUserMessage(trimmedContent);
      final assistantMessage = _createProcessingMessage();
      state = state.copyWith(
        messages: [...state.messages, assistantMessage],
        isProcessing: true,
        currentStep: 'ai_chat.step_start'.t,
      );

      final result = await _mcpAgentService.processQuery(
        query: trimmedContent,
        onStep: (step, status) => state = state.copyWith(currentStep: step),
      );
      _updateMessage(assistantMessage.id, result.answer);
    } catch (e, stackTrace) {
      logger.e('[AIChatController] 处理消息失败', error: e, stackTrace: stackTrace);
      _markLastAssistantAsError();
    } finally {
      state = state.copyWith(isProcessing: false, currentStep: '');
    }
  }

  Future<void> retryMessage(ChatMessage message) async {
    if (message.type != ChatMessageType.user) return;
    final index = state.messages.indexOf(message);
    if (index == -1) return;
    state = state.copyWith(messages: state.messages.sublist(0, index));
    await sendMessage(message.content);
  }

  void clearMessages() {
    state = state.copyWith(
      sessionId: 'chat_${DateTime.now().millisecondsSinceEpoch}',
      messages: [_createWelcomeMessage()],
      isProcessing: false,
      currentStep: '',
    );
  }

  ChatMessage _createWelcomeMessage() =>
      ChatMessage.assistant(id: _generateMessageId(), content: 'ai_chat.welcome_message'.t);

  void _addUserMessage(String content) {
    final message = ChatMessage.user(id: _generateMessageId(), content: content);
    state = state.copyWith(messages: [...state.messages, message]);
  }

  ChatMessage _createProcessingMessage() => ChatMessage.assistant(
    id: _generateMessageId(),
    content: 'ai_chat.processing'.t,
    status: MessageStatus.processing,
  );

  void _updateMessage(String messageId, String newContent) {
    state = state.copyWith(
      messages: state.messages.map((msg) => msg.id == messageId ? msg.copyWith(content: newContent) : msg).toList(),
    );
  }

  void _markLastAssistantAsError() {
    final index = state.messages.lastIndexWhere((msg) => msg.type == ChatMessageType.assistant);
    if (index == -1) return;
    final updated = List<ChatMessage>.from(state.messages);
    updated[index] = updated[index].copyWith(content: 'ai_chat.error_message'.t);
    state = state.copyWith(messages: updated);
  }

  String _generateMessageId() => 'msg_${state.sessionId}_${++_messageCounter}';
}
