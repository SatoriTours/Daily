import 'dart:convert';
import 'package:openai_dart/openai_dart.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/pages/ai_chat/models/search_result.dart';
import 'mcp_tool_definition.dart';
import 'mcp_tool_executor.dart';

/// MCP Agent 处理结果
///
/// 包含 AI 生成的答案和搜索到的原始数据
class MCPAgentResult {
  /// AI 生成的总结答案
  final String answer;

  /// 搜索到的原始数据（用于展示给用户查看）
  final List<SearchResult> searchResults;

  const MCPAgentResult({required this.answer, required this.searchResults});
}

/// MCP Agent 服务
///
/// 基于 Function Calling 的智能代理服务
/// 让 AI 自主决定调用哪些工具来回答用户问题
class MCPAgentService {
  // ========================================================================
  // 单例模式
  // ========================================================================

  static MCPAgentService? _instance;
  static MCPAgentService get i => _instance ??= MCPAgentService._();
  MCPAgentService._();

  // ========================================================================
  // 常量配置
  // ========================================================================

  /// AI 功能类型（使用通用配置）
  static const int _functionType = 0;

  /// 最大工具调用轮次（防止无限循环）
  static const int _maxToolCallRounds = 5;

  // ========================================================================
  // 依赖服务
  // ========================================================================

  final MCPToolExecutor _toolExecutor = MCPToolExecutor.i;

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 处理用户查询
  ///
  /// [query] 用户查询内容
  /// [onStep] 步骤更新回调 (stepName, status)
  /// [onToolCall] 工具调用回调
  ///
  /// 返回包含 AI 答案和搜索结果的 MCPAgentResult
  Future<MCPAgentResult> processQuery({
    required String query,
    required Function(String step, String status) onStep,
    Function(String toolName, Map<String, dynamic> args)? onToolCall,
  }) async {
    logger.i('[MCPAgentService] ========== 开始处理查询 ==========');
    logger.i('[MCPAgentService] 查询内容: $query');

    // 当前步骤名称（用于状态切换）
    String? currentStepName;

    // 收集的搜索结果
    final List<SearchResult> collectedResults = [];

    // 辅助函数：更新步骤状态
    void updateStep(String stepName, String status) {
      // 如果有前一个步骤且状态是 processing，先完成它
      if (currentStepName != null && currentStepName != stepName) {
        onStep(currentStepName!, 'completed');
      }
      currentStepName = stepName;
      onStep(stepName, status);
    }

    try {
      // 创建 OpenAI 客户端
      final client = await _createClient();
      if (client == null) {
        return MCPAgentResult(answer: _buildErrorResponse('AI 服务未配置，请先在设置中配置 OpenAI API'), searchResults: []);
      }

      // 步骤1: 理解问题
      updateStep('正在理解您的问题...', 'processing');

      // 构建初始消息
      final messages = <ChatCompletionMessage>[
        ChatCompletionMessage.system(content: _buildSystemPrompt()),
        ChatCompletionMessage.user(content: ChatCompletionUserMessageContent.string(query)),
      ];

      // 执行对话循环（支持多轮工具调用）
      var currentRound = 0;
      String? finalAnswer;

      while (currentRound < _maxToolCallRounds) {
        currentRound++;
        logger.i('[MCPAgentService] 第 $currentRound 轮对话');

        // 发送请求
        final response = await _sendChatCompletion(client, messages);
        if (response == null) {
          logger.e('[MCPAgentService] AI 请求返回 null');
          return MCPAgentResult(answer: _buildErrorResponse('AI 请求失败，请稍后重试'), searchResults: collectedResults);
        }

        final choice = response.choices.first;
        final message = choice.message;

        logger.i(
          '[MCPAgentService] AI 响应 - 内容: ${message.content?.substring(0, (message.content?.length ?? 0).clamp(0, 100))}...',
        );
        logger.i('[MCPAgentService] AI 响应 - 工具调用数量: ${message.toolCalls?.length ?? 0}');

        // 检查是否有工具调用
        if (message.toolCalls != null && message.toolCalls!.isNotEmpty) {
          // 步骤2: 查询数据
          updateStep('正在查询数据...', 'processing');

          // 将助手消息添加到历史
          messages.add(ChatCompletionMessage.assistant(toolCalls: message.toolCalls));

          // 执行所有工具调用
          for (final toolCall in message.toolCalls!) {
            final toolName = toolCall.function.name;
            final toolArgs = toolCall.function.arguments;

            logger.i('[MCPAgentService] 调用工具: $toolName');
            logger.i('[MCPAgentService] 工具参数: $toolArgs');

            // 通知 UI 工具调用
            if (onToolCall != null) {
              final argsMap = _parseArguments(toolArgs);
              onToolCall(toolName, argsMap);
            }

            // 执行工具并收集搜索结果
            final toolResult = await _toolExecutor.executeTool(toolName, toolArgs);
            final searchResults = _extractSearchResults(toolName, toolResult);
            collectedResults.addAll(searchResults);

            logger.i('[MCPAgentService] 工具结果: ${_truncateLog(toolResult)}');
            logger.i('[MCPAgentService] 收集到 ${searchResults.length} 条搜索结果');

            // 将工具结果添加到消息历史
            messages.add(ChatCompletionMessage.tool(toolCallId: toolCall.id, content: toolResult));
          }

          // 步骤3: 生成回答（继续循环让 AI 生成答案）
          updateStep('正在生成回答...', 'processing');
          logger.i('[MCPAgentService] 工具执行完成，继续请求 AI 生成答案...');

          // 继续循环，让 AI 根据工具结果生成答案
          continue;
        } else {
          // 没有工具调用，说明 AI 已经准备好回答
          finalAnswer = message.content;
          logger.i('[MCPAgentService] AI 生成最终答案: ${_truncateLog(finalAnswer ?? '')}');

          // 完成当前步骤并标记整体完成
          if (currentStepName != null) {
            onStep(currentStepName!, 'completed');
          }
          onStep('完成', 'completed');
          break;
        }
      }

      // 如果达到最大轮次但没有最终答案，发送最后一次请求获取答案
      if (finalAnswer == null) {
        logger.w('[MCPAgentService] 达到最大工具调用轮次，强制获取答案');
        updateStep('正在整理答案...', 'processing');

        final response = await _sendChatCompletion(client, messages);
        finalAnswer = response?.choices.first.message.content;

        // 完成当前步骤
        if (currentStepName != null) {
          onStep(currentStepName!, 'completed');
        }
        onStep('完成', 'completed');
      }

      logger.i('[MCPAgentService] ========== 处理完成 ==========');
      logger.i('[MCPAgentService] 总共收集 ${collectedResults.length} 条搜索结果');

      return MCPAgentResult(answer: finalAnswer ?? _buildErrorResponse('无法生成回答'), searchResults: collectedResults);
    } catch (e, stackTrace) {
      logger.e('[MCPAgentService] 处理失败', error: e, stackTrace: stackTrace);
      // 标记当前步骤为错误
      if (currentStepName != null) {
        onStep(currentStepName!, 'error');
      }
      onStep('处理失败', 'error');
      return MCPAgentResult(answer: _buildErrorResponse('处理失败: $e'), searchResults: collectedResults);
    }
  }

  // ========================================================================
  // 私有方法 - 搜索结果提取
  // ========================================================================

  /// 从工具结果中提取搜索结果
  ///
  /// [toolName] 工具名称
  /// [toolResult] 工具返回的 JSON 字符串
  List<SearchResult> _extractSearchResults(String toolName, String toolResult) {
    logger.d('[MCPAgentService] 开始提取搜索结果, 工具: $toolName');
    try {
      final data = jsonDecode(toolResult) as Map<String, dynamic>;
      logger.d('[MCPAgentService] 解析数据键: ${data.keys.toList()}');

      // 提取日记结果
      if (toolName.contains('diary') && data['diaries'] != null) {
        final diaries = data['diaries'] as List;
        logger.d('[MCPAgentService] 找到 ${diaries.length} 条日记数据');
        return _extractDiaryResults(diaries);
      }

      // 提取文章结果
      if (toolName.contains('article') && data['articles'] != null) {
        final articles = data['articles'] as List;
        logger.d('[MCPAgentService] 找到 ${articles.length} 条文章数据');
        return _extractArticleResults(articles);
      }

      // 提取书籍结果
      if (toolName.contains('book') && data['books'] != null) {
        final books = data['books'] as List;
        logger.d('[MCPAgentService] 找到 ${books.length} 条书籍数据');
        return _extractBookResults(books);
      }

      logger.d('[MCPAgentService] 未匹配到任何数据类型');
      return [];
    } catch (e) {
      logger.w('[MCPAgentService] 提取搜索结果失败: $e');
      return [];
    }
  }

  /// 从日记数据提取搜索结果
  List<SearchResult> _extractDiaryResults(List diaries) {
    final results = <SearchResult>[];
    for (var i = 0; i < diaries.length; i++) {
      try {
        final d = diaries[i] as Map<String, dynamic>;

        // 处理 tags 字段（可能是字符串或列表）
        List<String>? tagsList;
        final tagsValue = d['tags'];
        if (tagsValue is List) {
          tagsList = tagsValue.map((t) => t.toString()).toList();
        } else if (tagsValue is String && tagsValue.isNotEmpty) {
          tagsList = tagsValue.split(',').map((t) => t.trim()).where((t) => t.isNotEmpty).toList();
        }

        final result = SearchResult.fromDiary(
          id: d['id'] as int,
          title: _generateDiaryTitle(d),
          summary: _truncateContent(d['content'] as String?, maxLength: 100),
          createdAt: _parseDateTime(d['createdAt']),
          tags: tagsList,
        );
        results.add(result);
        logger.d('[MCPAgentService] 日记[$i]: id=${result.id}, title=${result.title}');
      } catch (e) {
        logger.w('[MCPAgentService] 提取日记[$i]失败: $e');
      }
    }
    return results;
  }

  /// 从文章数据提取搜索结果
  List<SearchResult> _extractArticleResults(List articles) {
    final results = <SearchResult>[];
    for (var i = 0; i < articles.length; i++) {
      try {
        final a = articles[i] as Map<String, dynamic>;
        final result = SearchResult.fromArticle(
          id: a['id'] as int,
          title: a['title'] as String? ?? '未知标题',
          summary: _truncateContent(a['summary'] as String?, maxLength: 100),
          createdAt: _parseDateTime(a['createdAt']),
          isFavorite: a['isFavorite'] as bool?,
        );
        results.add(result);
        logger.d('[MCPAgentService] 文章[$i]: id=${result.id}, title=${result.title}');
      } catch (e) {
        logger.w('[MCPAgentService] 提取文章[$i]失败: $e');
      }
    }
    return results;
  }

  /// 从书籍数据提取搜索结果
  List<SearchResult> _extractBookResults(List books) {
    final results = <SearchResult>[];
    for (var i = 0; i < books.length; i++) {
      try {
        final b = books[i] as Map<String, dynamic>;
        final result = SearchResult.fromBook(
          id: b['id'] as int,
          title: b['title'] as String? ?? '未知书名',
          summary: b['author'] as String?,
          createdAt: _parseDateTime(b['createdAt']),
        );
        results.add(result);
        logger.d('[MCPAgentService] 书籍[$i]: id=${result.id}, title=${result.title}');
      } catch (e) {
        logger.w('[MCPAgentService] 提取书籍[$i]失败: $e');
      }
    }
    return results;
  }

  /// 生成日记标题
  String _generateDiaryTitle(Map<String, dynamic> diary) {
    final createdAt = _parseDateTime(diary['createdAt']);
    if (createdAt != null) {
      return '${createdAt.year}年${createdAt.month}月${createdAt.day}日的日记';
    }
    return '日记';
  }

  /// 截断内容
  String? _truncateContent(String? content, {int maxLength = 100}) {
    if (content == null || content.isEmpty) return null;
    if (content.length <= maxLength) return content;
    return '${content.substring(0, maxLength)}...';
  }

  /// 解析日期时间
  DateTime? _parseDateTime(dynamic value) {
    if (value == null) return null;
    if (value is DateTime) return value;
    if (value is String) {
      try {
        return DateTime.parse(value);
      } catch (_) {
        return null;
      }
    }
    return null;
  }

  // ========================================================================
  // 私有方法 - OpenAI 客户端
  // ========================================================================

  /// 创建 OpenAI 客户端
  Future<OpenAIClient?> _createClient() async {
    try {
      // 尝试从AI配置服务获取配置
      final apiAddress = AIConfigService.i.getApiAddressForFunction(_functionType);
      final apiToken = AIConfigService.i.getApiTokenForFunction(_functionType);

      String apiKey;
      String baseUrl;

      // 如果特定功能配置为空，则使用通用设置
      if (apiAddress.isEmpty || apiToken.isEmpty) {
        apiKey = SettingRepository.i.getSetting(SettingService.openAITokenKey);
        baseUrl = SettingRepository.i.getSetting(SettingService.openAIAddressKey);
      } else {
        apiKey = apiToken;
        baseUrl = apiAddress;
      }

      if (apiKey.isEmpty || baseUrl.isEmpty) {
        logger.w('[MCPAgentService] AI 配置不完整');
        return null;
      }

      return OpenAIClient(apiKey: apiKey, baseUrl: baseUrl);
    } catch (e) {
      logger.e('[MCPAgentService] 创建客户端失败', error: e);
      return null;
    }
  }

  /// 发送聊天完成请求
  Future<CreateChatCompletionResponse?> _sendChatCompletion(
    OpenAIClient client,
    List<ChatCompletionMessage> messages,
  ) async {
    try {
      final modelName = AIConfigService.i.getModelNameForFunction(_functionType);

      logger.i('[MCPAgentService] 发送请求 - 模型: $modelName, 消息数: ${messages.length}');

      final response = await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: ChatCompletionModel.modelId(modelName),
          messages: messages,
          tools: _buildTools(),
          toolChoice: const ChatCompletionToolChoiceOption.mode(ChatCompletionToolChoiceMode.auto),
          temperature: 0.7,
        ),
      );

      logger.i('[MCPAgentService] 请求成功 - 选择数: ${response.choices.length}');

      return response;
    } catch (e, stackTrace) {
      logger.e('[MCPAgentService] 聊天请求失败: $e', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  // ========================================================================
  // 私有方法 - 提示词和工具定义
  // ========================================================================

  /// 构建系统提示词
  String _buildSystemPrompt() {
    return '''你是一个智能助手，专门帮助用户从他们的个人数据中查找和总结信息。用户的数据包括：
- **日记**: 用户的个人日记记录
- **文章**: 用户收藏的网页文章
- **书籍**: 用户添加的书籍和读书笔记

## 核心规则（非常重要！）

**你只能基于用户的个人数据来回答问题，不要使用你的通用知识来回答。**

当用户提问时，你必须：
1. **首先使用搜索工具**查找用户数据中的相关内容
2. **基于搜索结果**来生成回答
3. 如果没有找到相关内容，告知用户"在您的数据中没有找到相关信息"

**禁止行为**：
- ❌ 不要直接用你的知识回答问题
- ❌ 不要跳过搜索步骤直接给答案
- ❌ 不要编造用户数据中不存在的内容

**正确行为**：
- ✅ 用户问"如何办理海外电话卡" → 搜索文章、日记、书籍中的相关内容，然后总结
- ✅ 用户问"最近写了什么" → 获取最新的日记
- ✅ 用户问"有什么好书推荐" → 获取书籍列表并推荐

## 搜索策略（最重要！必须严格遵守！）

### 关键词生成原则

**目标**：生成精准的关键词，既能找到相关内容，又不会匹配到无关文章。

**关键词生成规则**：
1. **保留核心词组**：用户问题中的核心概念要作为整体保留（如"电话卡"、"英语学习"）
2. **添加精准同义词**：只添加意思完全相同的词（如"手机卡"="电话卡"）
3. **避免泛化词**：不要用太宽泛的单字词（如"海外"、"国际"单独使用会匹配太多无关内容）
4. **组合词优先**：优先使用组合词（如"海外电话卡"、"境外手机卡"），而不是拆开的单词

**关键词生成示例**：

| 用户问题 | 正确的关键词 | 错误的关键词 |
|---------|------------|------------|
| 海外电话卡如何办理 | 电话卡,手机卡,SIM卡,境外手机,国际漫游 | 海外,国际,出国（太泛） |
| 如何提高英语水平 | 英语学习,学英语,英语口语,英语听力 | 学习,提高（太泛） |
| 投资理财有什么建议 | 投资理财,理财产品,基金投资,股票投资 | 投资,建议（太泛） |

### 搜索执行规则

1. **keyword 参数格式**：关键词用逗号分隔，例如：`"电话卡,手机卡,SIM卡,国际漫游"`
2. **关键词数量**：3-6 个精准关键词即可，不需要太多
3. **关键词长度**：2-6 个字为宜，可以是词组
4. **同时搜索多个数据源**（如果用户没有明确指定范围）

### 错误 vs 正确示例

❌ **错误**（关键词太泛，会匹配很多无关内容）：
```json
{"keyword": "海外,电话卡,境外,国际,出国,通信"}
```
→ "海外"会匹配到"海外版产品"、"海外新闻"等无关文章

✅ **正确**（关键词精准，围绕核心主题）：
```json
{"keyword": "电话卡,手机卡,SIM卡,国际漫游,境外手机卡"}
```
→ 只会匹配真正关于电话卡的文章

## 工具使用指南

### 日记相关
- `get_latest_diary`: 获取最新的日记
- `get_diary_by_date`: 获取指定日期的日记，**date 参数必须是 YYYY-MM-DD 格式**
- `search_diary_by_content`: 按关键词搜索日记内容
- `get_diary_by_tag`: 按标签获取日记
- `get_diary_count`: 获取日记总数

### 文章相关
- `get_latest_articles`: 获取最新收藏的文章
- `search_articles`: 按关键词搜索文章（用户说的"收藏的文章"就是所有文章）
- `get_favorite_articles`: 获取标记为"喜爱"的文章（仅当用户明确说"喜爱"、"喜欢"时使用）
- `get_article_count`: 获取文章总数

### 书籍相关
- `get_latest_books`: 获取最新添加的书籍
- `search_books`: 按书名或作者搜索书籍
- `get_book_viewpoints`: 获取书籍的读书笔记
- `get_book_count`: 获取书籍总数

### 综合
- `get_statistics`: 获取应用数据统计

## 日期处理规则（重要！）

当用户提到日期时，你必须将其转换为 **YYYY-MM-DD** 格式：
- "今天" → "${DateTime.now().toString().substring(0, 10)}"
- "昨天" → "${DateTime.now().subtract(const Duration(days: 1)).toString().substring(0, 10)}"
- "前天" → "${DateTime.now().subtract(const Duration(days: 2)).toString().substring(0, 10)}"
- "上周一" → 计算出具体日期

**示例**：用户问"今天的日记" → 调用 `get_diary_by_date(date: "${DateTime.now().toString().substring(0, 10)}")`

## 回答格式要求

1. **总结式回答**：用自然语言总结，不要返回原始 JSON
2. **Markdown 格式**：重要信息用 **加粗**
3. **适当使用表情**：让回答更生动
4. **无结果时**：友好告知，建议其他搜索条件

当前时间: ${DateTime.now().toString().substring(0, 19)}
''';
  }

  /// 构建工具定义（OpenAI 格式）
  List<ChatCompletionTool> _buildTools() {
    return MCPToolRegistry.tools.map((tool) {
      return ChatCompletionTool(
        type: ChatCompletionToolType.function,
        function: FunctionObject(
          name: tool.name,
          description: tool.description,
          parameters: {
            'type': 'object',
            'properties': {for (final entry in tool.parameters.entries) entry.key: entry.value.toSchema()},
            'required': tool.required,
          },
        ),
      );
    }).toList();
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 解析工具参数
  Map<String, dynamic> _parseArguments(String arguments) {
    try {
      return jsonDecode(arguments) as Map<String, dynamic>;
    } catch (_) {
      return {};
    }
  }

  /// 构建错误响应
  String _buildErrorResponse(String message) {
    return '''😔 **出现问题**

$message

**建议**:
- 检查网络连接
- 确保 AI 服务配置正确
- 稍后重试''';
  }

  /// 截断日志内容
  String _truncateLog(String content, {int maxLength = 500}) {
    if (content.length <= maxLength) return content;
    return '${content.substring(0, maxLength)}...';
  }
}
