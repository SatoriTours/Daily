/// 工具调用模型
class ToolCall {
  /// 工具类型
  final ToolType type;

  /// 工具名称
  final String name;

  /// 工具描述
  final String description;

  /// 工具参数
  final Map<String, dynamic> parameters;

  /// 工具调用ID
  final String id;

  /// 创建时间
  final DateTime timestamp;

  /// 执行状态
  final ToolCallStatus status;

  /// 执行结果
  final String? result;

  /// 错误信息
  final String? error;

  const ToolCall({
    required this.type,
    required this.name,
    required this.description,
    required this.parameters,
    required this.id,
    required this.timestamp,
    this.status = ToolCallStatus.pending,
    this.result,
    this.error,
  });

  /// 创建搜索文章工具调用
  factory ToolCall.searchArticles({required String query, Map<String, dynamic>? filters}) {
    return ToolCall(
      type: ToolType.searchArticles,
      name: 'search_articles',
      description: '📄 正在文章中搜索「$query」...',
      id: 'tool_${DateTime.now().millisecondsSinceEpoch}',
      timestamp: DateTime.now(),
      parameters: {'query': query, 'filters': filters ?? {}},
    );
  }

  /// 创建搜索日记工具调用
  factory ToolCall.searchDiary({required String query, DateTimeRange? dateRange}) {
    return ToolCall(
      type: ToolType.searchDiary,
      name: 'search_diary',
      description: '📔 正在日记中搜索「$query」...',
      id: 'tool_${DateTime.now().millisecondsSinceEpoch}',
      timestamp: DateTime.now(),
      parameters: {'query': query, 'dateRange': dateRange},
    );
  }

  /// 创建搜索书籍工具调用
  factory ToolCall.searchBooks({required String query}) {
    return ToolCall(
      type: ToolType.searchBooks,
      name: 'search_books',
      description: '📖 正在书籍中搜索「$query」...',
      id: 'tool_${DateTime.now().millisecondsSinceEpoch}',
      timestamp: DateTime.now(),
      parameters: {'query': query},
    );
  }

  /// 创建综合搜索工具调用
  factory ToolCall.searchAll({required String query}) {
    return ToolCall(
      type: ToolType.searchAll,
      name: 'search_all',
      description: '🔍 正在搜索「$query」相关的所有内容...',
      id: 'tool_${DateTime.now().millisecondsSinceEpoch}',
      timestamp: DateTime.now(),
      parameters: {'query': query},
    );
  }

  /// 复制并更新状态
  ToolCall copyWith({
    ToolType? type,
    String? name,
    String? description,
    Map<String, dynamic>? parameters,
    String? id,
    DateTime? timestamp,
    ToolCallStatus? status,
    String? result,
    String? error,
  }) {
    return ToolCall(
      type: type ?? this.type,
      name: name ?? this.name,
      description: description ?? this.description,
      parameters: parameters ?? this.parameters,
      id: id ?? this.id,
      timestamp: timestamp ?? this.timestamp,
      status: status ?? this.status,
      result: result ?? this.result,
      error: error ?? this.error,
    );
  }

  /// 标记为执行中
  ToolCall markAsRunning() {
    return copyWith(status: ToolCallStatus.running);
  }

  /// 标记为完成
  ToolCall markAsCompleted(String result) {
    return copyWith(status: ToolCallStatus.completed, result: result);
  }

  /// 标记为失败
  ToolCall markAsFailed(String error) {
    return copyWith(status: ToolCallStatus.failed, error: error);
  }

  /// 是否正在执行
  bool get isRunning => status == ToolCallStatus.running;

  /// 是否已完成
  bool get isCompleted => status == ToolCallStatus.completed;

  /// 是否失败
  bool get hasFailed => status == ToolCallStatus.failed;

  /// 获取查询参数
  String? get query => parameters['query'] as String?;

  /// 获取过滤条件
  Map<String, dynamic> get filters => parameters['filters'] as Map<String, dynamic>? ?? {};

  /// 获取日期范围
  DateTimeRange? get dateRange => parameters['dateRange'] as DateTimeRange?;
}

/// 工具类型枚举
enum ToolType {
  searchArticles('搜索文章'),
  searchDiary('搜索日记'),
  searchBooks('搜索书籍'),
  searchAll('综合搜索');

  const ToolType(this.displayName);

  final String displayName;
}

/// 工具调用状态枚举
enum ToolCallStatus {
  pending('待执行'),
  running('执行中'),
  completed('已完成'),
  failed('失败');

  const ToolCallStatus(this.displayName);

  final String displayName;
}

/// 日期范围类
class DateTimeRange {
  final DateTime start;
  final DateTime end;

  const DateTimeRange({required this.start, required this.end});

  /// 获取描述
  String get description {
    return '${start.month}/${start.day} - ${end.month}/${end.day}';
  }

  /// 获取持续天数
  int get days => end.difference(start).inDays + 1;
}
