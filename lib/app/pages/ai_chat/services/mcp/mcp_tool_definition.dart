/// MCP 工具定义
///
/// 定义所有可供 AI 调用的工具，包括参数规范和描述
/// 基于 Model Context Protocol 概念设计
class MCPToolDefinition {
  /// 工具名称
  final String name;

  /// 工具描述（用于 AI 理解工具用途）
  final String description;

  /// 参数定义
  final Map<String, MCPParameterDefinition> parameters;

  /// 必需参数列表
  final List<String> required;

  const MCPToolDefinition({
    required this.name,
    required this.description,
    required this.parameters,
    this.required = const [],
  });

  /// 转换为 OpenAI function calling 格式
  Map<String, dynamic> toFunctionSchema() {
    return {
      'type': 'function',
      'function': {
        'name': name,
        'description': description,
        'parameters': {
          'type': 'object',
          'properties': {for (final entry in parameters.entries) entry.key: entry.value.toSchema()},
          'required': required,
        },
      },
    };
  }
}

/// MCP 参数定义
class MCPParameterDefinition {
  /// 参数类型
  final String type;

  /// 参数描述
  final String description;

  /// 枚举值（可选）
  final List<String>? enumValues;

  /// 默认值（可选）
  final dynamic defaultValue;

  const MCPParameterDefinition({required this.type, required this.description, this.enumValues, this.defaultValue});

  /// 转换为 JSON Schema 格式
  Map<String, dynamic> toSchema() {
    final schema = <String, dynamic>{'type': type, 'description': description};
    if (enumValues != null) {
      schema['enum'] = enumValues;
    }
    if (defaultValue != null) {
      schema['default'] = defaultValue;
    }
    return schema;
  }
}

/// MCP 工具注册表
///
/// 集中管理所有可用的工具定义
class MCPToolRegistry {
  MCPToolRegistry._();

  /// 所有可用工具
  static final List<MCPToolDefinition> tools = [
    // ========================================================================
    // 日记工具
    // ========================================================================
    MCPToolDefinition(
      name: 'get_latest_diary',
      description: '''获取最新的日记条目。
用于回答如"最近的日记是什么"、"最新写了什么日记"、"上一篇日记内容"等问题。
返回最近创建的 N 条日记。''',
      parameters: {
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的日记数量，默认为 1，最大为 10', defaultValue: 1),
      },
    ),
    MCPToolDefinition(
      name: 'get_diary_by_date',
      description: '''获取指定日期的日记。
用于回答如"今天的日记"、"昨天写了什么"、"上周一的日记"等问题。
支持相对日期（today, yesterday）和具体日期。''',
      parameters: {
        'date': const MCPParameterDefinition(type: 'string', description: '日期，格式为 YYYY-MM-DD 或相对日期如 today, yesterday'),
      },
      required: ['date'],
    ),
    MCPToolDefinition(
      name: 'search_diary_by_content',
      description: '''按内容关键词搜索日记。
搜索日记的内容和标签。
用于回答如"关于旅行的日记"、"记录了工作的日记"等问题。
按创建时间倒序返回匹配的日记。''',
      parameters: {
        'keyword': const MCPParameterDefinition(type: 'string', description: '搜索关键词，多个关键词用逗号分隔'),
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的最大数量，默认为 20', defaultValue: 20),
      },
      required: ['keyword'],
    ),
    MCPToolDefinition(
      name: 'get_diary_by_tag',
      description: '''获取指定标签的日记。
用于回答如"工作标签的日记"、"标记为重要的日记"等问题。''',
      parameters: {
        'tag': const MCPParameterDefinition(type: 'string', description: '标签名称'),
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的最大数量，默认为 10', defaultValue: 10),
      },
      required: ['tag'],
    ),
    MCPToolDefinition(
      name: 'get_diary_count',
      description: '''获取日记的总数量。
用于回答如"我写了多少日记"、"日记总数"等问题。''',
      parameters: {},
    ),

    // ========================================================================
    // 文章工具
    // ========================================================================
    MCPToolDefinition(
      name: 'get_latest_articles',
      description: '''获取最新收藏的文章。
用于回答如"最近收藏了什么文章"、"最新的文章是什么"等问题。
返回最近创建的 N 篇文章。''',
      parameters: {
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的文章数量，默认为 5，最大为 20', defaultValue: 5),
      },
    ),
    MCPToolDefinition(
      name: 'search_articles',
      description: '''按关键词搜索收藏的文章。
搜索所有收藏文章的标题、内容、AI摘要和用户备注。
用于回答如"关于 Flutter 的文章"、"收藏的文章中有关于 xxx 的吗"、"从收藏的文章中找 xxx"等问题。
注意：用户说的"收藏的文章"就是指所有文章，应该用这个工具搜索。''',
      parameters: {
        'keyword': const MCPParameterDefinition(type: 'string', description: '搜索关键词，多个关键词用逗号分隔'),
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的最大数量，默认为 20', defaultValue: 20),
      },
      required: ['keyword'],
    ),
    MCPToolDefinition(
      name: 'get_favorite_articles',
      description: '''获取标记为喜爱的文章。
仅用于用户明确提到"喜爱"、"喜欢"、"标记为喜爱"、"我喜欢的文章"时使用。
注意："收藏的文章"不等于"喜爱的文章"，搜索收藏的文章应使用 search_articles。''',
      parameters: {
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的最大数量，默认为 10', defaultValue: 10),
      },
    ),
    MCPToolDefinition(
      name: 'get_article_count',
      description: '''获取文章的总数量。
用于回答如"我收藏了多少文章"、"文章总数"等问题。''',
      parameters: {},
    ),

    // ========================================================================
    // 书籍工具
    // ========================================================================
    MCPToolDefinition(
      name: 'get_latest_books',
      description: '''获取最新添加的书籍。
用于回答如"最近在读什么书"、"最新添加的书"等问题。''',
      parameters: {
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的书籍数量，默认为 5，最大为 20', defaultValue: 5),
      },
    ),
    MCPToolDefinition(
      name: 'search_books',
      description: '''按标题、作者或分类搜索书籍。
用于回答如"有没有《xxx》这本书"、"xxx 作者的书"、"关于投资的书"等问题。''',
      parameters: {
        'keyword': const MCPParameterDefinition(type: 'string', description: '搜索关键词（匹配书名、作者或分类），多个关键词用逗号分隔'),
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的最大数量，默认为 15', defaultValue: 15),
      },
      required: ['keyword'],
    ),
    MCPToolDefinition(
      name: 'search_book_notes',
      description: '''搜索读书笔记内容。
用于回答如"关于 xxx 的读书笔记"、"书中关于 xxx 的内容"等问题。
当用户询问某个主题相关的内容时，也应该搜索读书笔记。''',
      parameters: {
        'keyword': const MCPParameterDefinition(type: 'string', description: '搜索关键词（匹配笔记标题或内容），多个关键词用逗号分隔'),
        'limit': const MCPParameterDefinition(type: 'integer', description: '返回的最大数量，默认为 20', defaultValue: 20),
      },
      required: ['keyword'],
    ),
    MCPToolDefinition(
      name: 'get_book_viewpoints',
      description: '''获取书籍的读书笔记/观点。
用于回答如"这本书的笔记"、"书中的观点"等问题。
需要先通过搜索获取书籍 ID。''',
      parameters: {'book_id': const MCPParameterDefinition(type: 'integer', description: '书籍 ID')},
      required: ['book_id'],
    ),
    MCPToolDefinition(
      name: 'get_book_count',
      description: '''获取书籍的总数量。
用于回答如"我有多少本书"、"书籍总数"等问题。''',
      parameters: {},
    ),

    // ========================================================================
    // 综合统计工具
    // ========================================================================
    MCPToolDefinition(
      name: 'get_statistics',
      description: '''获取应用的综合统计信息。
用于回答如"我的数据概况"、"有多少内容"等问题。
返回文章、日记、书籍的数量统计。''',
      parameters: {},
    ),
  ];

  /// 获取所有工具的 function schema（用于 OpenAI API）
  static List<Map<String, dynamic>> getFunctionSchemas() {
    return tools.map((tool) => tool.toFunctionSchema()).toList();
  }

  /// 根据名称查找工具
  static MCPToolDefinition? findTool(String name) {
    try {
      return tools.firstWhere((tool) => tool.name == name);
    } catch (_) {
      return null;
    }
  }

  /// 获取工具描述文本（用于系统提示词）
  static String getToolDescriptions() {
    final buffer = StringBuffer();
    buffer.writeln('## 可用工具\n');

    for (final tool in tools) {
      buffer.writeln('### ${tool.name}');
      buffer.writeln(tool.description);
      if (tool.parameters.isNotEmpty) {
        buffer.writeln('\n参数：');
        for (final entry in tool.parameters.entries) {
          final param = entry.value;
          final requiredMark = tool.required.contains(entry.key) ? '（必需）' : '（可选）';
          buffer.writeln('- **${entry.key}** $requiredMark: ${param.description}');
        }
      }
      buffer.writeln();
    }

    return buffer.toString();
  }
}
