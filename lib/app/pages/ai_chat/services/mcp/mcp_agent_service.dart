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

class MCPAgentResult {
  final String answer;
  final List<SearchResult> searchResults;
  const MCPAgentResult({required this.answer, required this.searchResults});
}

class MCPAgentService {
  static MCPAgentService? _instance;
  static MCPAgentService get i => _instance ??= MCPAgentService._();
  MCPAgentService._();

  static const int _functionType = 0;
  static const int _maxToolCallRounds = 5;
  final MCPToolExecutor _toolExecutor = MCPToolExecutor.i;

  Future<MCPAgentResult> processQuery({
    required String query,
    required Function(String step, String status) onStep,
    Function(String toolName, Map<String, dynamic> args)? onToolCall,
  }) async {
    String? currentStepName;
    final collectedResults = <SearchResult>[];

    void updateStep(String stepName, String status) {
      if (currentStepName != null && currentStepName != stepName) onStep(currentStepName!, 'completed');
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
      for (var round = 0; round < _maxToolCallRounds; round++) {
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
            onToolCall?.call(toolCall.function.name, _parseArguments(toolCall.function.arguments));
            final toolResult = await _toolExecutor.executeTool(toolCall.function.name, toolCall.function.arguments);
            collectedResults.addAll(_extractSearchResults(toolCall.function.name, toolResult));
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
        updateStep('正在整理答案...', 'processing');
        final response = await _sendChatCompletion(client, messages);
        finalAnswer = response?.choices.first.message.content;
        _completeStep(currentStepName, onStep);
      }

      final filteredResults = _filterRelevantResults(collectedResults, finalAnswer ?? '');
      final cleanAnswer = _removeRefsTag(finalAnswer ?? MCPPrompts.buildErrorResponse('无法生成回答'));
      return MCPAgentResult(answer: cleanAnswer, searchResults: filteredResults);
    } catch (e, stackTrace) {
      logger.e('[MCPAgentService] 处理失败', error: e, stackTrace: stackTrace);
      if (currentStepName != null) onStep(currentStepName!, 'error');
      onStep('处理失败', 'error');
      return MCPAgentResult(answer: MCPPrompts.buildErrorResponse('处理失败: $e'), searchResults: collectedResults);
    }
  }

  List<SearchResult> _filterRelevantResults(List<SearchResult> results, String answer) {
    if (results.isEmpty || answer.isEmpty) return results;

    final refsMatch = RegExp(r'<!--\s*refs:\s*([^>]+)\s*-->').firstMatch(answer);
    if (refsMatch == null) return _filterByTitleMatch(results, answer);

    final refsContent = refsMatch.group(1)?.trim() ?? '';
    if (refsContent.toLowerCase() == 'none') return [];
    if (refsContent.isEmpty) return _filterByTitleMatch(results, answer);

    final referencedIds = <String, Set<int>>{'article': <int>{}, 'diary': <int>{}, 'book': <int>{}};
    for (final ref in refsContent.split(',').map((s) => s.trim())) {
      final match = RegExp(r'(article|diary|book)_(\d+)').firstMatch(ref);
      if (match != null) {
        final id = int.tryParse(match.group(2)!);
        if (id != null) referencedIds[match.group(1)!]!.add(id);
      }
    }

    if (referencedIds.values.fold<int>(0, (sum, set) => sum + set.length) == 0) {
      return _filterByTitleMatch(results, answer);
    }

    final filtered = results
        .where(
          (r) => switch (r.type) {
            SearchResultType.article => referencedIds['article']!.contains(r.id),
            SearchResultType.diary => referencedIds['diary']!.contains(r.id),
            SearchResultType.book => referencedIds['book']!.contains(r.id),
          },
        )
        .toList();

    return filtered.isEmpty && results.isNotEmpty ? _filterByTitleMatch(results, answer) : filtered;
  }

  List<SearchResult> _filterByTitleMatch(List<SearchResult> results, String answer) {
    final answerLower = answer.toLowerCase();
    return results.where((result) {
      final keywords = result.title
          .replaceAll(RegExp(r'[^\w\u4e00-\u9fa5]'), ' ')
          .split(RegExp(r'\s+'))
          .where((w) => w.length >= 2);
      for (final keyword in keywords) {
        if (answerLower.contains(keyword.toLowerCase())) return true;
      }
      if (result.type == SearchResultType.diary && result.createdAt != null) {
        final date = result.createdAt!;
        final patterns = [
          '${date.month}月${date.day}日',
          '${date.year}年${date.month}月${date.day}日',
          '${date.month}/${date.day}',
        ];
        for (final pattern in patterns) {
          if (answer.contains(pattern)) return true;
        }
      }
      return false;
    }).toList();
  }

  String _removeRefsTag(String answer) => answer.replaceAll(RegExp(r'\n*<!--\s*refs:[^>]*-->\s*$'), '').trim();

  void _completeStep(String? currentStepName, Function(String, String) onStep) {
    if (currentStepName != null) onStep(currentStepName, 'completed');
    onStep('完成', 'completed');
  }

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
    } catch (_) {
      return [];
    }
  }

  List<SearchResult> _extractResults(List items, SearchResult Function(Map<String, dynamic>) converter) {
    return items
        .map((item) {
          try {
            return converter(item as Map<String, dynamic>);
          } catch (_) {
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

  SearchResult _articleToSearchResult(Map<String, dynamic> a) => SearchResult.fromArticle(
    id: a['id'] as int,
    title: a['title'] as String? ?? '未知标题',
    summary: _truncate(a['summary'] as String?, 100),
    createdAt: _parseDateTime(a['createdAt']),
    isFavorite: a['isFavorite'] as bool?,
  );

  SearchResult _bookToSearchResult(Map<String, dynamic> b) => SearchResult.fromBook(
    id: b['id'] as int,
    title: b['title'] as String? ?? '未知书名',
    summary: b['author'] as String?,
    createdAt: _parseDateTime(b['createdAt']),
  );

  String _generateDiaryTitle(Map<String, dynamic> diary) {
    final createdAt = _parseDateTime(diary['createdAt']);
    return createdAt != null ? '${createdAt.year}年${createdAt.month}月${createdAt.day}日的日记' : '日记';
  }

  Future<OpenAIClient?> _createClient() async {
    try {
      var apiKey = AIConfigService.i.getApiTokenForFunction(_functionType);
      var baseUrl = AIConfigService.i.getApiAddressForFunction(_functionType);

      if (apiKey.isEmpty || baseUrl.isEmpty) {
        apiKey = SettingRepository.i.getSetting(SettingService.openAITokenKey);
        baseUrl = SettingRepository.i.getSetting(SettingService.openAIAddressKey);
      }
      if (apiKey.isEmpty || baseUrl.isEmpty) return null;
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
      return await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: ChatCompletionModel.modelId(AIConfigService.i.getModelNameForFunction(_functionType)),
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

  List<ChatCompletionTool> _buildTools() => MCPToolRegistry.tools
      .map(
        (tool) => ChatCompletionTool(
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
        ),
      )
      .toList();

  Map<String, dynamic> _parseArguments(String arguments) {
    try {
      return jsonDecode(arguments) as Map<String, dynamic>;
    } catch (_) {
      return {};
    }
  }

  String? _truncate(String? content, int maxLength) => content == null || content.isEmpty
      ? null
      : (content.length <= maxLength ? content : '${content.substring(0, maxLength)}...');

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
