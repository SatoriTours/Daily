/// AI聊天消息类型
enum ChatMessageType {
  user, // 用户消息
  assistant, // AI助手消息
  system, // 系统消息
  tool, // 工具调用消息
  thinking, // AI思考过程
}

/// 消息状态
enum MessageStatus {
  sending, // 发送中
  sent, // 已发送
  processing, // 处理中
  completed, // 已完成
  error, // 错误
}

/// 步骤状态
enum StepStatus {
  pending, // 等待执行
  processing, // 执行中
  completed, // 已完成
  error, // 执行出错
}

/// 处理步骤
class ProcessingStep {
  final String id;
  final String description;
  final StepStatus status;
  final DateTime timestamp;

  const ProcessingStep({required this.id, required this.description, required this.status, required this.timestamp});

  ProcessingStep copyWith({String? id, String? description, StepStatus? status, DateTime? timestamp}) {
    return ProcessingStep(
      id: id ?? this.id,
      description: description ?? this.description,
      status: status ?? this.status,
      timestamp: timestamp ?? this.timestamp,
    );
  }
}

/// AI聊天消息模型
class ChatMessage {
  /// 消息ID
  final String id;

  /// 消息类型
  final ChatMessageType type;

  /// 消息内容
  final String content;

  /// 消息状态
  final MessageStatus status;

  /// 创建时间
  final DateTime timestamp;

  /// 工具调用数据（当type为tool时使用）
  final Map<String, dynamic>? toolData;

  /// 是否显示思考过程
  final bool showThinking;

  /// 子消息（用于工具调用的详细步骤）
  final List<ChatMessage>? subMessages;

  /// 处理步骤列表
  final List<ProcessingStep>? processingSteps;

  /// 搜索结果列表
  final List<dynamic>? searchResults;

  const ChatMessage({
    required this.id,
    required this.type,
    required this.content,
    required this.status,
    required this.timestamp,
    this.toolData,
    this.showThinking = false,
    this.subMessages,
    this.processingSteps,
    this.searchResults,
  });

  /// 创建用户消息
  factory ChatMessage.user({required String id, required String content, MessageStatus status = MessageStatus.sent}) {
    return ChatMessage(id: id, type: ChatMessageType.user, content: content, status: status, timestamp: DateTime.now());
  }

  /// 创建助手消息
  factory ChatMessage.assistant({
    required String id,
    required String content,
    MessageStatus status = MessageStatus.completed,
    List<ChatMessage>? subMessages,
    List<ProcessingStep>? processingSteps,
    List<dynamic>? searchResults,
  }) {
    return ChatMessage(
      id: id,
      type: ChatMessageType.assistant,
      content: content,
      status: status,
      timestamp: DateTime.now(),
      subMessages: subMessages,
      processingSteps: processingSteps,
      searchResults: searchResults,
    );
  }

  /// 创建系统消息
  factory ChatMessage.system({required String id, required String content}) {
    return ChatMessage(
      id: id,
      type: ChatMessageType.system,
      content: content,
      status: MessageStatus.completed,
      timestamp: DateTime.now(),
    );
  }

  /// 创建工具调用消息
  factory ChatMessage.tool({
    required String id,
    required String toolName,
    required Map<String, dynamic> toolData,
    required String description,
    MessageStatus status = MessageStatus.processing,
  }) {
    return ChatMessage(
      id: id,
      type: ChatMessageType.tool,
      content: description,
      status: status,
      timestamp: DateTime.now(),
      toolData: {'toolName': toolName, ...toolData},
    );
  }

  /// 创建思考过程消息
  factory ChatMessage.thinking({required String id, required String content}) {
    return ChatMessage(
      id: id,
      type: ChatMessageType.thinking,
      content: content,
      status: MessageStatus.processing,
      timestamp: DateTime.now(),
      showThinking: true,
    );
  }

  /// 复制消息并更新状态
  ChatMessage copyWith({
    String? id,
    ChatMessageType? type,
    String? content,
    MessageStatus? status,
    DateTime? timestamp,
    Map<String, dynamic>? toolData,
    bool? showThinking,
    List<ChatMessage>? subMessages,
    List<ProcessingStep>? processingSteps,
    List<dynamic>? searchResults,
  }) {
    return ChatMessage(
      id: id ?? this.id,
      type: type ?? this.type,
      content: content ?? this.content,
      status: status ?? this.status,
      timestamp: timestamp ?? this.timestamp,
      toolData: toolData ?? this.toolData,
      showThinking: showThinking ?? this.showThinking,
      subMessages: subMessages ?? this.subMessages,
      processingSteps: processingSteps ?? this.processingSteps,
      searchResults: searchResults ?? this.searchResults,
    );
  }

  /// 获取工具名称
  String? get toolName => toolData?['toolName'] as String?;

  /// 是否为处理中状态
  bool get isProcessing => status == MessageStatus.processing || status == MessageStatus.sending;

  /// 是否已完成
  bool get isCompleted => status == MessageStatus.completed;

  /// 是否出错
  bool get hasError => status == MessageStatus.error;
}
