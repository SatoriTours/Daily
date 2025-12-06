import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/weekly_summary_prompts.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';

/// 周报服务
///
/// 负责生成和管理每周的文章和日记总结
/// 非生产环境下使用最近10篇文章和日记进行测试
class WeeklySummaryService {
  // 单例实现
  WeeklySummaryService._();
  static final WeeklySummaryService _instance = WeeklySummaryService._();
  static WeeklySummaryService get i => _instance;

  /// 调试模式下的数据数量限制
  static const int _debugDataLimit = 10;

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 检查并生成当前需要的周报
  ///
  /// 返回是否有新的周报需要生成
  Future<bool> checkAndGenerateSummaries() async {
    logger.i('[周报服务] 开始检查周报');

    // 非生产环境：检查是否有最近数据的周报
    if (!AppInfoUtils.isProduction) {
      return _checkDebugSummary();
    }

    // 生产环境：获取上周的日期范围（只生成已完成的周）
    final lastWeekRange = _getLastCompletedWeekRange();
    if (lastWeekRange == null) {
      logger.i('[周报服务] 本周尚未结束，无需生成');
      return false;
    }

    final (weekStart, weekEnd) = lastWeekRange;

    // 检查是否已存在该周的周报
    final existing = WeeklySummaryRepository.i.findByWeekStartDate(weekStart);
    if (existing != null && existing.isCompleted) {
      logger.i('[周报服务] 上周周报已存在且完成');
      return false;
    }

    // 需要生成周报
    logger.i('[周报服务] 需要生成周报: ${weekStart.toString()} - ${weekEnd.toString()}');
    return true;
  }

  /// 生成指定周的周报
  Future<WeeklySummaryModel?> generateWeeklySummary(DateTime weekStart, DateTime weekEnd) async {
    // 非生产环境使用调试模式生成
    if (!AppInfoUtils.isProduction) {
      return _generateDebugSummary();
    }

    return _generateProductionSummary(weekStart, weekEnd);
  }

  /// 生产环境生成周报
  Future<WeeklySummaryModel?> _generateProductionSummary(DateTime weekStart, DateTime weekEnd) async {
    logger.i('[周报服务] 开始生成周报: ${weekStart.toString()} - ${weekEnd.toString()}');

    // 获取或创建周报记录
    final summary = WeeklySummaryRepository.i.getOrCreate(weekStart, weekEnd);

    // 更新状态为生成中
    WeeklySummaryRepository.i.updateStatus(summary.id, WeeklySummaryStatus.generating);

    try {
      // 获取该周的文章、日记和书籍观点
      final articles = _getArticlesInRange(weekStart, weekEnd);
      final diaries = _getDiariesInRange(weekStart, weekEnd);
      final viewpoints = _getViewpointsInRange(weekStart, weekEnd);

      // 获取上周的产品思考
      final previousAppIdeas = _getPreviousAppIdeas();

      logger.i('[周报服务] 找到 ${articles.length} 篇文章, ${diaries.length} 篇日记, ${viewpoints.length} 个书摘');

      if (articles.isEmpty && diaries.isEmpty && viewpoints.isEmpty) {
        // 没有内容，标记为完成但内容为空
        WeeklySummaryRepository.i.updateContent(
          summary.id,
          _generateEmptySummary(weekStart, weekEnd),
          0,
          0,
          null,
          null,
        );
        return WeeklySummaryRepository.i.find(summary.id);
      }

      // 准备AI输入
      final prompt = buildProductionSummaryPrompt(
        articles,
        diaries,
        weekStart,
        weekEnd,
        viewpoints: viewpoints,
        previousAppIdeas: previousAppIdeas,
      );

      // 调用AI生成总结
      final aiResult = await AiService.i.getCompletion(prompt);

      if (aiResult.isEmpty) {
        logger.e('[周报服务] AI生成失败，返回空结果');
        WeeklySummaryRepository.i.updateStatus(summary.id, WeeklySummaryStatus.failed);
        return null;
      }

      // 提取产品灵感部分用于下周融合
      final appIdeas = _extractAppIdeas(aiResult);

      // 保存结果
      final articleIds = articles.map((a) => a.id.toString()).join(',');
      final diaryIds = diaries.map((d) => d.id.toString()).join(',');
      final viewpointIds = viewpoints.map((v) => v.id.toString()).join(',');

      WeeklySummaryRepository.i.updateContent(
        summary.id,
        aiResult,
        articles.length,
        diaries.length,
        articleIds,
        diaryIds,
        viewpointIds: viewpointIds,
        viewpointCount: viewpoints.length,
        appIdeas: appIdeas,
      );

      logger.i('[周报服务] 周报生成完成');
      return WeeklySummaryRepository.i.find(summary.id);
    } catch (e, stackTrace) {
      logger.e('[周报服务] 生成周报失败', error: e, stackTrace: stackTrace);
      WeeklySummaryRepository.i.updateStatus(summary.id, WeeklySummaryStatus.failed);
      return null;
    }
  }

  /// 获取最近完成的一周的周报
  WeeklySummaryModel? getLatestSummary() {
    final summaries = WeeklySummaryRepository.i.findRecent(1);
    return summaries.isNotEmpty ? summaries.first : null;
  }

  /// 获取所有周报
  List<WeeklySummaryModel> getAllSummaries() {
    return WeeklySummaryRepository.i.findAllCompleted();
  }

  /// 获取上周的日期范围
  (DateTime, DateTime)? getLastCompletedWeekRange() {
    // 非生产环境返回今天作为结束日期
    if (!AppInfoUtils.isProduction) {
      return _getDebugWeekRange();
    }
    return _getLastCompletedWeekRange();
  }

  // ========================================================================
  // 私有方法 - 调试模式
  // ========================================================================

  /// 检查调试模式下是否需要生成周报
  Future<bool> _checkDebugSummary() async {
    logger.i('[周报服务-调试] 检查是否需要生成调试周报');

    // 获取最近的文章和日记
    final articles = _getRecentArticles();
    final diaries = _getRecentDiaries();

    if (articles.isEmpty && diaries.isEmpty) {
      logger.i('[周报服务-调试] 没有数据，无需生成');
      return false;
    }

    // 检查是否已存在调试周报
    final debugRange = _getDebugWeekRange();
    if (debugRange == null) return false;

    final (weekStart, _) = debugRange;
    final existing = WeeklySummaryRepository.i.findByWeekStartDate(weekStart);

    if (existing != null && existing.isCompleted) {
      logger.i('[周报服务-调试] 调试周报已存在');
      return false;
    }

    logger.i('[周报服务-调试] 需要生成调试周报');
    return true;
  }

  /// 调试模式的日期范围（使用今天作为标识）
  (DateTime, DateTime)? _getDebugWeekRange() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    // 调试模式：使用今天作为起始和结束日期
    return (today, today);
  }

  /// 调试模式生成周报
  Future<WeeklySummaryModel?> _generateDebugSummary() async {
    logger.i('[周报服务-调试] 开始生成调试周报（最近$_debugDataLimit条数据）');

    final debugRange = _getDebugWeekRange();
    if (debugRange == null) return null;

    final (weekStart, weekEnd) = debugRange;

    // 获取或创建周报记录
    final summary = WeeklySummaryRepository.i.getOrCreate(weekStart, weekEnd);

    // 更新状态为生成中
    WeeklySummaryRepository.i.updateStatus(summary.id, WeeklySummaryStatus.generating);

    try {
      // 获取最近的文章、日记和书籍观点
      final articles = _getRecentArticles();
      final diaries = _getRecentDiaries();
      final viewpoints = _getRecentViewpoints();

      // 获取上周的产品思考
      final previousAppIdeas = _getPreviousAppIdeas();

      logger.i('[周报服务-调试] 找到 ${articles.length} 篇文章, ${diaries.length} 篇日记, ${viewpoints.length} 个书摘');

      if (articles.isEmpty && diaries.isEmpty && viewpoints.isEmpty) {
        WeeklySummaryRepository.i.updateContent(summary.id, _generateDebugEmptySummary(), 0, 0, null, null);
        return WeeklySummaryRepository.i.find(summary.id);
      }

      // 准备AI输入
      final prompt = buildDebugSummaryPrompt(
        articles,
        diaries,
        viewpoints: viewpoints,
        previousAppIdeas: previousAppIdeas,
      );

      // 调用AI生成总结
      final aiResult = await AiService.i.getCompletion(prompt);

      if (aiResult.isEmpty) {
        logger.e('[周报服务-调试] AI生成失败，返回空结果');
        WeeklySummaryRepository.i.updateStatus(summary.id, WeeklySummaryStatus.failed);
        return null;
      }

      // 提取产品灵感部分用于下周融合
      final appIdeas = _extractAppIdeas(aiResult);

      // 保存结果
      final articleIds = articles.map((a) => a.id.toString()).join(',');
      final diaryIds = diaries.map((d) => d.id.toString()).join(',');
      final viewpointIds = viewpoints.map((v) => v.id.toString()).join(',');

      WeeklySummaryRepository.i.updateContent(
        summary.id,
        aiResult,
        articles.length,
        diaries.length,
        articleIds,
        diaryIds,
        viewpointIds: viewpointIds,
        viewpointCount: viewpoints.length,
        appIdeas: appIdeas,
      );

      logger.i('[周报服务-调试] 调试周报生成完成');
      return WeeklySummaryRepository.i.find(summary.id);
    } catch (e, stackTrace) {
      logger.e('[周报服务-调试] 生成周报失败', error: e, stackTrace: stackTrace);
      WeeklySummaryRepository.i.updateStatus(summary.id, WeeklySummaryStatus.failed);
      return null;
    }
  }

  /// 获取最近的文章
  List<ArticleModel> _getRecentArticles() {
    final allArticles = ArticleRepository.i.all();
    // 按创建时间倒序排列，取前N条
    allArticles.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return allArticles.take(_debugDataLimit).toList();
  }

  /// 获取最近的日记
  List<DiaryModel> _getRecentDiaries() {
    final allDiaries = DiaryRepository.i.findAll();
    // 按创建时间倒序排列，取前N条
    return allDiaries.take(_debugDataLimit).toList();
  }

  /// 获取最近的书籍观点
  List<BookViewpointModel> _getRecentViewpoints() {
    final allViewpoints = BookViewpointRepository.i.all();
    // 取前N条
    return allViewpoints.take(_debugDataLimit).toList();
  }

  /// 获取上周的产品思考内容
  String? _getPreviousAppIdeas() {
    // 获取最近一个已完成的周报
    final recentSummaries = WeeklySummaryRepository.i.findRecent(2);
    if (recentSummaries.length < 2) return null;

    // 返回上一个周报的产品思考
    return recentSummaries[1].appIdeas;
  }

  /// 从 AI 结果中提取产品灵感部分
  String? _extractAppIdeas(String aiResult) {
    // 尝试提取"产品灵感"部分的内容
    final regex = RegExp(r'###?\s*💡?\s*产品灵感([\s\S]*?)(?=###|---|$)', multiLine: true);
    final match = regex.firstMatch(aiResult);
    if (match != null) {
      return match.group(1)?.trim();
    }
    return null;
  }

  /// 调试模式空周报
  String _generateDebugEmptySummary() {
    return '''
# 📅 调试模式周报

## 📊 概览

当前没有收藏任何文章，也没有写日记。

## 💡 建议

这是调试模式，请添加一些文章或日记后重新生成。
''';
  }

  // ========================================================================
  // 私有方法 - 生产环境
  // ========================================================================

  /// 获取上一个已完成的周的日期范围
  ///
  /// 只有当周日结束后才返回该周的范围
  (DateTime, DateTime)? _getLastCompletedWeekRange() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    // 如果今天是周日，说明本周还没结束
    // weekday: 1=周一, 7=周日
    if (now.weekday == 7) {
      // 本周日还没过完，返回上上周
      final lastSunday = today.subtract(Duration(days: 7));
      final lastMonday = lastSunday.subtract(Duration(days: 6));
      return (lastMonday, lastSunday);
    }

    // 今天不是周日，可以生成上周的周报
    // 计算上周一和上周日
    final daysFromMonday = now.weekday - 1; // 今天距离本周一的天数
    final thisMonday = today.subtract(Duration(days: daysFromMonday));
    final lastSunday = thisMonday.subtract(Duration(days: 1));
    final lastMonday = lastSunday.subtract(Duration(days: 6));

    return (lastMonday, lastSunday);
  }

  /// 获取指定日期范围内的文章
  List<ArticleModel> _getArticlesInRange(DateTime start, DateTime end) {
    final allArticles = ArticleRepository.i.all();
    return allArticles.where((article) {
      final createdAt = article.createdAt;
      return createdAt.isAfter(start.subtract(Duration(seconds: 1))) && createdAt.isBefore(end.add(Duration(days: 1)));
    }).toList();
  }

  /// 获取指定日期范围内的日记
  List<DiaryModel> _getDiariesInRange(DateTime start, DateTime end) {
    final allDiaries = DiaryRepository.i.findAll();
    return allDiaries.where((diary) {
      final createdAt = diary.createdAt;
      return createdAt.isAfter(start.subtract(Duration(seconds: 1))) && createdAt.isBefore(end.add(Duration(days: 1)));
    }).toList();
  }

  /// 获取指定日期范围内的书籍观点
  List<BookViewpointModel> _getViewpointsInRange(DateTime start, DateTime end) {
    final allViewpoints = BookViewpointRepository.i.all();
    return allViewpoints.where((vp) {
      final createdAt = vp.createdAt;
      return createdAt.isAfter(start.subtract(Duration(seconds: 1))) && createdAt.isBefore(end.add(Duration(days: 1)));
    }).toList();
  }

  /// 生成空周报内容
  String _generateEmptySummary(DateTime weekStart, DateTime weekEnd) {
    return '''
# 📅 ${weekStart.month}月${weekStart.day}日 - ${weekEnd.month}月${weekEnd.day}日 周报

## 📊 本周概览

这周没有收藏任何文章，也没有写日记。

## 💡 建议

不妨试试：
- 阅读一些感兴趣的文章并收藏
- 记录每天的想法和感悟
- 养成定期整理知识的习惯

期待下周的精彩内容！
''';
  }
}
