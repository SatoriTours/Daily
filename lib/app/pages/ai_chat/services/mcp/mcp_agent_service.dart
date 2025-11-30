import 'dart:convert';
import 'package:openai_dart/openai_dart.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/pages/ai_chat/models/search_result.dart';
import 'mcp_tool_definition.dart';
import 'mcp_tool_executor.dart';
import 'mcp_prompts.dart';

/// MCP Agent 处理结果
class MCPAgentResult {
  final String answer;
  final List<SearchResult> searchResults;
  const MCPAgentResult({required this.answer, required this.searchResults});
}

/// MCP Agent 服务
///
/// 基于 Function Calling 的智能代理服务
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

  static const int _functionType = 0;
  static const int _maxToolCallRounds = 5;

  // ========================================================================
  // 依赖服务
  // ========================================================================

  final MCPToolExecutor _toolExecutor = MCPToolExecutor.i;

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 处理用户查询
  Future<MCPAgentResult> processQuery({
    required String query,
    required Function(String step, String status) onStep,
    Function(String toolName, Map<String, dynamic> args)? onToolCall,
  }) async {
    logger.i('[MCPAgentService] 开始处理查询: $query');

    String? currentStepName;
    final collectedResults = <SearchResult>[];

    void updateStep(String stepName, String status) {
      if (currentStepName != null && currentStepName != stepName) {
        onStep(currentStepName!, 'completed');
      }
      currentStepName = stepName;
      onStep(stepName, status);
    }

    try {
      final client = await _createClient();
      if (client == null) {
        return MCPAgentResult(answer: MCPPrompts.buildErrorResponse('AI 服务未配置，请先在设置中配置 OpenAI API'), searchResults: []);
      }

      updateStep('正在理解您的问题...', 'processing');

      final messages = <ChatCompletionMessage>[
        ChatCompletionMessage.system(content: MCPPrompts.buildSystemPrompt()),
        ChatCompletionMessage.user(content: ChatCompletionUserMessageContent.string(query)),
      ];

      String? finalAnswer;
      var currentRound = 0;

      while (currentRound < _maxToolCallRounds) {
        currentRound++;
        logger.i('[MCPAgentService] 第 $currentRound 轮对话');

        final response = await _sendChatCompletion(client, messages);
        if (response == null) {
          return MCPAgentResult(
            answer: MCPPrompts.buildErrorResponse('AI 请求失败，请稍后重试'),
            searchResults: collectedResults,
          );
        }

        final message = response.choices.first.message;

        if (message.toolCalls != null && message.toolCalls!.isNotEmpty) {
          updateStep('正在查询数据...', 'processing');
          messages.add(ChatCompletionMessage.assistant(toolCalls: message.toolCalls));

          for (final toolCall in message.toolCalls!) {
            final toolName = toolCall.function.name;
            final toolArgs = toolCall.function.arguments;

            logger.i('[MCPAgentService] 调用工具: $toolName');
            onToolCall?.call(toolName, _parseArguments(toolArgs));

            final toolResult = await _toolExecutor.executeTool(toolName, toolArgs);
            collectedResults.addAll(_extractSearchResults(toolName, toolResult));

            messages.add(ChatCompletionMessage.tool(toolCallId: toolCall.id, content: toolResult));
          }

          updateStep('正在生成回答...', 'processing');
        } else {
          finalAnswer = message.content;
          _completeStep(currentStepName, onStep);
          break;
        }
      }

      if (finalAnswer == null) {
        logger.w('[MCPAgentService] 达到最大轮次，强制获取答案');
        updateStep('正在整理答案...', 'processing');
        final response = await _sendChatCompletion(client, messages);
        finalAnswer = response?.choices.first.message.content;
        _completeStep(currentStepName, onStep);
      }

      logger.i('[MCPAgentService] 处理完成，收集 ${collectedResults.length} 条结果');
      return MCPAgentResult(
        answer: finalAnswer ?? MCPPrompts.buildErrorResponse('无法生成回答'),
        searchResults: collectedResults,
      );
    } catch (e, stackTrace) {
      logger.e('[MCPAgentService] 处理失败', error: e, stackTrace: stackTrace);
      if (currentStepName != null) onStep(currentStepName!, 'error');
      onStep('处理失败', 'error');
      return MCPAgentResult(answer: MCPPrompts.buildErrorResponse('处理失败: $e'), searchResults: collectedResults);
    }
  }

  void _completeStep(String? currentStepName, Function(String, String) onStep) {
    if (currentStepName != null) onStep(currentStepName, 'completed');
    onStep('完成', 'completed');
  }

  // ========================================================================
  // 搜索结果提取
  // ========================================================================

  List<SearchResult> _extractSearchResults(String toolName, String toolResult) {
    try {
      final data = jsonDecode(toolResult) as Map<String, dynamic>;

      if (toolName.contains('diary') && data['diaries'] != null) {
        return _extractResults(data['diaries'] as List, _diaryToSearchResult);
      }
      if (toolName.contains('article') && data['articles'] != null) {
        return _extractResults(data['articles'] as List, _articleToSearchResult);
      }
      if (toolName.contains('book') && data['books'] != null) {
        return _extractResults(data['books'] as List, _bookToSearchResult);
      }
      return [];
    } catch (e) {
      logger.w('[MCPAgentService] 提取搜索结果失败: $e');
      return [];
    }
  }

  List<SearchResult> _extractResults(List items, SearchResult Function(Map<String, dynamic>) converter) {
    return items
        .map((item) {
          try {
            return converter(item as Map<String, dynamic>);
          } catch (e) {
            logger.w('[MCPAgentService] 转换结果失败: $e');
            return null;
          }
        })
        .whereType<SearchResult>()
        .toList();
  }

  SearchResult _diaryToSearchResult(Map<String, dynamic> d) {
    final tags = d['tags'];
    List<String>? tagsList;
    if (tags is List) {
      tagsList = tags.map((t) => t.toString()).toList();
    } else if (tags is String && tags.isNotEmpty) {
      tagsList = tags.split(',').map((t) => t.trim()).where((t) => t.isNotEmpty).toList();
    }

    return SearchResult.fromDiary(
      id: d['id'] as int,
      title: d['title'] as String? ?? _generateDiaryTitle(d),
      summary: _truncate(d['content'] as String?, 100),
      createdAt: _parseDateTime(d['createdAt']),
      tags: tagsList,
    );
  }

  SearchResult _articleToSearchResult(Map<String, dynamic> a) {
    return SearchResult.fromArticle(
      id: a['id'] as int,
      title: a['title'] as String? ?? '未知标题',
      summary: _truncate(a['summary'] as String?, 100),
      createdAt: _parseDateTime(a['createdAt']),
      isFavorite: a['isFavorite'] as bool?,
    );
  }

  SearchResult _bookToSearchResult(Map<String, dynamic> b) {
    return SearchResult.fromBook(
      id: b['id'] as int,
      title: b['title'] as String? ?? '未知书名',
      summary: b['author'] as String?,
      createdAt: _parseDateTime(b['createdAt']),
    );
  }

  String _generateDiaryTitle(Map<String, dynamic> diary) {
    final createdAt = _parseDateTime(diary['createdAt']);
    if (createdAt != null) {
      return '${createdAt.year}年${createdAt.month}月${createdAt.day}日的日记';
    }
    return '日记';
  }

  // ========================================================================
  // OpenAI 客户端
  // ========================================================================

  Future<OpenAIClient?> _createClient() async {
    try {
      final apiAddress = AIConfigService.i.getApiAddressForFunction(_functionType);
      final apiToken = AIConfigService.i.getApiTokenForFunction(_functionType);

      String apiKey;
      String baseUrl;

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

  Future<CreateChatCompletionResponse?> _sendChatCompletion(
    OpenAIClient client,
    List<ChatCompletionMessage> messages,
  ) async {
    try {
      final modelName = AIConfigService.i.getModelNameForFunction(_functionType);
      logger.i('[MCPAgentService] 发送请求 - 模型: $modelName, 消息数: ${messages.length}');

      return await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: ChatCompletionModel.modelId(modelName),
          messages: messages,
          tools: _buildTools(),
          toolChoice: const ChatCompletionToolChoiceOption.mode(ChatCompletionToolChoiceMode.auto),
          temperature: 0.7,
        ),
      );
    } catch (e, stackTrace) {
      logger.e('[MCPAgentService] 聊天请求失败', error: e, stackTrace: stackTrace);
      return null;
    }
  }

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
  // 工具方法
  // ========================================================================

  Map<String, dynamic> _parseArguments(String arguments) {
    try {
      return jsonDecode(arguments) as Map<String, dynamic>;
    } catch (_) {
      return {};
    }
  }

  String? _truncate(String? content, int maxLength) {
    if (content == null || content.isEmpty) return null;
    return content.length <= maxLength ? content : '${content.substring(0, maxLength)}...';
  }

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
}
