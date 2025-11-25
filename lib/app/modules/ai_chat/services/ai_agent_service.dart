import 'dart:async';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/diary_repository.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import '../models/tool_call.dart';
import '../models/search_result.dart';

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
  // 常量配置
  // ========================================================================

  /// 搜索结果最大数量
  static const int _maxSearchResults = 10;

  /// 内容分析最大长度
  static const int _maxContentLength = 6000;

  /// 内容摘要预览长度
  static const int _summaryPreviewLength = 150;

  // ========================================================================
  // 主流程方法
  // ========================================================================

  /// 处理用户查询
  ///
  /// 这是AI Agent的主入口方法，完整处理用户的查询请求
  ///
  /// [query] 用户查询内容
  /// [onStep] 步骤更新回调
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

    try {
      // 步骤1: 分析用户意图
      final intent = await _executeStep(
        onStep,
        'ai_chat.step_analyzing_query'.t,
        () => _analyzeIntent(query),
        '意图: ',
      );

      // 步骤2: 生成搜索计划
      final toolPlan = await _executeStep(
        onStep,
        'ai_chat.step_planning_tools'.t,
        () => _generateToolPlan(query, intent),
        '计划: ',
      );

      // 步骤3: 执行所有搜索
      final searchResults = await _executeStep(
        onStep,
        'ai_chat.step_searching'.t,
        () => _executeSearchPlan(toolPlan),
        '搜索: ',
      );

      // 步骤4: 生成AI答案
      final answer = await _executeStep(
        onStep,
        'ai_chat.step_summarizing'.t,
        () => _generateAnswer(query, searchResults),
        '完成: ',
      );

      // 通知结果
      onResult(answer);
      onSearchResults(searchResults);

      logger.i('[AIAgentService] ========== 处理完成 ==========\n');
      return answer;

    } catch (e, stackTrace) {
      logger.e('[AIAgentService] 处理失败', error: e, stackTrace: stackTrace);
      onStep('ai_chat.step_error_occurred'.t, 'error');
      rethrow;
    }
  }

  /// 执行步骤（通用步骤执行器）
  ///
  /// [onStep] 步骤回调
  /// [stepName] 步骤名称
  /// [action] 要执行的操作
  /// [logPrefix] 日志前缀
  ///
  /// 返回操作的结果
  Future<T> _executeStep<T>(
    Function(String, String) onStep,
    String stepName,
    Future<T> Function() action,
    String logPrefix,
  ) async {
    // 开始步骤
    onStep(stepName, 'processing');

    // 执行操作
    final result = await action();

    // 记录日志
    String resultLog;
    if (result is QueryIntent) {
      resultLog = result.description;
    } else if (result is List<ToolCall>) {
      resultLog = '${result.length}个搜索任务';
    } else if (result is List<SearchResult>) {
      resultLog = '找到${result.length}条结果';
    } else {
      resultLog = '已生成答案';
    }
    logger.i('[AIAgentService] $logPrefix$resultLog');

    // 完成步骤
    onStep(stepName, 'completed');

    return result;
  }

  /// 执行搜索计划
  ///
  /// [toolPlan] 工具调用计划列表
  /// 返回所有搜索结果
  Future<List<SearchResult>> _executeSearchPlan(List<ToolCall> toolPlan) async {
    final allResults = <SearchResult>[];

    for (var i = 0; i < toolPlan.length; i++) {
      final toolCall = toolPlan[i];
      logger.d('[AIAgentService] 执行任务 ${i + 1}/${toolPlan.length}: ${toolCall.name}');

      final results = await _executeToolCall(toolCall);

      if (results.isNotEmpty) {
        logger.d('[AIAgentService] 任务${i + 1}完成: ${results.length}条结果');
      }
      allResults.addAll(results);
    }

    return allResults;
  }

  // ========================================================================
  // 步骤实现 - 意图分析
  // ========================================================================

  /// 分析用户意图
  ///
  /// 使用AI分析用户查询，判断用户想要查找什么类型的内容
  ///
  /// [query] 用户查询
  /// 返回查询意图类型
  Future<QueryIntent> _analyzeIntent(String query) async {
    logger.d('[AIAgentService] 开始分析意图');

    // 模拟思考时间
    await Future.delayed(const Duration(milliseconds: 800));

    // 调用AI分析
    final prompt = _buildPrompt(_Prompts.intentAnalysis, {'query': query});
    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);

    // 解析意图
    final intent = _parseIntentResult(aiResult);

    logger.d('[AIAgentService] 意图分析完成: ${intent.description}');
    return intent;
  }

  /// 解析AI返回的意图结果
  ///
  /// [aiResult] AI返回的文本
  /// 返回对应的QueryIntent枚举
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
  ///
  /// 根据用户意图和查询内容，生成详细的搜索计划
  ///
  /// [query] 用户查询
  /// [intent] 查询意图
  /// 返回工具调用计划列表
  Future<List<ToolCall>> _generateToolPlan(String query, QueryIntent intent) async {
    logger.d('[AIAgentService] 开始生成搜索计划');

    // 模拟思考时间
    await Future.delayed(const Duration(milliseconds: 1200));

    // 提取搜索参数
    final keywords = await _extractSearchKeywords(query);
    final dateRange = await _extractDateRange(query);
    final filters = await _extractFilters(query);

    // 选择有效的查询词
    final effectiveQuery = keywords.isNotEmpty ? keywords : query;

    logger.d('[AIAgentService] 搜索关键词: $effectiveQuery');

    // 根据意图生成计划
    final toolCalls = _buildToolCallsByIntent(
      intent,
      effectiveQuery,
      keywords,
      dateRange,
      filters,
    );

    logger.d('[AIAgentService] 计划生成完成: ${toolCalls.length}个任务');
    return toolCalls;
  }

  /// 根据意图构建工具调用列表
  ///
  /// [intent] 查询意图
  /// [effectiveQuery] 有效查询词
  /// [keywords] 提取的关键词
  /// [dateRange] 日期范围
  /// [filters] 过滤条件
  ///
  /// 返回工具调用列表
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
        toolCalls.add(ToolCall.searchArticles(
          query: effectiveQuery,
          filters: filters,
        ));
        break;

      case QueryIntent.diary:
        toolCalls.add(ToolCall.searchDiary(
          query: effectiveQuery,
          dateRange: dateRange,
        ));
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
  // 参数提取方法
  // ========================================================================

  /// 执行工具调用
  Future<List<SearchResult>> _executeToolCall(ToolCall toolCall) async {
    await Future.delayed(const Duration(milliseconds: 500));

    // 调用真实的搜索服务
    switch (toolCall.type) {
      case ToolType.searchArticles:
        return _searchArticles(toolCall.parameters);
      case ToolType.searchDiary:
        return _searchDiary(toolCall.parameters);
      case ToolType.searchBooks:
        return _searchBooks(toolCall.parameters);
      case ToolType.searchAll:
        return _searchAll(toolCall.parameters);
    }
  }

  // ========================================================================
  // 步骤实现 - 答案生成
  // ========================================================================

  /// 生成最终答案
  ///
  /// 根据搜索结果生成AI答案
  ///
  /// [query] 用户查询
  /// [results] 搜索结果列表
  /// 返回AI生成的答案
  Future<String> _generateAnswer(String query, List<SearchResult> results) async {
    logger.d('[AIAgentService] 开始生成答案');

    await Future.delayed(const Duration(milliseconds: 800));

    // 处理空结果
    if (results.isEmpty) {
      return await _handleEmptyResults(query);
    }

    // 获取完整内容
    final fullContents = await _fetchFullContents(results);
    if (fullContents.isEmpty) {
      logger.w('[AIAgentService] 内容加载失败');
      return '😔 **未找到相关内容**\n\n很抱歉，搜索到的内容无法加载。';
    }

    // 统计结果类型
    final stats = _calculateResultStats(results);
    logger.d('[AIAgentService] 结果统计: ${stats['articles']}篇文章, ${stats['diaries']}条日记, ${stats['books']}本书');

    // 生成AI答案
    return await _generateAIResponse(query, fullContents, stats);
  }

  /// 处理空结果情况
  ///
  /// 当没有搜索到结果时，分析是否为外部问题并返回适当的回复
  ///
  /// [query] 用户查询
  /// 返回空结果的回复消息
  Future<String> _handleEmptyResults(String query) async {
    logger.i('[AIAgentService] 处理空结果');

    final prompt = _buildPrompt(_Prompts.emptyResultAnalysis, {'query': query});
    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final isExternalQuestion = aiResult.trim().toLowerCase().contains('external');

    if (isExternalQuestion) {
      logger.d('[AIAgentService] 识别为外部问题');
      return _buildMessage(_Messages.externalQuestionResponse, {'query': query});
    }

    logger.d('[AIAgentService] 识别为搜索无结果');
    return _buildMessage(_Messages.noResultsResponse, {'query': query});
  }

  /// 统计搜索结果
  ///
  /// [results] 搜索结果列表
  /// 返回包含各类型数量的统计映射
  Map<String, int> _calculateResultStats(List<SearchResult> results) {
    return {
      'articles': results.where((r) => r.type == SearchResultType.article).length,
      'diaries': results.where((r) => r.type == SearchResultType.diary).length,
      'books': results.where((r) => r.type == SearchResultType.book).length,
    };
  }

  // ========================================================================
  // 内容提取方法
  // ========================================================================

  /// 提取搜索关键词
  ///
  /// 使用AI从查询中提取核心关键词，包括同义词扩展
  ///
  /// [query] 用户查询
  /// 返回提取的关键词字符串
  Future<String> _extractSearchKeywords(String query) async {
    final prompt = _buildPrompt(_Prompts.keywordExtraction, {'query': query});
    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final keywords = aiResult.trim();

    logger.d('[AIAgentService] 提取关键词: $keywords');
    return keywords.isNotEmpty ? keywords : query;
  }

  /// 提取过滤条件
  ///
  /// 从查询中提取过滤条件（如收藏、标签等）
  ///
  /// [query] 用户查询
  /// 返回过滤条件映射
  Future<Map<String, dynamic>> _extractFilters(String query) async {
    final filters = <String, dynamic>{};

    try {
      final prompt = _buildPrompt(_Prompts.filterExtraction, {'query': query});
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
  ///
  /// 从查询中提取时间范围（如今天、本周等）
  ///
  /// [query] 用户查询
  /// 返回日期范围，如果没有则返回null
  Future<DateTimeRange?> _extractDateRange(String query) async {
    try {
      final prompt = _buildPrompt(_Prompts.dateExtraction, {'query': query});
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

  /// 获取搜索结果的完整内容
  ///
  /// [results] 搜索结果列表
  /// 返回内容映射 (键: 类型:ID, 值: 完整内容)
  Future<Map<String, String>> _fetchFullContents(List<SearchResult> results) async {
    logger.d('[AIAgentService] 开始提取内容，共${results.length}条结果');

    final fullContents = <String, String>{};

    for (final result in results) {
      try {
        final content = _extractContentByType(result);
        if (content != null && content.isNotEmpty) {
          fullContents['${result.type}:${result.id}'] = content;
        }
      } catch (e) {
        logger.e('[AIAgentService] 内容提取失败: ${result.type}:${result.id}', error: e);
      }
    }

    logger.d('[AIAgentService] 内容提取完成，共${fullContents.length}条有效内容');
    return fullContents;
  }

  /// 根据类型提取内容
  String? _extractContentByType(SearchResult result) {
    switch (result.type) {
      case SearchResultType.article:
        return _extractArticleContent(result.id);
      case SearchResultType.diary:
        return _extractDiaryContent(result.id);
      case SearchResultType.book:
        return _extractBookContent(result.id);
    }
  }

  /// 提取文章内容
  String? _extractArticleContent(int id) {
    final article = ArticleRepository.i.find(id);
    if (article == null) return null;

    final content = article.aiContent?.isNotEmpty == true
        ? article.aiContent!
        : (article.content?.isNotEmpty == true ? article.content! : '');

    return content.isNotEmpty ? '【文章】${article.title ?? "无标题"}\n\n$content' : null;
  }

  /// 提取日记内容
  String? _extractDiaryContent(int id) {
    final diary = DiaryRepository.i.find(id);
    if (diary == null || diary.content.isEmpty) return null;

    final dateStr =
        '${diary.createdAt.year}-'
        '${diary.createdAt.month.toString().padLeft(2, '0')}-'
        '${diary.createdAt.day.toString().padLeft(2, '0')}';

    return '【日记】$dateStr\n\n${diary.content}';
  }

  /// 提取书籍内容
  String? _extractBookContent(int id) {
    final book = BookRepository.i.find(id);
    if (book == null) return null;

    final intro = book.introduction.isNotEmpty ? book.introduction : '暂无简介';
    return '【书籍】${book.title}\n作者: ${book.author}\n\n$intro';
  }

  /// 生成AI响应
  ///
  /// 使用AI分析内容并生成最终答案
  ///
  /// [query] 用户查询
  /// [fullContents] 完整内容映射
  /// [stats] 结果统计
  /// 返回AI生成的答案
  Future<String> _generateAIResponse(
    String query,
    Map<String, String> fullContents,
    Map<String, int> stats,
  ) async {
    if (fullContents.isEmpty) {
      return '抱歉，未找到相关内容。';
    }

    logger.d('[AIAgentService] 开始生成AI响应');

    // 合并并限制内容长度
    final allContent = fullContents.values.join('\n\n---\n\n');
    final contentToAnalyze = _limitContentLength(allContent, _maxContentLength);

    // 构建提示词并调用AI
    final prompt = _buildPrompt(
      _Prompts.answerGeneration,
      {'query': query, 'content': contentToAnalyze},
    );

    final aiResponse = await AiService.i.getCompletion(prompt, functionType: 0);

    logger.d('[AIAgentService] AI响应生成完成');
    return aiResponse.trim();
  }

  // ========================================================================
  // 工具辅助方法
  // ========================================================================

  /// 构建提示词
  ///
  /// 使用参数映射替换提示词模板中的占位符
  ///
  /// [template] 提示词模板
  /// [params] 参数映射
  /// 返回构建好的提示词
  String _buildPrompt(String template, Map<String, String> params) {
    var result = template;
    params.forEach((key, value) {
      result = result.replaceAll('{$key}', value);
    });
    return result;
  }

  /// 构建消息
  ///
  /// 使用参数映射替换消息模板中的占位符
  ///
  /// [template] 消息模板
  /// [params] 参数映射
  /// 返回构建好的消息
  String _buildMessage(String template, Map<String, String> params) {
    return _buildPrompt(template, params);
  }

  /// 限制内容长度
  ///
  /// [content] 原始内容
  /// [maxLength] 最大长度
  /// 返回限制长度后的内容
  String _limitContentLength(String content, int maxLength) {
    if (content.length <= maxLength) {
      return content;
    }
    logger.d('[AIAgentService] 内容过长，截断至$maxLength字符');
    return content.substring(0, maxLength);
  }

  // ========================================================================
  // 搜索实现方法
  // ========================================================================

  /// 搜索文章
  ///
  /// [params] 搜索参数 {
  ///   'query': 关键词,
  ///   'filters': {过滤条件}
  /// }
  /// 返回文章搜索结果列表
  List<SearchResult> _searchArticles(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    if (keyword == null || keyword.isEmpty) {
      logger.w('[AIAgentService] 文章搜索: 关键词为空');
      return [];
    }

    logger.d('[AIAgentService] 搜索文章: $keyword');

    final filters = params['filters'] as Map<String, dynamic>?;
    final keywords = _splitKeywords(keyword);
    final articleMap = <int, dynamic>{};

    // 使用每个关键词搜索，去重
    for (final kw in keywords) {
      final articles = ArticleRepository.i.findArticles(
        keyword: kw,
        isFavorite: filters?['favorite'] as bool?,
        limit: 20,
      );
      for (final article in articles) {
        articleMap[article.id] = article;
      }
    }

    // 转换为搜索结果
    final results = articleMap.values.take(_maxSearchResults).map((article) {
      final summary = _extractArticleSummary(article);
      return SearchResult.fromArticle(
        id: article.id,
        title: article.title ?? '无标题',
        summary: summary,
        createdAt: article.createdAt,
        isFavorite: article.isFavorite,
      );
    }).toList();

    logger.d('[AIAgentService] 文章搜索完成: ${results.length}条');
    return results;
  }

  /// 搜索日记
  ///
  /// [params] 搜索参数 {'query': 关键词}
  /// 返回日记搜索结果列表
  List<SearchResult> _searchDiary(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    if (keyword == null || keyword.isEmpty) {
      logger.w('[AIAgentService] 日记搜索: 关键词为空');
      return [];
    }

    logger.d('[AIAgentService] 搜索日记: $keyword');

    final keywords = _splitKeywords(keyword);
    final diaryMap = <int, dynamic>{};

    // 使用每个关键词搜索，去重
    for (final kw in keywords) {
      final diaries = DiaryRepository.i.findByContentPaginated(kw, 1);
      for (final diary in diaries) {
        diaryMap[diary.id] = diary;
      }
    }

    // 转换为搜索结果
    final results = diaryMap.values.take(_maxSearchResults).map((diary) {
      final content = diary.content;
      final title = _extractDiaryTitle(content);
      final tags = _extractDiaryTags(diary.tags);

      return SearchResult.fromDiary(
        id: diary.id,
        title: title,
        summary: _limitContentLength(content, _summaryPreviewLength),
        createdAt: diary.createdAt,
        tags: tags,
      );
    }).toList();

    logger.d('[AIAgentService] 日记搜索完成: ${results.length}条');
    return results;
  }

  /// 搜索书籍
  ///
  /// [params] 搜索参数 {'query': 关键词}
  /// 返回书籍搜索结果列表
  List<SearchResult> _searchBooks(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    if (keyword == null || keyword.isEmpty) {
      logger.w('[AIAgentService] 书籍搜索: 关键词为空');
      return [];
    }

    logger.d('[AIAgentService] 搜索书籍: $keyword');

    final keywords = _splitKeywords(keyword);
    final bookMap = <int, dynamic>{};

    // 使用每个关键词搜索，去重
    for (final kw in keywords) {
      final books = BookRepository.i.findByTitle(kw);
      for (final book in books) {
        bookMap[book.id] = book;
      }
    }

    // 转换为搜索结果
    final results = bookMap.values.take(_maxSearchResults).map((book) {
      return SearchResult.fromBook(
        id: book.id,
        title: book.title,
        summary: '作者: ${book.author}',
        createdAt: book.createdAt,
      );
    }).toList();

    logger.d('[AIAgentService] 书籍搜索完成: ${results.length}条');
    return results;
  }

  /// 搜索所有内容
  ///
  /// 在文章、日记、书籍中全面搜索
  ///
  /// [params] 搜索参数
  /// 返回所有类型的搜索结果
  List<SearchResult> _searchAll(Map<String, dynamic> params) {
    logger.d('[AIAgentService] 执行全面搜索');

    return [
      ..._searchArticles(params),
      ..._searchDiary(params),
      ..._searchBooks(params),
    ];
  }

  // ========================================================================
  // 数据提取辅助方法
  // ========================================================================

  /// 分割关键词
  ///
  /// 将关键词字符串按空格分割成列表
  ///
  /// [keyword] 关键词字符串
  /// 返回关键词列表
  List<String> _splitKeywords(String keyword) {
    return keyword.split(' ').where((k) => k.trim().isNotEmpty).toList();
  }

  /// 提取文章摘要
  ///
  /// 优先使用AI内容，其次使用原始内容
  ///
  /// [article] 文章对象
  /// 返回摘要文本
  String? _extractArticleSummary(dynamic article) {
    if (article.aiContent?.isNotEmpty == true) {
      return article.aiContent;
    }

    if (article.content?.isNotEmpty == true) {
      return _limitContentLength(article.content, _summaryPreviewLength);
    }

    return null;
  }

  /// 提取日记标题
  ///
  /// 从内容第一行提取标题
  ///
  /// [content] 日记内容
  /// 返回标题文本
  String _extractDiaryTitle(String content) {
    final firstLine = content.split('\n').first;
    final title = firstLine.length > 30
        ? '${firstLine.substring(0, 30)}...'
        : firstLine;
    return title.isNotEmpty ? title : '无标题';
  }

  /// 提取日记标签
  ///
  /// 从标签字符串解析为标签列表
  ///
  /// [tagsString] 标签字符串（逗号分隔）
  /// 返回标签列表
  List<String>? _extractDiaryTags(String? tagsString) {
    if (tagsString?.isNotEmpty != true) return null;

    return tagsString!
        .split(',')
        .where((t) => t.trim().isNotEmpty)
        .toList();
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

// ============================================================================
// AI 提示词常量
// ============================================================================

/// AI 提示词集合
class _Prompts {
  /// 意图分析提示词
  static const intentAnalysis = '''
请分析以下用户问题的意图，返回最合适的分类。

分类说明：
- articles: 查找文章、阅读内容、网页收藏
- diary: 查找日记、个人记录、今天/昨天的内容
- books: 查找书籍、读书笔记
- summary: 需要总结、汇总多种内容
- general: 通用搜索，不确定具体类型

用户问题：{query}

请只返回以下之一：articles, diary, books, summary, general

意图分类：''';

  /// 关键词提取提示词
  static const keywordExtraction = '''
你是一个智能关键词提取专家，需要从用户问题中提取最全面的搜索关键词。

用户问题：{query}

关键词提取规则（按优先级）：

1. **核心概念提取**
   - 识别主题实体（产品、服务、概念、品牌等）
   - 移除无意义词（如何、怎么、什么、查找、请问等）
   - 保留专有名词、技术术语、品牌名称

2. **同义词和别称扩展**（极其重要！）
   - 中英文互译：iPhone → iPhone 苹果手机
   - 全称简称：电话卡 → 电话卡 手机卡 SIM卡 电信卡
   - 品牌与通用名：大疆 → 大疆 DJI 无人机
   - 口语书面语：搞定 → 办理 申请 购买 获取

3. **场景关联词扩展**
   - 相关产品：电话卡 → 流量卡 上网卡 数据卡
   - 相关场景：海外 → 国外 出国 境外 国际 漫游 留学 旅游
   - 相关动作：办理 → 申请 购买 激活 注册 开通

4. **细分领域词**
   - 运营商：移动 联通 电信 中国移动 中国联通 中国电信
   - 服务商：虚拟运营商 MVNO 第三方
   - 产品类型：实体卡 eSIM 虚拟卡

5. **组合搭配**
   - 两两组合核心词生成新关键词
   - 例如："海外 电话卡" → "海外电话卡 国际电话卡 境外手机卡 出国上网卡"

输出要求：
- 生成 15-20 个关键词（越多越好，宁多勿少）
- 用空格分隔
- 按相关性排序（最核心的在前）
- 只输出关键词，不要任何解释说明
- 确保覆盖所有可能的表述方式

示例（仅供参考）：
输入："海外电话卡如何办理"
输出："海外电话卡 国际电话卡 境外手机卡 出国上网卡 国外SIM卡 电话卡 手机卡 SIM卡 海外 国外 境外 出国 国际 办理 申请 购买 激活 开通 流量卡 上网卡"

现在请处理：

搜索关键词：''';

  /// 过滤条件提取提示词
  static const filterExtraction = '''
分析用户问题，判断是否需要特殊过滤条件。

用户问题：{query}

请判断：
1. 是否只查找"收藏"的内容？（包含"收藏"、"favorite"、"喜欢"等词）
2. 是否只查找有"标签"的内容？（明确提到"标签"、"tag"）

请返回 JSON 格式：
{"favorite": true/false, "hasTags": true/false}

只返回 JSON，不要其他内容：''';

  /// 日期范围提取提示词
  static const dateExtraction = '''
分析用户问题中的时间信息，返回对应的时间范围类型。

用户问题：{query}

时间类型选项：
- today: 今天
- yesterday: 昨天
- recent: 最近（最近7天）
- this_week: 本周/这周
- last_week: 上周
- this_month: 本月/这个月
- none: 没有时间限制

请只返回以下之一：today, yesterday, recent, this_week, last_week, this_month, none

时间类型：''';

  /// 空结果分析提示词
  static const emptyResultAnalysis = '''
用户提问："{query}"

我在应用内搜索了文章、日记、书籍，但没有找到任何结果。

请分析这个问题属于哪种类型：
1. search_related: 是应用内容搜索，只是数据库中没有相关内容（如"查找关于Flutter的文章"）
2. external_question: 不是搜索问题，而是询问应用外部信息的通用问题（如"怎么注册账号"、"推荐一个工具"）

请只返回：search_related 或 external_question

类型：''';

  /// AI答案生成提示词
  static const answerGeneration = '''
你是一个专业又友好的助手。请基于以下内容回答用户问题。

用户问题：{query}

相关内容：
{content}

请用自然对话的方式回答,就像和朋友聊天一样:

1. 开头直接回答问题(1-2句话)
2. 如果有重要信息,用列表形式列出关键点
3. 必要时提供详细说明或步骤

格式要求:
- 使用 Markdown 格式让内容更易读
- 重点信息用 **加粗** 标记
- 列表用 - 或数字
- 如果有步骤,用数字列表
- 可以适当使用表情符号 ✨ 让内容生动
- 只基于提供的内容回答,不要编造

回答：''';
}

/// 用户消息模板
class _Messages {
  /// 外部问题回复
  static const externalQuestionResponse = '''
🤖 **AI助手说明**

很抱歉，我目前只能帮您搜索应用内的内容（文章、日记、书籍）。

对于"{query}"这类问题，我暂时无法回答。

**我可以帮您**:
• 📄 搜索收藏的文章
• 📔 查找日记内容
• 📖 搜索读书笔记
• 📋 总结已有内容

请尝试问我关于应用内容的问题，例如：
"查找最近的日记"、"搜索关于Flutter的文章"等。''';

  /// 无结果回复
  static const noResultsResponse = '''
😔 **未找到相关内容**

很抱歉，没有找到与"{query}"相关的内容。

**建议**:
• 尝试使用不同的关键词
• 减少搜索条件的限制
• 检查拼写是否正确''';
}
