import 'dart:convert';
import 'package:openai_dart/openai_dart.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'mcp_tool_definition.dart';
import 'mcp_tool_executor.dart';

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
  /// 返回 AI 生成的最终答案
  Future<String> processQuery({
    required String query,
    required Function(String step, String status) onStep,
    Function(String toolName, Map<String, dynamic> args)? onToolCall,
  }) async {
    logger.i('[MCPAgentService] ========== 开始处理查询 ==========');
    logger.i('[MCPAgentService] 查询内容: $query');

    // 当前步骤名称（用于状态切换）
    String? currentStepName;

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
        return _buildErrorResponse('AI 服务未配置，请先在设置中配置 OpenAI API');
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
          return _buildErrorResponse('AI 请求失败，请稍后重试');
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

            // 执行工具
            final toolResult = await _toolExecutor.executeTool(toolName, toolArgs);

            logger.i('[MCPAgentService] 工具结果: ${_truncateLog(toolResult)}');

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
      return finalAnswer ?? _buildErrorResponse('无法生成回答');
    } catch (e, stackTrace) {
      logger.e('[MCPAgentService] 处理失败', error: e, stackTrace: stackTrace);
      // 标记当前步骤为错误
      if (currentStepName != null) {
        onStep(currentStepName!, 'error');
      }
      onStep('处理失败', 'error');
      return _buildErrorResponse('处理失败: $e');
    }
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
    return '''你是一个智能助手，帮助用户查询和管理他们的个人数据，包括：
- **日记**: 用户的个人日记记录
- **文章**: 用户收藏的网页文章
- **书籍**: 用户添加的书籍和读书笔记

## 工具使用指南

你可以使用以下工具来获取数据：

### 日记相关
- `get_latest_diary`: 获取最新的日记（回答"最近的日记"、"最新日记"等问题）
- `get_diary_by_date`: 获取指定日期的日记（回答"今天/昨天的日记"等问题）
- `search_diary_by_content`: 按关键词搜索日记内容
- `get_diary_by_tag`: 按标签获取日记
- `get_diary_count`: 获取日记总数

### 文章相关
- `get_latest_articles`: 获取最新收藏的文章
- `search_articles`: 按关键词搜索文章
- `get_favorite_articles`: 获取标星收藏的文章
- `get_article_count`: 获取文章总数

### 书籍相关
- `get_latest_books`: 获取最新添加的书籍
- `search_books`: 按书名或作者搜索书籍
- `get_book_viewpoints`: 获取书籍的读书笔记
- `get_book_count`: 获取书籍总数

### 综合
- `get_statistics`: 获取应用数据统计

## 重要提示

1. **理解用户意图**:
   - "最近的日记" = 调用 `get_latest_diary`，而不是搜索"最近"这个关键词
   - "今天写了什么" = 调用 `get_diary_by_date` 并传入 "today"
   - "有多少文章" = 调用 `get_article_count`

2. **回答格式**:
   - 使用 Markdown 格式
   - 重要信息用 **加粗**
   - 适当使用表情符号让回答更生动
   - 如果找到多条结果，用列表展示

3. **无结果处理**:
   - 如果没有找到数据，友好地告知用户
   - 可以建议用户尝试其他搜索条件

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
