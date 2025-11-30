import 'dart:async';
import 'package:daily_satori/app_exports.dart';
import '../../../components/ai_chat/chat_message.dart';
import '../services/mcp/index.dart';

/// AI聊天控制器
///
/// 负责管理AI聊天界面的状态和交互
class AIChatController extends BaseController {
  // ========================================================================
  // 构造函数
  // ========================================================================

  AIChatController(super._appStateService);

  // ========================================================================
  // 属性
  // ========================================================================

  final RxList<ChatMessage> messages = <ChatMessage>[].obs;
  final RxBool isProcessing = false.obs;
  final RxString currentStep = ''.obs;
  final TextEditingController inputController = TextEditingController();
  final ScrollController scrollController = ScrollController();
  final String sessionId = 'chat_${DateTime.now().millisecondsSinceEpoch}';

  final MCPAgentService _mcpAgentService = MCPAgentService.i;
  int _messageCounter = 0;

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void onInit() {
    super.onInit();
    logger.d('[AIChatController] 初始化控制器，会话ID: $sessionId');
    _addWelcomeMessage();
  }

  @override
  void onClose() {
    logger.d('[AIChatController] 销毁控制器');
    inputController.dispose();
    scrollController.dispose();
    super.onClose();
  }

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 发送消息
  Future<void> sendMessage(String content) async {
    final trimmedContent = content.trim();
    if (trimmedContent.isEmpty || isProcessing.value) return;

    logger.i('[AIChatController] 发送消息: $trimmedContent');

    try {
      _addUserMessage(trimmedContent);
      final assistantMessage = _createProcessingMessage();
      messages.add(assistantMessage);

      isProcessing.value = true;
      currentStep.value = 'ai_chat.step_start'.t;
      _scrollToBottom();

      final result = await _mcpAgentService.processQuery(query: trimmedContent, onStep: _handleStepUpdate);

      _updateMessage(assistantMessage.id, result);
      logger.i('[AIChatController] 消息处理完成');
    } catch (e, stackTrace) {
      logger.e('[AIChatController] 处理消息失败', error: e, stackTrace: stackTrace);
      _markLastAssistantAsError();
    } finally {
      isProcessing.value = false;
      currentStep.value = '';
      _scrollToBottom();
    }
  }

  /// 重试失败的消息
  Future<void> retryMessage(ChatMessage message) async {
    if (message.type != ChatMessageType.assistant || isProcessing.value) return;

    logger.i('[AIChatController] 重试消息: ${message.id}');

    final userMessage = _findPreviousUserMessage(message);
    if (userMessage != null) {
      messages.remove(message);
      await sendMessage(userMessage.content);
    }
  }

  /// 清除所有消息
  void clearMessages() {
    logger.i('[AIChatController] 清除所有消息');
    messages.clear();
    _addWelcomeMessage();
  }

  // ========================================================================
  // 消息管理
  // ========================================================================

  String _generateMessageId() => '${sessionId}_${_messageCounter++}';

  void _addUserMessage(String content) {
    messages.add(ChatMessage.user(id: _generateMessageId(), content: content));
  }

  ChatMessage _createProcessingMessage() {
    return ChatMessage.assistant(
      id: _generateMessageId(),
      content: '',
      status: MessageStatus.processing,
      processingSteps: [],
    );
  }

  void _addWelcomeMessage() {
    messages.add(
      ChatMessage.assistant(
        id: _generateMessageId(),
        content: MCPPrompts.welcomeMessage,
        status: MessageStatus.completed,
      ),
    );
  }

  void _updateMessage(String messageId, MCPAgentResult result) {
    final index = messages.indexWhere((m) => m.id == messageId);
    if (index == -1) return;

    messages[index] = messages[index].copyWith(
      status: MessageStatus.completed,
      content: result.answer,
      subMessages: null,
      processingSteps: null,
      searchResults: result.searchResults.isNotEmpty ? result.searchResults : null,
    );
  }

  void _markLastAssistantAsError() {
    for (var i = messages.length - 1; i >= 0; i--) {
      final message = messages[i];
      if (message.type == ChatMessageType.assistant && message.isProcessing) {
        final updatedSteps = message.processingSteps?.map((step) {
          return step.status == StepStatus.processing ? step.copyWith(status: StepStatus.error) : step;
        }).toList();

        messages[i] = message.copyWith(
          status: MessageStatus.error,
          content: 'ai_chat.error_occurred'.t,
          processingSteps: updatedSteps,
        );
        break;
      }
    }
  }

  ChatMessage? _findPreviousUserMessage(ChatMessage assistantMessage) {
    final index = messages.indexOf(assistantMessage);
    if (index == -1) return null;

    for (var i = index - 1; i >= 0; i--) {
      if (messages[i].type == ChatMessageType.user) return messages[i];
    }
    return null;
  }

  // ========================================================================
  // 步骤更新
  // ========================================================================

  void _handleStepUpdate(String step, String status) {
    currentStep.value = step;
    _updateProcessingStep(step, status);
  }

  void _updateProcessingStep(String stepDescription, String statusString) {
    final processingIndex = messages.lastIndexWhere((m) => m.type == ChatMessageType.assistant && m.isProcessing);
    if (processingIndex == -1) return;

    final message = messages[processingIndex];
    final stepStatus = _parseStepStatus(statusString);
    final steps = List<ProcessingStep>.from(message.processingSteps ?? []);

    final existingIndex = steps.indexWhere((s) => s.description == stepDescription);
    if (existingIndex != -1) {
      steps[existingIndex] = steps[existingIndex].copyWith(status: stepStatus);
    } else {
      steps.add(
        ProcessingStep(
          id: _generateMessageId(),
          description: stepDescription,
          status: stepStatus,
          timestamp: DateTime.now(),
        ),
      );
    }

    messages[processingIndex] = message.copyWith(processingSteps: steps);
  }

  StepStatus _parseStepStatus(String statusString) {
    return switch (statusString) {
      'processing' => StepStatus.processing,
      'completed' => StepStatus.completed,
      'error' => StepStatus.error,
      _ => StepStatus.pending,
    };
  }

  // ========================================================================
  // UI 辅助
  // ========================================================================

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scrollController.hasClients) {
        scrollController.animateTo(
          scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  // ========================================================================
  // 统计信息
  // ========================================================================

  int get messageCount => messages.length;
  int get userMessageCount => messages.where((m) => m.type == ChatMessageType.user).length;
  int get assistantMessageCount => messages.where((m) => m.type == ChatMessageType.assistant).length;
}
