/// AI Chat Controller Provider
///
/// AI聊天控制器，管理AI聊天界面的状态和交互。

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

/// AIChatController 状态
@freezed
abstract class AIChatControllerState with _$AIChatControllerState {
  const factory AIChatControllerState({
    /// 消息列表
    @Default([]) List<ChatMessage> messages,

    /// 是否正在处理
    @Default(false) bool isProcessing,

    /// 当前处理步骤
    @Default('') String currentStep,

    /// 会话ID
    @Default('') String sessionId,
  }) = _AIChatControllerState;
}

/// AIChatController Provider
@riverpod
class AIChatController extends _$AIChatController {
  final MCPAgentService _mcpAgentService = MCPAgentService.i;
  int _messageCounter = 0;

  @override
  AIChatControllerState build() {
    final sessionId = 'chat_${DateTime.now().millisecondsSinceEpoch}';
    final initialState = AIChatControllerState(sessionId: sessionId, messages: [_createWelcomeMessage()]);

    logger.d('[AIChatController] 初始化控制器，会话ID: $sessionId');
    return initialState;
  }

  /// 发送消息
  Future<void> sendMessage(String content) async {
    final trimmedContent = content.trim();
    if (trimmedContent.isEmpty || state.isProcessing) return;

    logger.i('[AIChatController] 发送消息: $trimmedContent');

    try {
      // 添加用户消息
      _addUserMessage(trimmedContent);

      // 创建处理中的消息
      final assistantMessage = _createProcessingMessage();
      state = state.copyWith(
        messages: [...state.messages, assistantMessage],
        isProcessing: true,
        currentStep: 'ai_chat.step_start'.t,
      );
      _scrollToBottom();

      // 处理查询
      final result = await _mcpAgentService.processQuery(
        query: trimmedContent,
        onStep: (step, status) => _handleStepUpdate(step),
      );

      // 更新消息
      _updateMessage(assistantMessage.id, result.answer);
      logger.i('[AIChatController] 消息处理完成');
    } catch (e, stackTrace) {
      logger.e('[AIChatController] 处理消息失败', error: e, stackTrace: stackTrace);
      _markLastAssistantAsError();
    } finally {
      state = state.copyWith(isProcessing: false, currentStep: '');
      _scrollToBottom();
    }
  }

  /// 重试消息
  Future<void> retryMessage(ChatMessage message) async {
    // 只有用户消息可以重试
    if (message.type != ChatMessageType.user) return;

    // 移除该消息之后的所有消息
    final index = state.messages.indexOf(message);
    if (index == -1) return;

    final newMessages = state.messages.sublist(0, index);
    state = state.copyWith(messages: newMessages);

    // 重新发送
    await sendMessage(message.content);
  }

  /// 清空消息
  void clearMessages() {
    final sessionId = 'chat_${DateTime.now().millisecondsSinceEpoch}';
    state = state.copyWith(
      sessionId: sessionId,
      messages: [_createWelcomeMessage()],
      isProcessing: false,
      currentStep: '',
    );
  }

  /// 创建欢迎消息
  ChatMessage _createWelcomeMessage() {
    return ChatMessage.assistant(id: _generateMessageId(), content: 'ai_chat.welcome_message'.t);
  }

  /// 添加用户消息
  void _addUserMessage(String content) {
    final message = ChatMessage.user(id: _generateMessageId(), content: content);
    state = state.copyWith(messages: [...state.messages, message]);
  }

  /// 创建处理中的消息
  ChatMessage _createProcessingMessage() {
    return ChatMessage.assistant(
      id: _generateMessageId(),
      content: 'ai_chat.processing'.t,
      status: MessageStatus.processing,
    );
  }

  /// 更新消息
  void _updateMessage(String messageId, String newContent) {
    final updatedMessages = state.messages.map((msg) {
      if (msg.id == messageId) {
        return msg.copyWith(content: newContent);
      }
      return msg;
    }).toList();

    state = state.copyWith(messages: updatedMessages);
  }

  /// 标记最后一条助手消息为错误
  void _markLastAssistantAsError() {
    final lastAssistantIndex = state.messages.lastIndexWhere((msg) => msg.type == ChatMessageType.assistant);

    if (lastAssistantIndex != -1) {
      final updatedMessages = List<ChatMessage>.from(state.messages);
      updatedMessages[lastAssistantIndex] = updatedMessages[lastAssistantIndex].copyWith(
        content: 'ai_chat.error_message'.t,
      );
      state = state.copyWith(messages: updatedMessages);
    }
  }

  /// 处理步骤更新
  void _handleStepUpdate(String step) {
    state = state.copyWith(currentStep: step);
  }

  /// 滚动到底部
  void _scrollToBottom() {
    // TODO: 实现滚动到底部逻辑
  }

  /// 生成消息ID
  String _generateMessageId() {
    return 'msg_${state.sessionId}_${++_messageCounter}';
  }
}
