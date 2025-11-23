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
/// 负责处理用户查询，生成工具调用计划，执行搜索并总结结果
class AIAgentService {
  static AIAgentService? _instance;
  static AIAgentService get i => _instance ??= AIAgentService._();
  AIAgentService._();

  /// 处理用户查询
  ///
  /// [query] 用户查询
  /// [onStep] 步骤回调，用于实时显示AI思考过程 (stepDescription, stepStatus)
  /// [onToolCall] 工具调用回调
  /// [onResult] 结果回调
  /// [onSearchResults] 搜索结果回调
  Future<String> processQuery({
    required String query,
    required Function(String step, String status) onStep,
    required Function(ToolCall toolCall) onToolCall,
    required Function(String result) onResult,
    required Function(List<SearchResult> results) onSearchResults,
  }) async {
    try {
      logger.i('[AI Agent] 开始处理查询: $query');

      // 1. 分析用户意图
      logger.d('[AI Agent] ========== 步骤1: 分析用户意图 ==========');
      logger.d('[AI Agent] 用户输入: "$query"');
      onStep('ai_chat.step_analyzing_query'.t, 'processing');
      final intent = await _analyzeIntent(query);
      logger.i('[AI Agent] ✅ AI返回结果 - 意图类型: ${intent.description}');
      logger.d('[AI Agent] ==========================================');
      onStep('ai_chat.step_analyzing_query'.t, 'completed');

      // 2. 生成工具调用计划
      logger.d('[AI Agent] ========== 步骤2: 生成搜索计划 ==========');
      onStep('ai_chat.step_planning_tools'.t, 'processing');
      final toolPlan = await _generateToolPlan(query, intent);
      logger.i('[AI Agent] ✅ AI返回结果 - 搜索计划:');
      for (var i = 0; i < toolPlan.length; i++) {
        logger.i('[AI Agent]    ${i + 1}. ${toolPlan[i].description}');
        logger.i('[AI Agent]       类型: ${toolPlan[i].type.name}');
        logger.i('[AI Agent]       参数: ${toolPlan[i].parameters}');
      }
      logger.d('[AI Agent] ==========================================');
      onStep('ai_chat.step_planning_tools'.t, 'completed');

      // 3. 执行工具调用
      logger.d('[AI Agent] ========== 步骤3: 执行搜索 ==========');
      onStep('ai_chat.step_searching'.t, 'processing');

      final allSearchResults = <SearchResult>[];
      for (var i = 0; i < toolPlan.length; i++) {
        final toolCall = toolPlan[i];
        logger.d('[AI Agent] 正在执行: ${toolCall.description}');
        // 不再调用 onToolCall，避免在界面显示搜索关键词
        // onToolCall(toolCall);

        final searchResults = await _executeToolCall(toolCall);
        logger.i('[AI Agent] ✅ 搜索返回结果 (${i + 1}/${toolPlan.length}):');
        logger.i('[AI Agent]    找到 ${searchResults.length} 条结果');
        if (searchResults.isNotEmpty) {
          for (var j = 0; j < searchResults.length && j < 3; j++) {
            logger.i('[AI Agent]    - [${searchResults[j].type.name}] ${searchResults[j].title}');
          }
          if (searchResults.length > 3) {
            logger.i('[AI Agent]    - ... 还有 ${searchResults.length - 3} 条结果');
          }
        }
        allSearchResults.addAll(searchResults);
      }
      onStep('ai_chat.step_searching'.t, 'completed');
      logger.d('[AI Agent] ==========================================');

      // 4. 总结结果
      logger.d('[AI Agent] ========== 步骤4: 生成AI总结 ==========');
      onStep('ai_chat.step_summarizing'.t, 'processing');
      final summary = await _summarizeResults(query, allSearchResults);
      logger.i('[AI Agent] ✅ AI生成的总结内容:');
      logger.i('[AI Agent] ${summary.split('\n').take(5).join('\n[AI Agent] ')}');
      if (summary.split('\n').length > 5) {
        logger.i('[AI Agent] ... (总结内容共 ${summary.split('\n').length} 行)');
      }
      logger.d('[AI Agent] ==========================================');
      onStep('ai_chat.step_summarizing'.t, 'completed');
      onResult(summary);

      // 回调搜索结果 - 显示搜索结果卡片(默认折叠)
      logger.d('[AI Agent] 总共找到 ${allSearchResults.length} 条搜索结果');
      onSearchResults(allSearchResults);

      logger.i('[AI Agent] ========================================');
      logger.i('[AI Agent] 🎉 查询处理完成！');
      logger.i('[AI Agent] ========================================');
      return summary;
    } catch (e, stackTrace) {
      logger.e('[AI Agent] 处理查询失败: $e\n$stackTrace');
      onStep('ai_chat.step_error_occurred'.t, 'error');
      rethrow;
    }
  }

  /// 分析用户意图 - 使用AI智能理解
  Future<QueryIntent> _analyzeIntent(String query) async {
    await Future.delayed(const Duration(milliseconds: 800));

    logger.i('[AI Agent] 🤖 AI分析意图: "$query"');

    final prompt =
        '''
请分析以下用户问题的意图，返回最合适的分类。

分类说明：
- articles: 查找文章、阅读内容、网页收藏
- diary: 查找日记、个人记录、今天/昨天的内容
- books: 查找书籍、读书笔记
- summary: 需要总结、汇总多种内容
- general: 通用搜索，不确定具体类型

用户问题：$query

请只返回以下之一：articles, diary, books, summary, general

意图分类：''';

    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final intentStr = aiResult.trim().toLowerCase();

    QueryIntent intent = QueryIntent.general;
    if (intentStr.contains('article')) {
      intent = QueryIntent.articles;
    } else if (intentStr.contains('diary')) {
      intent = QueryIntent.diary;
    } else if (intentStr.contains('book')) {
      intent = QueryIntent.books;
    } else if (intentStr.contains('summary')) {
      intent = QueryIntent.summary;
    }

    logger.i('[AI Agent] ✅ AI判断意图: ${intent.description}');
    return intent;
  }

  /// AI 智能生成搜索计划
  Future<List<ToolCall>> _generateToolPlan(String query, QueryIntent intent) async {
    await Future.delayed(const Duration(milliseconds: 1200));

    final toolCalls = <ToolCall>[];

    // AI 提取：搜索关键词、日期范围、过滤条件
    final searchKeywords = await _extractSearchKeywords(query);
    final dateRange = await _extractDateRange(query);
    final filters = await _extractFilters(query);

    // AI 决策：根据意图制定搜索计划
    switch (intent) {
      case QueryIntent.articles:
        toolCalls.add(
          ToolCall.searchArticles(query: searchKeywords.isEmpty ? query : searchKeywords, filters: filters),
        );
        break;

      case QueryIntent.diary:
        toolCalls.add(
          ToolCall.searchDiary(query: searchKeywords.isEmpty ? query : searchKeywords, dateRange: dateRange),
        );
        break;

      case QueryIntent.books:
        toolCalls.add(ToolCall.searchBooks(query: searchKeywords.isEmpty ? query : searchKeywords));
        break;

      case QueryIntent.summary:
        if (searchKeywords.isNotEmpty) {
          toolCalls.add(ToolCall.searchArticles(query: searchKeywords, filters: {}));
          toolCalls.add(ToolCall.searchDiary(query: searchKeywords, dateRange: dateRange));
        } else {
          toolCalls.add(ToolCall.searchAll(query: query));
        }
        break;

      case QueryIntent.general:
        toolCalls.add(ToolCall.searchAll(query: searchKeywords.isEmpty ? query : searchKeywords));
        break;
    }

    logger.i('[AI Agent] 📝 搜索计划: ${toolCalls.length}个任务');
    return toolCalls;
  }

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

  /// 总结搜索结果
  Future<String> _summarizeResults(String originalQuery, List<SearchResult> results) async {
    await Future.delayed(const Duration(milliseconds: 800));

    if (results.isEmpty) {
      return await _handleEmptyResults(originalQuery);
    }

    // 按类型分组
    final articles = results.where((r) => r.type == SearchResultType.article).toList();
    final diaries = results.where((r) => r.type == SearchResultType.diary).toList();
    final books = results.where((r) => r.type == SearchResultType.book).toList();

    // 获取完整内容并生成AI答案
    logger.i('[AI Agent] 🤖 正在获取完整内容以生成AI答案...');
    final fullContents = await _fetchFullContents(results);

    if (fullContents.isEmpty) {
      logger.w('[AI Agent] ⚠️ 无法获取完整内容');
      return '😔 **未找到相关内容**\n\n很抱歉，搜索到的内容无法加载。';
    }

    // 直接使用 AI 生成智能答案,不显示搜索统计信息
    final aiAnswer = await _generateAIAnswer(
      originalQuery,
      fullContents,
      articles.length,
      diaries.length,
      books.length,
    );

    return aiAnswer;
  }

  /// 处理空搜索结果 - 使用AI判断问题类型并生成回复
  Future<String> _handleEmptyResults(String query) async {
    logger.i('[AI Agent] 🤖 AI判断空结果原因: "$query"');

    final prompt =
        '''
用户提问："$query"

我在应用内搜索了文章、日记、书籍，但没有找到任何结果。

请分析这个问题属于哪种类型：
1. search_related: 是应用内容搜索，只是数据库中没有相关内容（如"查找关于Flutter的文章"）
2. external_question: 不是搜索问题，而是询问应用外部信息的通用问题（如"怎么注册账号"、"推荐一个工具"）

请只返回：search_related 或 external_question

类型：''';

    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final isExternalQuestion = aiResult.trim().toLowerCase().contains('external');

    if (isExternalQuestion) {
      return '''🤖 **AI助手说明**

很抱歉，我目前只能帮您搜索应用内的内容（文章、日记、书籍）。

对于"$query"这类问题，我暂时无法回答。

**我可以帮您**:
• 📄 搜索收藏的文章
• 📔 查找日记内容
• 📖 搜索读书笔记
• 📋 总结已有内容

请尝试问我关于应用内容的问题，例如：
"查找最近的日记"、"搜索关于Flutter的文章"等。''';
    }

    return '''😔 **未找到相关内容**

很抱歉，没有找到与"$query"相关的内容。

**建议**:
• 尝试使用不同的关键词
• 减少搜索条件的限制
• 检查拼写是否正确''';
  }

  /// AI 智能提取搜索关键词
  Future<String> _extractSearchKeywords(String query) async {
    logger.i('[AI Agent] 🤖 AI分析关键词: "$query"');

    final prompt =
        '''
请分析以下用户问题，提取出 10 个最相关的搜索关键词。

重要要求：
1. 提取核心关键词（移除问句词、动词、时间词）
2. **扩展同义词和相关词**（非常重要！）
   - 例如："苹果电脑" → 苹果电脑 Mac MacBook MacOS Apple 笔记本 macOS 苹果系统 Apple电脑 Mac系统
   - 例如："手机" → 手机 iPhone Android 华为 小米 三星 移动设备 智能手机 手机设备
   - 例如："网络" → 网络 WiFi 无线 路由器 上网 联网 网速 宽带
   - 例如："大疆" → 大疆 DJI 无人机 飞行器 航拍 drone 大疆创新
3. 包含英文/中文变体和常见表述方式
4. 返回 10 个关键词，用空格分隔
5. 按相关性排序（最相关的在前）
6. 只返回关键词，不要解释

用户问题：$query

搜索关键词（10个）：''';

    final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
    final keywords = aiResult.trim();
    logger.i('[AI Agent] ✅ AI扩展关键词: "$keywords"');
    return keywords.isNotEmpty ? keywords : query;
  }

  /// AI 提取过滤条件
  Future<Map<String, dynamic>> _extractFilters(String query) async {
    final filters = <String, dynamic>{};

    final prompt =
        '''
分析用户问题，判断是否需要特殊过滤条件。

用户问题：$query

请判断：
1. 是否只查找"收藏"的内容？（包含"收藏"、"favorite"、"喜欢"等词）
2. 是否只查找有"标签"的内容？（明确提到"标签"、"tag"）

请返回 JSON 格式：
{"favorite": true/false, "hasTags": true/false}

只返回 JSON，不要其他内容：''';

    try {
      final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
      // 简单解析 AI 返回的结果
      if (aiResult.contains('"favorite": true') || aiResult.contains("'favorite': true")) {
        filters['favorite'] = true;
      }
      if (aiResult.contains('"hasTags": true') || aiResult.contains("'hasTags': true")) {
        filters['hasTags'] = true;
      }
    } catch (e) {
      logger.e('[AI Agent] AI提取过滤条件失败: $e');
    }

    return filters;
  }

  /// AI 智能提取日期范围
  Future<DateTimeRange?> _extractDateRange(String query) async {
    logger.i('[AI Agent] 🤖 AI日期提取: "$query"');

    final prompt =
        '''
分析用户问题中的时间信息，返回对应的时间范围类型。

用户问题：$query

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

    try {
      final aiResult = await AiService.i.getCompletion(prompt, functionType: 0);
      final timeType = aiResult.trim().toLowerCase();

      final now = DateTime.now();
      DateTimeRange? result;

      switch (timeType) {
        case 'today':
          result = DateTimeRange(
            start: DateTime(now.year, now.month, now.day),
            end: DateTime(now.year, now.month, now.day, 23, 59, 59),
          );
          break;
        case 'yesterday':
          final yesterday = now.subtract(const Duration(days: 1));
          result = DateTimeRange(
            start: DateTime(yesterday.year, yesterday.month, yesterday.day),
            end: DateTime(yesterday.year, yesterday.month, yesterday.day, 23, 59, 59),
          );
          break;
        case 'recent':
          result = DateTimeRange(start: now.subtract(const Duration(days: 7)), end: now);
          break;
        case 'this_week':
          final weekday = now.weekday;
          final startOfWeek = now.subtract(Duration(days: weekday - 1));
          result = DateTimeRange(start: DateTime(startOfWeek.year, startOfWeek.month, startOfWeek.day), end: now);
          break;
        case 'last_week':
          final weekday = now.weekday;
          final lastWeekStart = now.subtract(Duration(days: weekday + 6));
          final lastWeekEnd = now.subtract(Duration(days: weekday));
          result = DateTimeRange(
            start: DateTime(lastWeekStart.year, lastWeekStart.month, lastWeekStart.day),
            end: DateTime(lastWeekEnd.year, lastWeekEnd.month, lastWeekEnd.day, 23, 59, 59),
          );
          break;
        case 'this_month':
          result = DateTimeRange(start: DateTime(now.year, now.month, 1), end: now);
          break;
      }

      if (result != null) {
        logger.i('[AI Agent] ✅ 识别到时间范围: $timeType');
      }
      return result;
    } catch (e) {
      logger.e('[AI Agent] AI日期提取失败: $e');
      return null;
    }
  }

  /// 获取搜索结果的完整内容
  Future<Map<String, String>> _fetchFullContents(List<SearchResult> results) async {
    final fullContents = <String, String>{};

    for (final result in results) {
      try {
        String? content;
        final key = '${result.type}:${result.id}';

        switch (result.type) {
          case SearchResultType.article:
            final article = ArticleRepository.i.find(result.id);
            if (article != null) {
              // 优先使用 AI 内容，其次原始内容
              final articleContent = article.aiContent?.isNotEmpty == true
                  ? article.aiContent!
                  : (article.content?.isNotEmpty == true ? article.content! : '');

              if (articleContent.isNotEmpty) {
                content = '【文章】${article.title ?? "无标题"}\n\n$articleContent';
              }
            }
            break;
          case SearchResultType.diary:
            final diary = DiaryRepository.i.find(result.id);
            if (diary != null && diary.content.isNotEmpty) {
              final dateStr =
                  '${diary.createdAt.year}-${diary.createdAt.month.toString().padLeft(2, '0')}-${diary.createdAt.day.toString().padLeft(2, '0')}';
              content = '【日记】$dateStr\n\n${diary.content}';
            }
            break;
          case SearchResultType.book:
            final book = BookRepository.i.find(result.id);
            if (book != null) {
              final bookIntro = book.introduction.isNotEmpty ? book.introduction : '暂无简介';
              content = '【书籍】${book.title}\n作者: ${book.author}\n\n$bookIntro';
            }
            break;
        }

        if (content != null && content.isNotEmpty) {
          fullContents[key] = content;
          logger.d('[AI Agent] 获取完整内容: $key (${content.length} 字符)');
        }
      } catch (e) {
        logger.e('[AI Agent] 获取完整内容失败: ${result.type}:${result.id}', error: e);
      }
    }

    return fullContents;
  }

  /// 基于完整内容生成AI智能答案
  Future<String> _generateAIAnswer(
    String query,
    Map<String, String> fullContents,
    int articles,
    int diaries,
    int books,
  ) async {
    logger.i('[AI Agent] 🤖 AI生成答案...');

    if (fullContents.isEmpty) {
      return '抱歉，未找到相关内容。';
    }

    // 合并内容（限制长度避免 token 过多）
    final allContent = fullContents.values.join('\n\n---\n\n');
    final contentToAnalyze = allContent.length > 6000 ? allContent.substring(0, 6000) : allContent;

    // 调用 AI 生成答案
    final prompt =
        '''
你是一个专业又友好的助手。请基于以下内容回答用户问题。

用户问题：$query

相关内容：
$contentToAnalyze

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

    final aiResponse = await AiService.i.getCompletion(prompt, functionType: 0);

    logger.i('[AI Agent] ✅ AI答案生成完成');
    return aiResponse.trim();
  }

  /// 搜索文章
  List<SearchResult> _searchArticles(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    final filters = params['filters'] as Map<String, dynamic>?;

    if (keyword == null || keyword.isEmpty) {
      return [];
    }

    // 拆分多个关键词，每个关键词都搜索
    final keywords = keyword.split(' ').where((k) => k.trim().isNotEmpty).toList();
    final articleMap = <int, dynamic>{}; // 用 Map 去重

    logger.i('[AI Agent] 搜索文章 - 关键词数: ${keywords.length}');

    for (final kw in keywords) {
      logger.d('[AI Agent]   使用关键词: "$kw"');
      final articles = ArticleRepository.i.findArticles(
        keyword: kw,
        isFavorite: filters?['favorite'] as bool?,
        limit: 20, // 每个关键词搜 20 条，最后合并
      );

      for (final article in articles) {
        articleMap[article.id] = article; // ID 去重
      }
    }

    logger.i('[AI Agent] 找到文章数: ${articleMap.length}');

    // 限制返回 top 10 结果
    final limitedArticles = articleMap.values.take(10);

    return limitedArticles.map((article) {
      String? summary;
      if (article.aiContent != null && article.aiContent!.isNotEmpty) {
        summary = article.aiContent;
      } else if (article.content != null && article.content!.isNotEmpty) {
        final content = article.content!;
        summary = content.length > 150 ? content.substring(0, 150) : content;
      }

      return SearchResult.fromArticle(
        id: article.id,
        title: article.title ?? '无标题',
        summary: summary,
        createdAt: article.createdAt,
        isFavorite: article.isFavorite,
      );
    }).toList();
  }

  /// 搜索日记
  List<SearchResult> _searchDiary(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;

    if (keyword == null || keyword.isEmpty) {
      return [];
    }

    // 拆分多个关键词，每个关键词都搜索
    final keywords = keyword.split(' ').where((k) => k.trim().isNotEmpty).toList();
    final diaryMap = <int, dynamic>{}; // 用 Map 去重

    logger.i('[AI Agent] 搜索日记 - 关键词数: ${keywords.length}');

    for (final kw in keywords) {
      logger.d('[AI Agent]   使用关键词: "$kw"');
      final diaries = DiaryRepository.i.findByContentPaginated(kw, 1);

      for (final diary in diaries) {
        diaryMap[diary.id] = diary; // ID 去重
      }
    }

    logger.i('[AI Agent] 找到日记数: ${diaryMap.length}');

    // 限制返回 top 10 结果
    final limitedDiaries = diaryMap.values.take(10);

    return limitedDiaries.map((diary) {
      final content = diary.content;
      final firstLine = content.split('\n').first;
      final title = firstLine.length > 30 ? '${firstLine.substring(0, 30)}...' : firstLine;

      // 处理标签
      List<String>? tagList;
      if (diary.tags != null && diary.tags!.isNotEmpty) {
        tagList = diary.tags!.split(',').where((t) => t.trim().isNotEmpty).toList();
      }

      return SearchResult.fromDiary(
        id: diary.id,
        title: title.isNotEmpty ? title : '无标题',
        summary: content.length > 150 ? '${content.substring(0, 150)}...' : content,
        createdAt: diary.createdAt,
        tags: tagList,
      );
    }).toList();
  }

  /// 搜索书籍
  List<SearchResult> _searchBooks(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;

    if (keyword == null || keyword.isEmpty) {
      return [];
    }

    // 拆分多个关键词，每个关键词都搜索
    final keywords = keyword.split(' ').where((k) => k.trim().isNotEmpty).toList();
    final bookMap = <int, dynamic>{}; // 用 Map 去重

    logger.i('[AI Agent] 搜索书籍 - 关键词数: ${keywords.length}');

    for (final kw in keywords) {
      logger.d('[AI Agent]   使用关键词: "$kw"');
      final books = BookRepository.i.findByTitle(kw);

      for (final book in books) {
        bookMap[book.id] = book; // ID 去重
      }
    }

    logger.i('[AI Agent] 找到书籍数: ${bookMap.length}');

    // 限制返回 top 10 结果
    final limitedBooks = bookMap.values.take(10);

    return limitedBooks.map((book) {
      return SearchResult.fromBook(
        id: book.id,
        title: book.title,
        summary: '作者: ${book.author}',
        createdAt: book.createdAt,
      );
    }).toList();
  }

  /// 搜索所有内容
  List<SearchResult> _searchAll(Map<String, dynamic> params) {
    final results = <SearchResult>[];

    results.addAll(_searchArticles(params));
    results.addAll(_searchDiary(params));
    results.addAll(_searchBooks(params));

    return results;
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
