class MCPToolDefinition {
  final String name;
  final String description;
  final Map<String, MCPParameterDefinition> parameters;
  final List<String> required;

  const MCPToolDefinition({
    required this.name,
    required this.description,
    required this.parameters,
    this.required = const [],
  });

  Map<String, dynamic> toFunctionSchema() => {
    'type': 'function',
    'function': {
      'name': name,
      'description': description,
      'parameters': {
        'type': 'object',
        'properties': {
          for (final entry in parameters.entries)
            entry.key: entry.value.toSchema(),
        },
        'required': required,
      },
    },
  };
}

class MCPParameterDefinition {
  final String type;
  final String description;
  final List<String>? enumValues;
  final dynamic defaultValue;

  const MCPParameterDefinition({
    required this.type,
    required this.description,
    this.enumValues,
    this.defaultValue,
  });

  Map<String, dynamic> toSchema() {
    final schema = <String, dynamic>{'type': type, 'description': description};
    if (enumValues != null) schema['enum'] = enumValues;
    if (defaultValue != null) schema['default'] = defaultValue;
    return schema;
  }
}

class MCPToolRegistry {
  MCPToolRegistry._();

  static final List<MCPToolDefinition> tools = [
    // 日记工具
    const MCPToolDefinition(
      name: 'get_latest_diary',
      description: '获取最新的日记条目。用于回答如"最近的日记是什么"、"最新写了什么日记"等问题。',
      parameters: {
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的日记数量，默认为1，最大为10',
          defaultValue: 1,
        ),
      },
    ),
    const MCPToolDefinition(
      name: 'get_diary_by_date',
      description: '获取指定日期的日记。用于回答如"今天的日记"、"昨天写了什么"等问题。支持相对日期和具体日期。',
      parameters: {
        'date': MCPParameterDefinition(
          type: 'string',
          description: '日期，格式为YYYY-MM-DD或相对日期如today,yesterday',
        ),
      },
      required: ['date'],
    ),
    const MCPToolDefinition(
      name: 'search_diary_by_content',
      description: '按内容关键词搜索日记。搜索日记的内容和标签。用于回答如"关于旅行的日记"等问题。',
      parameters: {
        'keyword': MCPParameterDefinition(
          type: 'string',
          description: '搜索关键词，多个关键词用逗号分隔',
        ),
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的最大数量，默认为20',
          defaultValue: 20,
        ),
      },
      required: ['keyword'],
    ),
    const MCPToolDefinition(
      name: 'get_diary_by_tag',
      description: '获取指定标签的日记。用于回答如"工作标签的日记"等问题。',
      parameters: {
        'tag': MCPParameterDefinition(type: 'string', description: '标签名称'),
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的最大数量，默认为10',
          defaultValue: 10,
        ),
      },
      required: ['tag'],
    ),
    const MCPToolDefinition(
      name: 'get_diary_count',
      description: '获取日记的总数量。',
      parameters: {},
    ),

    // 文章工具
    const MCPToolDefinition(
      name: 'get_latest_articles',
      description: '获取最新收藏的文章。用于回答如"最近收藏了什么文章"等问题。',
      parameters: {
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的文章数量，默认为5，最大为20',
          defaultValue: 5,
        ),
      },
    ),
    const MCPToolDefinition(
      name: 'search_articles',
      description: '按关键词搜索收藏的文章。搜索所有收藏文章的标题、内容、AI摘要和用户备注。',
      parameters: {
        'keyword': MCPParameterDefinition(
          type: 'string',
          description: '搜索关键词，多个关键词用逗号分隔',
        ),
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的最大数量，默认为20',
          defaultValue: 20,
        ),
      },
      required: ['keyword'],
    ),
    const MCPToolDefinition(
      name: 'get_favorite_articles',
      description: '获取标记为喜爱的文章。仅用于用户明确提到"喜爱"、"喜欢"时使用。',
      parameters: {
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的最大数量，默认为10',
          defaultValue: 10,
        ),
      },
    ),
    const MCPToolDefinition(
      name: 'get_article_count',
      description: '获取文章的总数量。',
      parameters: {},
    ),

    // 书籍工具
    const MCPToolDefinition(
      name: 'get_latest_books',
      description: '获取最新添加的书籍。用于回答如"最近在读什么书"等问题。',
      parameters: {
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的书籍数量，默认为5，最大为20',
          defaultValue: 5,
        ),
      },
    ),
    const MCPToolDefinition(
      name: 'search_books',
      description: '按标题、作者或分类搜索书籍。',
      parameters: {
        'keyword': MCPParameterDefinition(
          type: 'string',
          description: '搜索关键词，多个关键词用逗号分隔',
        ),
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的最大数量，默认为15',
          defaultValue: 15,
        ),
      },
      required: ['keyword'],
    ),
    const MCPToolDefinition(
      name: 'search_book_notes',
      description: '搜索读书笔记内容。当用户询问某个主题相关的内容时，也应该搜索读书笔记。',
      parameters: {
        'keyword': MCPParameterDefinition(
          type: 'string',
          description: '搜索关键词，多个关键词用逗号分隔',
        ),
        'limit': MCPParameterDefinition(
          type: 'integer',
          description: '返回的最大数量，默认为20',
          defaultValue: 20,
        ),
      },
      required: ['keyword'],
    ),
    const MCPToolDefinition(
      name: 'get_book_viewpoints',
      description: '获取书籍的读书笔记/观点。需要先通过搜索获取书籍ID。',
      parameters: {
        'book_id': MCPParameterDefinition(type: 'integer', description: '书籍ID'),
      },
      required: ['book_id'],
    ),
    const MCPToolDefinition(
      name: 'get_book_count',
      description: '获取书籍的总数量。',
      parameters: {},
    ),

    // 综合统计工具
    const MCPToolDefinition(
      name: 'get_statistics',
      description: '获取应用的综合统计信息。返回文章、日记、书籍的数量统计。',
      parameters: {},
    ),
  ];

  static List<Map<String, dynamic>> getFunctionSchemas() =>
      tools.map((tool) => tool.toFunctionSchema()).toList();

  static MCPToolDefinition? findTool(String name) {
    try {
      return tools.firstWhere((tool) => tool.name == name);
    } catch (_) {
      return null;
    }
  }

  static String getToolDescriptions() {
    final buffer = StringBuffer()..writeln('## 可用工具\n');
    for (final tool in tools) {
      buffer.writeln('### ${tool.name}');
      buffer.writeln(tool.description);
      if (tool.parameters.isNotEmpty) {
        buffer.writeln('\n参数：');
        for (final entry in tool.parameters.entries) {
          final requiredMark = tool.required.contains(entry.key)
              ? '（必需）'
              : '（可选）';
          buffer.writeln(
            '- **${entry.key}** $requiredMark: ${entry.value.description}',
          );
        }
      }
      buffer.writeln();
    }
    return buffer.toString();
  }
}
