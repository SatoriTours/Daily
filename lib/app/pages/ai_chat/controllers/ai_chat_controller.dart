import 'dart:async';
import 'package:daily_satori/app_exports.dart';
import '../../../components/ai_chat/chat_message.dart';
import '../services/ai_agent_service.dart';
import '../models/tool_call.dart';

/// AI聊天控制器
///
/// 负责管理AI聊天界面的状态和交互，包括：
/// - 消息列表管理
/// - 发送和重试消息
/// - 与AI Agent服务交互
/// - 处理步骤和工具调用的更新
class AIChatController extends BaseController {
  // ========================================================================
  // 构造函数
  // ========================================================================

  AIChatController(super._appStateService);

  // ========================================================================
  // 属性
  // ========================================================================

  /// 消息列表
  final RxList<ChatMessage> messages = <ChatMessage>[].obs;

  /// 是否正在处理用户请求
  final RxBool isProcessing = false.obs;

  /// 当前处理步骤描述
  final RxString currentStep = ''.obs;

  /// 输入框控制器
  final TextEditingController inputController = TextEditingController();

  /// 滚动控制器
  final ScrollController scrollController = ScrollController();

  /// 当前会话ID（用于追踪会话）
  final String sessionId = 'chat_${DateTime.now().millisecondsSinceEpoch}';

  /// AI Agent 服务实例
  final AIAgentService _aiAgentService = AIAgentService.i;

  /// 消息ID生成计数器
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
  void onReady() {
    super.onReady();
    logger.i('[AIChatController] AI聊天助手准备就绪');
  }

  @override
  void onClose() {
    logger.d('[AIChatController] 销毁控制器，清理资源');
    inputController.dispose();
    scrollController.dispose();
    super.onClose();
  }

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 发送消息
  ///
  /// 处理用户输入的消息，调用AI Agent进行处理
  ///
  /// [content] 用户输入的消息内容
  Future<void> sendMessage(String content) async {
    final trimmedContent = content.trim();

    // 检查输入是否有效且未在处理中
    if (trimmedContent.isEmpty || isProcessing.value) {
      logger.d('[AIChatController] 忽略发送：内容为空或正在处理中');
      return;
    }

    logger.i('[AIChatController] 发送消息: $trimmedContent');

    try {
      // 1. 添加用户消息到列表
      _addUserMessage(trimmedContent);

      // 2. 创建并添加处理中的助手消息
      final assistantMessage = _createProcessingAssistantMessage();
      messages.add(assistantMessage);

      // 3. 开始处理
      _startProcessing();
      _scrollToBottom();

      // 4. 调用AI Agent处理查询
      final result = await _processWithAIAgent(trimmedContent);

      // 5. 更新助手消息为完成状态
      _updateAssistantMessage(assistantMessage, result);

      logger.i('[AIChatController] 消息处理完成');
    } catch (e, stackTrace) {
      logger.e('[AIChatController] 处理消息失败', error: e, stackTrace: stackTrace);
      _addErrorMessage();
    } finally {
      _stopProcessing();
      _scrollToBottom();
    }
  }

  /// 重试失败的消息
  ///
  /// 找到对应的用户消息并重新发送
  ///
  /// [message] 需要重试的助手消息
  Future<void> retryMessage(ChatMessage message) async {
    if (message.type != ChatMessageType.assistant) {
      logger.w('[AIChatController] 只能重试助手消息');
      return;
    }

    logger.i('[AIChatController] 重试消息: ${message.id}');

    try {
      // 找到此助手消息之前的最近用户消息
      final userMessage = _findPreviousUserMessage(message);

      if (userMessage != null) {
        // 移除失败的助手消息
        messages.remove(message);
        logger.d('[AIChatController] 已移除失败消息，准备重试');

        // 重新发送用户消息
        await sendMessage(userMessage.content);
      } else {
        logger.w('[AIChatController] 未找到对应的用户消息');
      }
    } catch (e, stackTrace) {
      logger.e('[AIChatController] 重试消息失败', error: e, stackTrace: stackTrace);
    }
  }

  /// 清除所有消息
  ///
  /// 清空消息列表并重新添加欢迎消息
  void clearMessages() {
    logger.i('[AIChatController] 清除所有消息');
    messages.clear();
    _addWelcomeMessage();
  }

  // ========================================================================
  // 私有辅助方法 - 消息管理
  // ========================================================================

  /// 生成唯一的消息ID
  String _generateMessageId() {
    return '${sessionId}_${_messageCounter++}';
  }

  /// 添加用户消息
  void _addUserMessage(String content) {
    final userMessage = ChatMessage.user(id: _generateMessageId(), content: content);
    messages.add(userMessage);
    logger.d('[AIChatController] 添加用户消息: ${userMessage.id}');
  }

  /// 创建处理中的助手消息
  ChatMessage _createProcessingAssistantMessage() {
    return ChatMessage.assistant(
      id: _generateMessageId(),
      content: '',
      status: MessageStatus.processing,
      processingSteps: [],
    );
  }

  /// 添加错误消息
  void _addErrorMessage() {
    final errorMessage = ChatMessage.assistant(
      id: _generateMessageId(),
      content: 'ai_chat.error_occurred'.t,
      status: MessageStatus.error,
    );
    messages.add(errorMessage);
    logger.d('[AIChatController] 添加错误消息');
  }

  /// 添加欢迎消息
  void _addWelcomeMessage() {
    final welcomeMessage = ChatMessage.assistant(
      id: _generateMessageId(),
      content: '''👋 **欢迎使用AI助手！**

我可以帮助您：

📚 **搜索文章**，📔 **查找日记**，📖 **搜索书籍**，📋 **智能总结**

💡 **使用示例**：
- "查找关于Flutter开发的文章"
- "最近一周的日记"
- "搜索海外电话卡相关内容"

请告诉我您想要查找什么，我会为您快速找到答案！''',
      status: MessageStatus.completed,
    );
    messages.add(welcomeMessage);
    logger.d('[AIChatController] 添加欢迎消息');
  }

  /// 查找助手消息之前的用户消息
  ///
  /// [assistantMessage] 助手消息
  /// 返回对应的用户消息，如果未找到则返回null
  ChatMessage? _findPreviousUserMessage(ChatMessage assistantMessage) {
    final assistantIndex = messages.indexOf(assistantMessage);
    if (assistantIndex == -1) return null;

    // 从助手消息往前查找最近的用户消息
    for (var i = assistantIndex - 1; i >= 0; i--) {
      if (messages[i].type == ChatMessageType.user) {
        return messages[i];
      }
    }
    return null;
  }

  // ========================================================================
  // 私有辅助方法 - 处理状态管理
  // ========================================================================

  /// 开始处理状态
  void _startProcessing() {
    isProcessing.value = true;
    currentStep.value = 'ai_chat.step_start'.t;
    logger.d('[AIChatController] 开始处理');
  }

  /// 停止处理状态
  void _stopProcessing() {
    isProcessing.value = false;
    currentStep.value = '';
    logger.d('[AIChatController] 处理结束');
  }

  /// 调用AI Agent处理查询
  ///
  /// [query] 用户查询内容
  /// 返回AI生成的答案
  Future<String> _processWithAIAgent(String query) async {
    logger.d('[AIChatController] 开始调用AI Agent');

    final result = await _aiAgentService.processQuery(
      query: query,
      onStep: _handleStepUpdate,
      onToolCall: _handleToolCall,
      onResult: _handleResultUpdate,
      onSearchResults: _handleSearchResults,
    );

    logger.d('[AIChatController] AI Agent处理完成');
    return result;
  }

  /// 更新助手消息为完成状态
  ///
  /// [message] 要更新的助手消息
  /// [result] AI生成的最终答案
  void _updateAssistantMessage(ChatMessage message, String result) {
    final updatedMessage = message.copyWith(status: MessageStatus.completed, content: result);

    final index = messages.indexOf(message);
    if (index != -1) {
      messages[index] = updatedMessage;
      logger.d('[AIChatController] 更新助手消息为完成状态');
    }
  }

  // ========================================================================
  // 私有辅助方法 - AI Agent回调处理
  // ========================================================================

  /// 处理步骤更新（AI Agent回调）
  void _handleStepUpdate(String step, String status) {
    currentStep.value = step;
    _updateProcessingStep(step, status);
  }

  /// 处理工具调用（AI Agent回调）
  void _handleToolCall(ToolCall toolCall) {
    _addToolCallToProcessingMessage(toolCall);
  }

  /// 处理结果更新（AI Agent回调）
  void _handleResultUpdate(String result) {
    _updateProcessingMessageContent(result);
  }

  /// 处理搜索结果（AI Agent回调）
  void _handleSearchResults(List<dynamic> results) {
    _updateProcessingMessageSearchResults(results);
  }

  // ========================================================================
  // 私有辅助方法 - 消息内容更新
  // ========================================================================

  /// 更新处理中消息的步骤状态
  ///
  /// [stepDescription] 步骤描述
  /// [statusString] 状态字符串 (processing/completed/error/pending)
  void _updateProcessingStep(String stepDescription, String statusString) {
    final processingMessage = _findProcessingMessage();
    if (processingMessage == null) return;

    final stepStatus = _parseStepStatus(statusString);
    final updatedSteps = _updateStepsList(processingMessage.processingSteps ?? [], stepDescription, stepStatus);

    _updateMessageInList(processingMessage, processingMessage.copyWith(processingSteps: updatedSteps));

    logger.d('[AIChatController] 更新步骤: $stepDescription -> $statusString');
  }

  /// 添加工具调用到处理中的消息
  ///
  /// [toolCall] 工具调用信息
  void _addToolCallToProcessingMessage(ToolCall toolCall) {
    final processingMessage = _findProcessingMessage();
    if (processingMessage == null) return;

    final toolMessage = ChatMessage.tool(
      id: _generateMessageId(),
      toolName: toolCall.name,
      toolData: toolCall.parameters,
      description: toolCall.description,
    );

    final updatedSubMessages = [...(processingMessage.subMessages ?? []), toolMessage];

    _updateMessageInList(processingMessage, processingMessage.copyWith(subMessages: updatedSubMessages));

    logger.d('[AIChatController] 添加工具调用: ${toolCall.name}');
  }

  /// 更新处理中消息的内容
  ///
  /// [result] AI生成的内容
  void _updateProcessingMessageContent(String result) {
    final processingMessage = _findProcessingMessage();
    if (processingMessage == null) return;

    _updateMessageInList(processingMessage, processingMessage.copyWith(content: result));

    final preview = result.length > 50 ? '${result.substring(0, 50)}...' : result;
    logger.d('[AIChatController] 更新消息内容: $preview');
  }

  /// 更新处理中消息的搜索结果
  ///
  /// [results] 搜索结果列表
  void _updateProcessingMessageSearchResults(List<dynamic> results) {
    final processingMessage = _findProcessingMessage();
    if (processingMessage == null) return;

    _updateMessageInList(processingMessage, processingMessage.copyWith(searchResults: results));

    logger.d('[AIChatController] 更新搜索结果: ${results.length}条');
  }

  // ========================================================================
  // 私有工具方法
  // ========================================================================

  /// 查找当前处理中的助手消息
  ///
  /// 返回正在处理的助手消息，如果未找到则返回null
  ChatMessage? _findProcessingMessage() {
    for (var i = messages.length - 1; i >= 0; i--) {
      final message = messages[i];
      if (message.type == ChatMessageType.assistant && message.isProcessing) {
        return message;
      }
    }
    return null;
  }

  /// 在消息列表中更新消息
  ///
  /// [oldMessage] 旧消息
  /// [newMessage] 新消息
  void _updateMessageInList(ChatMessage oldMessage, ChatMessage newMessage) {
    final index = messages.indexOf(oldMessage);
    if (index != -1) {
      messages[index] = newMessage;
    }
  }

  /// 解析步骤状态字符串
  ///
  /// [statusString] 状态字符串
  /// 返回对应的StepStatus枚举
  StepStatus _parseStepStatus(String statusString) {
    switch (statusString) {
      case 'processing':
        return StepStatus.processing;
      case 'completed':
        return StepStatus.completed;
      case 'error':
        return StepStatus.error;
      default:
        return StepStatus.pending;
    }
  }

  /// 更新步骤列表
  ///
  /// [currentSteps] 当前步骤列表
  /// [stepDescription] 步骤描述
  /// [status] 新状态
  /// 返回更新后的步骤列表
  List<ProcessingStep> _updateStepsList(List<ProcessingStep> currentSteps, String stepDescription, StepStatus status) {
    final steps = List<ProcessingStep>.from(currentSteps);
    final existingIndex = steps.indexWhere((s) => s.description == stepDescription);

    if (existingIndex != -1) {
      // 更新现有步骤
      steps[existingIndex] = steps[existingIndex].copyWith(status: status);
    } else {
      // 添加新步骤
      steps.add(
        ProcessingStep(
          id: _generateMessageId(),
          description: stepDescription,
          status: status,
          timestamp: DateTime.now(),
        ),
      );
    }

    return steps;
  }

  /// 滚动到底部
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
  // 公共Getter - 统计信息
  // ========================================================================

  /// 获取消息总数量
  int get messageCount => messages.length;

  /// 获取用户消息数量
  int get userMessageCount => messages.where((m) => m.type == ChatMessageType.user).length;

  /// 获取助手消息数量
  int get assistantMessageCount => messages.where((m) => m.type == ChatMessageType.assistant).length;
}
