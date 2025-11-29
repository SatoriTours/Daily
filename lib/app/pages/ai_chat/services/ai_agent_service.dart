import 'dart:async';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import '../models/tool_call.dart';
import '../models/search_result.dart';
import 'search_executor.dart';
import 'content_extractor.dart';
import 'ai_prompts.dart';

/// AI Agent 服务
///
/// 负责智能搜索和内容分析的核心服务
///
/// **核心流程**:
/// 1. 分析意图 - 理解用户想要查找什么类型的内容
/// 2. 生成计划 - 制定搜索策略和关键词
/// 3. 执行搜索 - 在文章、日记、书籍中搜索
/// 4. 生成答案 - 用AI总结搜索结果
class AIAgentService {
  // ========================================================================
  // 单例模式
  // ========================================================================

  static AIAgentService? _instance;
  static AIAgentService get i => _instance ??= AIAgentService._();
  AIAgentService._();

  // ========================================================================
  // 依赖服务
  // ========================================================================

  final SearchExecutor _searchExecutor = SearchExecutor.i;
  final ContentExtractor _contentExtractor = ContentExtractor.i;

  // ========================================================================
  // 主流程方法
  // ========================================================================

  /// 处理用户查询
  ///
  /// 这是AI Agent的主入口方法，完整处理用户的查询请求
  ///
  /// [query] 用户查询内容
  /// [onStep] 步骤更新回调，参数为 (步骤描述, 状态)
  /// [onToolCall] 工具调用回调
  /// [onResult] 结果更新回调
  /// [onSearchResults] 搜索结果回调
  ///
  /// 返回AI生成的最终答案
  Future<String> processQuery({
    required String query,
    required Function(String step, String status) onStep,
    required Function(ToolCall toolCall) onToolCall,
    required Function(String result) onResult,
    required Function(List<SearchResult> results) onSearchResults,
  }) async {
    logger.i('[AIAgentService] ========== 开始处理查询 ==========');
    logger.i('[AIAgentService] 查询内容: $query');

    // 用于追踪当前正在执行的步骤
    final activeSteps = <String>[];

    try {
      // 步骤1: 分析用户意图
      final intent = await _executeStep(
        onStep: onStep,
        stepName: 'ai_chat.step_analyzing_query'.t,
        action: () => _analyzeIntent(query),
        logPrefix: '意图: ',
        activeSteps: activeSteps,
      );

      // 步骤2: 生成搜索计划
      final toolPlan = await _executeStep(
        onStep: onStep,
        stepName: 'ai_chat.step_planning_tools'.t,
        action: () => _generateToolPlan(query, intent),
        logPrefix: '计划: ',
        activeSteps: activeSteps,
      );

      // 步骤3: 执行所有搜索
      final searchResults = await _executeStep(
        onStep: onStep,
        stepName: 'ai_chat.step_searching'.t,
        action: () => _searchExecutor.executeSearchPlan(toolPlan),
        logPrefix: '搜索: ',
        activeSteps: activeSteps,
      );

      // 步骤4: 生成AI答案
      final answer = await _executeStep(
        onStep: onStep,
        stepName: 'ai_chat.step_summarizing'.t,
        action: () => _generateAnswer(query, searchResults),
        logPrefix: '完成: ',
        activeSteps: activeSteps,
      );

      // 通知结果
      onResult(answer);
      onSearchResults(searchResults);

      logger.i('[AIAgentService] ========== 处理完成 ==========\n');
      return answer;
    } catch (e, stackTrace) {
      logger.e('[AIAgentService] 处理失败', error: e, stackTrace: stackTrace);

      // 将所有活跃步骤标记为错误
      _markActiveStepsAsError(onStep, activeSteps);

      onStep('ai_chat.step_error_occurred'.t, 'error');
      rethrow;
    }
  }

  // ========================================================================
  // 步骤执行器
  // ========================================================================

  /// 执行步骤（通用步骤执行器）
  ///
  /// [onStep] 步骤回调
  /// [stepName] 步骤名称
  /// [action] 要执行的操作
  /// [logPrefix] 日志前缀
  /// [activeSteps] 活跃步骤追踪列表
  ///
  /// 返回操作的结果
  Future<T> _executeStep<T>({
    required Function(String, String) onStep,
    required String stepName,
    required Future<T> Function() action,
    required String logPrefix,
    required List<String> activeSteps,
  }) async {
    // 开始步骤，添加到活跃列表
    onStep(stepName, 'processing');
    activeSteps.add(stepName);

    try {
      // 执行操作
      final result = await action();

      // 记录日志
      final resultLog = _formatResultLog(result);
      logger.i('[AIAgentService] $logPrefix$resultLog');

      // 完成步骤，从活跃列表移除
      onStep(stepName, 'completed');
      activeSteps.remove(stepName);

      return result;
    } catch (e) {
      // 发生错误时，标记当前步骤为错误并从活跃列表移除
      onStep(stepName, 'error');
      activeSteps.remove(stepName);
      rethrow;
    }
  }

  /// 格式化结果日志
  String _formatResultLog<T>(T result) {
    if (result is QueryIntent) {
      return result.description;
    } else if (result is List<ToolCall>) {
      return '${result.length}个搜索任务';
    } else if (result is List<SearchResult>) {
      return '找到${result.length}条结果';
    } else {
      return '已生成答案';
    }
  }

  /// 将所有活跃步骤标记为错误
  void _markActiveStepsAsError(Function(String, String) onStep, List<String> activeSteps) {
    for (final step in activeSteps) {
      onStep(step, 'error');
    }
    activeSteps.clear();
  }

  // ========================================================================
  // 步骤实现 - 意图分析
  // ========================================================================

  /// 分析用户意图
  Future<QueryIntent> _analyzeIntent(String query) async {
    logger.d('[AIAgentService] 开始分析意图');

    await Future.delayed(const Duration(milliseconds: 800));

    final prompt = _buildPrompt(AIPrompts.intentAnalysis, {'query': query});
    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);

    final intent = _parseIntentResult(aiResult);
    logger.d('[AIAgentService] 意图分析完成: ${intent.description}');
    return intent;
  }

  /// 解析AI返回的意图结果
  QueryIntent _parseIntentResult(String aiResult) {
    final intentStr = aiResult.trim().toLowerCase();

    if (intentStr.contains('article')) {
      return QueryIntent.articles;
    } else if (intentStr.contains('diary')) {
      return QueryIntent.diary;
    } else if (intentStr.contains('book')) {
      return QueryIntent.books;
    } else if (intentStr.contains('summary')) {
      return QueryIntent.summary;
    }

    return QueryIntent.general;
  }

  // ========================================================================
  // 步骤实现 - 计划生成
  // ========================================================================

  /// 生成搜索计划
  Future<List<ToolCall>> _generateToolPlan(String query, QueryIntent intent) async {
    logger.d('[AIAgentService] 开始生成搜索计划');

    await Future.delayed(const Duration(milliseconds: 1200));

    final keywords = await _extractSearchKeywords(query);
    final dateRange = await _extractDateRange(query);
    final filters = await _extractFilters(query);

    final effectiveQuery = keywords.isNotEmpty ? keywords : query;
    logger.d('[AIAgentService] 搜索关键词: $effectiveQuery');

    final toolCalls = _buildToolCallsByIntent(intent, effectiveQuery, keywords, dateRange, filters);
    logger.d('[AIAgentService] 计划生成完成: ${toolCalls.length}个任务');
    return toolCalls;
  }

  /// 根据意图构建工具调用列表
  List<ToolCall> _buildToolCallsByIntent(
    QueryIntent intent,
    String effectiveQuery,
    String keywords,
    DateTimeRange? dateRange,
    Map<String, dynamic> filters,
  ) {
    final toolCalls = <ToolCall>[];

    switch (intent) {
      case QueryIntent.articles:
        toolCalls.add(ToolCall.searchArticles(query: effectiveQuery, filters: filters));
        break;

      case QueryIntent.diary:
        toolCalls.add(ToolCall.searchDiary(query: effectiveQuery, dateRange: dateRange));
        break;

      case QueryIntent.books:
        toolCalls.add(ToolCall.searchBooks(query: effectiveQuery));
        break;

      case QueryIntent.summary:
        if (keywords.isNotEmpty) {
          toolCalls.add(ToolCall.searchArticles(query: keywords, filters: {}));
          toolCalls.add(ToolCall.searchDiary(query: keywords, dateRange: dateRange));
        } else {
          toolCalls.add(ToolCall.searchAll(query: effectiveQuery));
        }
        break;

      case QueryIntent.general:
        toolCalls.add(ToolCall.searchAll(query: effectiveQuery));
        break;
    }

    return toolCalls;
  }

  // ========================================================================
  // 步骤实现 - 答案生成
  // ========================================================================

  /// 生成最终答案
  Future<String> _generateAnswer(String query, List<SearchResult> results) async {
    logger.d('[AIAgentService] 开始生成答案');

    await Future.delayed(const Duration(milliseconds: 800));

    if (results.isEmpty) {
      return await _handleEmptyResults(query);
    }

    final fullContents = await _contentExtractor.fetchFullContents(results);
    if (fullContents.isEmpty) {
      logger.w('[AIAgentService] 内容加载失败');
      return '😔 **未找到相关内容**\n\n很抱歉，搜索到的内容无法加载。';
    }

    final stats = _contentExtractor.calculateResultStats(results);
    logger.d('[AIAgentService] 结果统计: ${stats['articles']}篇文章, ${stats['diaries']}条日记, ${stats['books']}本书');

    return await _generateAIResponse(query, fullContents);
  }

  /// 处理空结果情况
  Future<String> _handleEmptyResults(String query) async {
    logger.i('[AIAgentService] 处理空结果');

    final prompt = _buildPrompt(AIPrompts.emptyResultAnalysis, {'query': query});
    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final isExternalQuestion = aiResult.trim().toLowerCase().contains('external');

    if (isExternalQuestion) {
      logger.d('[AIAgentService] 识别为外部问题');
      return _buildPrompt(AIMessages.externalQuestionResponse, {'query': query});
    }

    logger.d('[AIAgentService] 识别为搜索无结果');
    return _buildPrompt(AIMessages.noResultsResponse, {'query': query});
  }

  /// 生成AI响应
  Future<String> _generateAIResponse(String query, Map<String, String> fullContents) async {
    if (fullContents.isEmpty) {
      return '抱歉，未找到相关内容。';
    }

    logger.d('[AIAgentService] 开始生成AI响应');

    final contentToAnalyze = _contentExtractor.mergeContents(fullContents);
    final prompt = _buildPrompt(AIPrompts.answerGeneration, {'query': query, 'content': contentToAnalyze});
    final aiResponse = await AiService.i.getCompletion(prompt, functionType: 0);

    logger.d('[AIAgentService] AI响应生成完成');
    return aiResponse.trim();
  }

  // ========================================================================
  // 参数提取方法
  // ========================================================================

  /// 提取搜索关键词
  Future<String> _extractSearchKeywords(String query) async {
    final prompt = _buildPrompt(AIPrompts.keywordExtraction, {'query': query});
    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final keywords = aiResult.trim();

    logger.d('[AIAgentService] 提取关键词: $keywords');
    return keywords.isNotEmpty ? keywords : query;
  }

  /// 提取过滤条件
  Future<Map<String, dynamic>> _extractFilters(String query) async {
    final filters = <String, dynamic>{};

    try {
      final prompt = _buildPrompt(AIPrompts.filterExtraction, {'query': query});
      final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);

      if (aiResult.contains('"favorite": true') || aiResult.contains("'favorite': true")) {
        filters['favorite'] = true;
        logger.d('[AIAgentService] 添加过滤条件: favorite=true');
      }
      if (aiResult.contains('"hasTags": true') || aiResult.contains("'hasTags': true")) {
        filters['hasTags'] = true;
        logger.d('[AIAgentService] 添加过滤条件: hasTags=true');
      }
    } catch (e) {
      logger.e('[AIAgentService] 提取过滤条件失败', error: e);
    }

    return filters;
  }

  /// 提取日期范围
  Future<DateTimeRange?> _extractDateRange(String query) async {
    try {
      final prompt = _buildPrompt(AIPrompts.dateExtraction, {'query': query});
      final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
      final timeType = aiResult.trim().toLowerCase();

      final dateRange = _parseDateRange(timeType);
      if (dateRange != null) {
        logger.d('[AIAgentService] 提取日期范围: $timeType');
      }
      return dateRange;
    } catch (e) {
      logger.e('[AIAgentService] 日期提取失败', error: e);
      return null;
    }
  }

  /// 解析时间类型为日期范围
  DateTimeRange? _parseDateRange(String timeType) {
    final now = DateTime.now();

    switch (timeType) {
      case 'today':
        return DateTimeRange(
          start: DateTime(now.year, now.month, now.day),
          end: DateTime(now.year, now.month, now.day, 23, 59, 59),
        );
      case 'yesterday':
        final yesterday = now.subtract(const Duration(days: 1));
        return DateTimeRange(
          start: DateTime(yesterday.year, yesterday.month, yesterday.day),
          end: DateTime(yesterday.year, yesterday.month, yesterday.day, 23, 59, 59),
        );
      case 'recent':
        return DateTimeRange(start: now.subtract(const Duration(days: 7)), end: now);
      case 'this_week':
        final weekday = now.weekday;
        final startOfWeek = now.subtract(Duration(days: weekday - 1));
        return DateTimeRange(start: DateTime(startOfWeek.year, startOfWeek.month, startOfWeek.day), end: now);
      case 'last_week':
        final weekday = now.weekday;
        final lastWeekStart = now.subtract(Duration(days: weekday + 6));
        final lastWeekEnd = now.subtract(Duration(days: weekday));
        return DateTimeRange(
          start: DateTime(lastWeekStart.year, lastWeekStart.month, lastWeekStart.day),
          end: DateTime(lastWeekEnd.year, lastWeekEnd.month, lastWeekEnd.day, 23, 59, 59),
        );
      case 'this_month':
        return DateTimeRange(start: DateTime(now.year, now.month, 1), end: now);
      default:
        return null;
    }
  }

  // ========================================================================
  // 工具辅助方法
  // ========================================================================

  /// 构建提示词
  String _buildPrompt(String template, Map<String, String> params) {
    var result = template;
    params.forEach((key, value) {
      result = result.replaceAll('{$key}', value);
    });
    return result;
  }
}

/// 查询意图枚举
enum QueryIntent {
  articles('查找文章'),
  diary('查找日记'),
  books('查找书籍'),
  summary('综合总结'),
  general('通用搜索');

  final String description;
  const QueryIntent(this.description);
}
