// 枚举定义

/// AI聊天消息类型
///
/// 定义了聊天系统中所有可能的消息类型
enum ChatMessageType {
  /// 用户发送的消息
  user,

  /// AI助手的回复消息
  assistant,

  /// 系统提示消息
  system,

  /// 工具调用相关消息（如搜索、计算等）
  tool,

  /// AI思考过程消息（显示AI的推理步骤）
  thinking,
}

/// 消息状态
///
/// 表示消息在生命周期中的不同阶段
enum MessageStatus {
  /// 正在发送中
  sending,

  /// 已成功发送
  sent,

  /// 正在处理中（AI正在生成回复）
  processing,

  /// 已完成处理
  completed,

  /// 处理出错
  error,
}

/// 步骤状态
///
/// 表示AI处理步骤的执行状态
enum StepStatus {
  /// 等待执行
  pending,

  /// 正在执行
  processing,

  /// 执行完成
  completed,

  /// 执行出错
  error,
}

// 数据模型

/// 处理步骤
///
/// 记录AI处理查询时的各个执行步骤
/// 用于向用户展示处理进度和当前状态
class ProcessingStep {
  /// 步骤唯一标识
  final String id;

  /// 步骤描述文本
  final String description;

  /// 步骤当前状态
  final StepStatus status;

  /// 步骤创建时间
  final DateTime timestamp;

  const ProcessingStep({
    required this.id,
    required this.description,
    required this.status,
    required this.timestamp,
  });

  /// 复制并更新部分属性
  ProcessingStep copyWith({
    String? id,
    String? description,
    StepStatus? status,
    DateTime? timestamp,
  }) {
    return ProcessingStep(
      id: id ?? this.id,
      description: description ?? this.description,
      status: status ?? this.status,
      timestamp: timestamp ?? this.timestamp,
    );
  }
}

/// AI聊天消息模型
///
/// 表示聊天界面中的一条消息，包含消息内容、状态、
/// 处理步骤、搜索结果等完整信息
class ChatMessage {
  // ========================================================================
  // 基本属性
  // ========================================================================

  /// 消息唯一标识
  final String id;

  /// 消息类型（用户/助手/系统/工具/思考）
  final ChatMessageType type;

  /// 消息文本内容
  final String content;

  /// 消息当前状态
  final MessageStatus status;

  /// 消息创建时间戳
  final DateTime timestamp;

  // ========================================================================
  // 扩展属性
  // ========================================================================

  /// 工具调用相关数据
  /// 当消息类型为tool时，包含工具名称和参数
  final Map<String, dynamic>? toolData;

  /// 是否显示思考过程动画
  final bool showThinking;

  /// 子消息列表
  /// 用于嵌套显示工具调用的详细步骤
  final List<ChatMessage>? subMessages;

  /// 处理步骤列表
  /// 记录AI处理查询的各个阶段
  final List<ProcessingStep>? processingSteps;

  /// 搜索结果列表
  /// 存储查询返回的搜索结果
  final List<dynamic>? searchResults;

  // ========================================================================
  // 构造函数
  // ========================================================================

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

  // ========================================================================
  // 工厂构造函数
  // ========================================================================

  /// 创建用户消息
  ///
  /// [id] 消息唯一标识
  /// [content] 消息内容
  /// [status] 消息状态，默认为已发送
  factory ChatMessage.user({
    required String id,
    required String content,
    MessageStatus status = MessageStatus.sent,
  }) {
    return ChatMessage(
      id: id,
      type: ChatMessageType.user,
      content: content,
      status: status,
      timestamp: DateTime.now(),
    );
  }

  /// 创建AI助手消息
  ///
  /// [id] 消息唯一标识
  /// [content] 回复内容
  /// [status] 消息状态，默认为已完成
  /// [subMessages] 子消息列表
  /// [processingSteps] 处理步骤列表
  /// [searchResults] 搜索结果列表
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
  ///
  /// [id] 消息唯一标识
  /// [content] 系统提示内容
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
  ///
  /// [id] 消息唯一标识
  /// [toolName] 工具名称
  /// [toolData] 工具参数数据
  /// [description] 工具调用描述
  /// [status] 消息状态，默认为处理中
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
  ///
  /// [id] 消息唯一标识
  /// [content] 思考内容
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

  // ========================================================================
  // ========================================================================

  /// 复制消息并更新部分属性
  ///
  /// 使用不可变模式创建新的消息实例
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

  // ========================================================================
  // Getter属性
  // ========================================================================

  /// 获取工具名称
  ///
  /// 从toolData中提取工具名称
  String? get toolName => toolData?['toolName'] as String?;

  /// 是否为处理中状态
  ///
  /// 包括正在发送和正在处理两种状态
  bool get isProcessing =>
      status == MessageStatus.processing || status == MessageStatus.sending;

  /// 是否已完成
  bool get isCompleted => status == MessageStatus.completed;

  /// 是否出错
  bool get hasError => status == MessageStatus.error;
}
