import 'dart:async';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import '../../../components/ai_chat/chat_message.dart';
import '../services/ai_agent_service.dart';
import '../models/tool_call.dart';

/// AI聊天控制器
class AIChatController extends GetxController {
  /// 消息列表
  final RxList<ChatMessage> messages = <ChatMessage>[].obs;

  /// 是否正在处理
  final RxBool isProcessing = false.obs;

  /// 当前步骤描述
  final RxString currentStep = ''.obs;

  /// 输入框控制器
  final TextEditingController inputController = TextEditingController();

  /// 滚动控制器
  final ScrollController scrollController = ScrollController();

  /// 当前会话ID
  final String sessionId = 'chat_${DateTime.now().millisecondsSinceEpoch}';

  /// AI Agent 服务
  final AIAgentService _aiAgentService = AIAgentService.i;

  /// 消息计数器
  int _messageCounter = 0;

  @override
  void onInit() {
    super.onInit();
    _addWelcomeMessage();
  }

  @override
  void onReady() {
    super.onReady();
    logger.i('[AI Chat Controller] AI聊天助手初始化完成');
  }

  @override
  void onClose() {
    inputController.dispose();
    scrollController.dispose();
    super.onClose();
  }

  /// 发送消息
  Future<void> sendMessage(String content) async {
    if (content.trim().isEmpty || isProcessing.value) return;

    try {
      // 添加用户消息
      final userMessage = ChatMessage.user(id: _generateMessageId(), content: content.trim());
      messages.add(userMessage);

      // 开始处理
      isProcessing.value = true;
      currentStep.value = 'ai_chat.step_start'.t;

      // 添加处理中的助手消息（带步骤列表）
      final assistantMessage = ChatMessage.assistant(
        id: _generateMessageId(),
        content: '',
        status: MessageStatus.processing,
        processingSteps: [],
      );
      messages.add(assistantMessage);

      // 滚动到底部
      _scrollToBottom();

      // 使用 AI Agent 处理查询
      final result = await _aiAgentService.processQuery(
        query: content,
        onStep: (step, status) => _updateStep(step, status),
        onToolCall: (toolCall) => _handleToolCall(toolCall),
        onResult: (result) => _updateResult(result),
        onSearchResults: (results) => _updateSearchResults(results),
      );

      // 更新助手消息
      final updatedMessage = assistantMessage.copyWith(status: MessageStatus.completed, content: result);
      final index = messages.indexOf(assistantMessage);
      if (index != -1) {
        messages[index] = updatedMessage;
      }
    } catch (e) {
      logger.e('[AI Chat] 处理消息失败: $e');

      // 添加错误消息
      final errorMessage = ChatMessage.assistant(
        id: _generateMessageId(),
        content: 'ai_chat.error_occurred'.t,
        status: MessageStatus.error,
      );
      messages.add(errorMessage);
    } finally {
      isProcessing.value = false;
      currentStep.value = '';
      _scrollToBottom();
    }
  }

  /// 重试消息
  Future<void> retryMessage(ChatMessage message) async {
    if (message.type != ChatMessageType.assistant) return;

    try {
      // 找到用户消息并重新发送
      final userMessageIndex = messages.indexWhere(
        (m) => m.type == ChatMessageType.user && messages.indexOf(m) < messages.indexOf(message),
      );

      if (userMessageIndex != -1) {
        final userMessage = messages[userMessageIndex];

        // 移除失败的消息
        messages.remove(message);

        // 重新发送用户消息
        await sendMessage(userMessage.content);
      }
    } catch (e) {
      logger.e('[AI Chat] 重试消息失败: $e');
    }
  }

  /// 清除所有消息
  void clearMessages() {
    messages.clear();
    _addWelcomeMessage();
  }

  /// 生成消息ID
  String _generateMessageId() {
    return '${sessionId}_${_messageCounter++}';
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
  }

  /// 更新处理步骤
  void _updateStep(String stepDescription, String statusString) {
    currentStep.value = stepDescription;

    // 找到处理中的助手消息
    ChatMessage? processingMessage;
    for (var i = messages.length - 1; i >= 0; i--) {
      if (messages[i].type == ChatMessageType.assistant && messages[i].isProcessing) {
        processingMessage = messages[i];
        break;
      }
    }

    if (processingMessage != null) {
      // 转换状态字符串为 StepStatus
      StepStatus stepStatus;
      switch (statusString) {
        case 'processing':
          stepStatus = StepStatus.processing;
          break;
        case 'completed':
          stepStatus = StepStatus.completed;
          break;
        case 'error':
          stepStatus = StepStatus.error;
          break;
        default:
          stepStatus = StepStatus.pending;
      }

      final currentSteps = List<ProcessingStep>.from(processingMessage.processingSteps ?? []);

      // 查找是否已存在相同描述的步骤
      final existingIndex = currentSteps.indexWhere((s) => s.description == stepDescription);

      if (existingIndex != -1) {
        // 更新现有步骤的状态
        currentSteps[existingIndex] = currentSteps[existingIndex].copyWith(status: stepStatus);
      } else {
        // 添加新步骤
        currentSteps.add(
          ProcessingStep(
            id: _generateMessageId(),
            description: stepDescription,
            status: stepStatus,
            timestamp: DateTime.now(),
          ),
        );
      }

      // 更新处理中的消息
      final updatedMessage = processingMessage.copyWith(processingSteps: currentSteps);

      final index = messages.indexOf(processingMessage);
      if (index != -1) {
        messages[index] = updatedMessage;
      }
    }
  }

  /// 处理工具调用
  void _handleToolCall(ToolCall toolCall) {
    // 将工具调用转换为消息
    final toolMessage = ChatMessage.tool(
      id: _generateMessageId(),
      toolName: toolCall.name,
      toolData: toolCall.parameters,
      description: toolCall.description,
    );

    // 添加到处理中的消息的子消息列表
    ChatMessage? processingMessage;
    for (var i = messages.length - 1; i >= 0; i--) {
      if (messages[i].type == ChatMessageType.assistant && messages[i].isProcessing) {
        processingMessage = messages[i];
        break;
      }
    }

    if (processingMessage != null) {
      final updatedMessage = processingMessage.copyWith(
        subMessages: [...(processingMessage.subMessages ?? []), toolMessage],
      );

      final index = messages.indexOf(processingMessage);
      if (index != -1) {
        messages[index] = updatedMessage;
      }
    }
  }

  /// 更新结果
  void _updateResult(String result) {
    // 找到处理中的助手消息并立即更新内容
    ChatMessage? processingMessage;
    for (var i = messages.length - 1; i >= 0; i--) {
      if (messages[i].type == ChatMessageType.assistant && messages[i].isProcessing) {
        processingMessage = messages[i];
        break;
      }
    }

    if (processingMessage != null) {
      // 立即更新内容,让用户能看到 AI 生成的答案
      // 保留processingSteps,让步骤和内容都显示
      final updatedMessage = processingMessage.copyWith(content: result);

      final index = messages.indexOf(processingMessage);
      if (index != -1) {
        messages[index] = updatedMessage;
        logger.d('[AI Chat] 已更新消息内容: ${result.substring(0, result.length > 50 ? 50 : result.length)}...');
      }
    }
  }

  /// 更新搜索结果
  void _updateSearchResults(List<dynamic> results) {
    // 找到处理中的助手消息并更新搜索结果
    ChatMessage? processingMessage;
    for (var i = messages.length - 1; i >= 0; i--) {
      if (messages[i].type == ChatMessageType.assistant && messages[i].isProcessing) {
        processingMessage = messages[i];
        break;
      }
    }

    if (processingMessage != null) {
      final updatedMessage = processingMessage.copyWith(searchResults: results);

      final index = messages.indexOf(processingMessage);
      if (index != -1) {
        messages[index] = updatedMessage;
      }
    }
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

  /// 获取消息数量
  int get messageCount => messages.length;

  /// 获取用户消息数量
  int get userMessageCount => messages.where((m) => m.type == ChatMessageType.user).length;

  /// 获取助手消息数量
  int get assistantMessageCount => messages.where((m) => m.type == ChatMessageType.assistant).length;
}
