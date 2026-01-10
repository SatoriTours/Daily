import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/weekly_summary_prompts.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/services/service_base.dart';

/// 周报服务
class WeeklySummaryService extends AppService {
  @override
  ServicePriority get priority => ServicePriority.low;

  WeeklySummaryService._();
  static final WeeklySummaryService _instance = WeeklySummaryService._();
  static WeeklySummaryService get i => _instance;

  static const int _debugDataLimit = 10;

  @override
  Future<void> init() async {}

  // ========================================================================
  // 公共方法
  // ========================================================================

  Future<bool> checkAndGenerateSummaries() async {
    if (!AppInfoUtils.isProduction) return _checkDebugSummary();

    final lastWeekRange = _getLastCompletedWeekRange();
    if (lastWeekRange == null) return false;

    final (weekStart, _) = lastWeekRange;
    final existing = WeeklySummaryRepository.i.findByWeekStartDate(weekStart);
    return existing == null || !existing.isCompleted;
  }

  Future<WeeklySummaryModel?> generateWeeklySummary(
    DateTime weekStart,
    DateTime weekEnd,
  ) async {
    if (!AppInfoUtils.isProduction) {
      return _generateDebugSummary(weekStart, weekEnd);
    }
    return _generateProductionSummary(weekStart, weekEnd);
  }

  WeeklySummaryModel? getLatestSummary() {
    final summaries = WeeklySummaryRepository.i.findRecent(1);
    return summaries.isNotEmpty ? summaries.first : null;
  }

  List<WeeklySummaryModel> getAllSummaries() =>
      WeeklySummaryRepository.i.findAllCompleted();

  (DateTime, DateTime)? getLastCompletedWeekRange() => AppInfoUtils.isProduction
      ? _getLastCompletedWeekRange()
      : _getDebugWeekRange();

  // ========================================================================
  // 调试模式
  // ========================================================================

  Future<bool> _checkDebugSummary() async {
    final articles = _getRecentArticles();
    final diaries = _getRecentDiaries();
    if (articles.isEmpty && diaries.isEmpty) return false;

    final debugRange = _getDebugWeekRange();
    if (debugRange == null) return false;

    final existing = WeeklySummaryRepository.i.findByWeekStartDate(
      debugRange.$1,
    );
    return existing == null || !existing.isCompleted;
  }

  (DateTime, DateTime)? _getDebugWeekRange() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    return (today, today);
  }

  Future<WeeklySummaryModel?> _generateDebugSummary(
    DateTime weekStart,
    DateTime weekEnd,
  ) async {
    final summary = WeeklySummaryRepository.i.getOrCreate(weekStart, weekEnd);
    WeeklySummaryRepository.i.updateStatus(
      summary.id,
      WeeklySummaryStatus.generating,
    );

    try {
      final articles = _getRecentArticles();
      final diaries = _getRecentDiaries();
      final viewpoints = _getRecentViewpoints();
      final previousAppIdeas = _getPreviousAppIdeas();

      if (articles.isEmpty && diaries.isEmpty && viewpoints.isEmpty) {
        WeeklySummaryRepository.i.updateContent(
          summary.id,
          _generateDebugEmptySummary(),
          0,
          0,
          null,
          null,
        );
        return WeeklySummaryRepository.i.find(summary.id);
      }

      final prompt = buildDebugSummaryPrompt(
        articles,
        diaries,
        viewpoints: viewpoints,
        previousAppIdeas: previousAppIdeas,
      );
      final aiResult = await AiService.i.getCompletion(prompt);

      if (aiResult.isEmpty) {
        WeeklySummaryRepository.i.updateStatus(
          summary.id,
          WeeklySummaryStatus.failed,
        );
        return null;
      }

      _saveResult(summary.id, aiResult, articles, diaries, viewpoints);
      return WeeklySummaryRepository.i.find(summary.id);
    } catch (e, stackTrace) {
      logger.e('[周报服务] 生成周报失败', error: e, stackTrace: stackTrace);
      WeeklySummaryRepository.i.updateStatus(
        summary.id,
        WeeklySummaryStatus.failed,
      );
      return null;
    }
  }

  List<ArticleModel> _getRecentArticles() {
    final allArticles = ArticleRepository.i.all();
    allArticles.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return allArticles.take(_debugDataLimit).toList();
  }

  List<DiaryModel> _getRecentDiaries() =>
      DiaryRepository.i.findAll().take(_debugDataLimit).toList();

  List<BookViewpointModel> _getRecentViewpoints() =>
      BookViewpointRepository.i.all().take(_debugDataLimit).toList();

  String? _getPreviousAppIdeas() {
    final recentSummaries = WeeklySummaryRepository.i.findRecent(2);
    return recentSummaries.length < 2 ? null : recentSummaries[1].appIdeas;
  }

  String? _extractAppIdeas(String aiResult) {
    final regex = RegExp(
      r'###?\s*💡?\s*产品灵感([\s\S]*?)(?=###|---|$)',
      multiLine: true,
    );
    final match = regex.firstMatch(aiResult);
    return match?.group(1)?.trim();
  }

  String _generateDebugEmptySummary() => '''
# 📅 调试模式周报

## 📊 概览

当前没有收藏任何文章，也没有写日记。

## 💡 建议

这是调试模式，请添加一些文章或日记后重新生成。
''';

  // ========================================================================
  // 生产环境
  // ========================================================================

  (DateTime, DateTime)? _getLastCompletedWeekRange() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    if (now.weekday == 7) {
      final lastSunday = today.subtract(const Duration(days: 7));
      return (lastSunday.subtract(const Duration(days: 6)), lastSunday);
    }

    final daysFromMonday = now.weekday - 1;
    final thisMonday = today.subtract(Duration(days: daysFromMonday));
    final lastSunday = thisMonday.subtract(const Duration(days: 1));
    return (lastSunday.subtract(const Duration(days: 6)), lastSunday);
  }

  Future<WeeklySummaryModel?> _generateProductionSummary(
    DateTime weekStart,
    DateTime weekEnd,
  ) async {
    final summary = WeeklySummaryRepository.i.getOrCreate(weekStart, weekEnd);
    WeeklySummaryRepository.i.updateStatus(
      summary.id,
      WeeklySummaryStatus.generating,
    );

    try {
      final articles = _getArticlesInRange(weekStart, weekEnd);
      final diaries = _getDiariesInRange(weekStart, weekEnd);
      final viewpoints = _getViewpointsInRange(weekStart, weekEnd);
      final previousAppIdeas = _getPreviousAppIdeas();

      if (articles.isEmpty && diaries.isEmpty && viewpoints.isEmpty) {
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

      final prompt = buildProductionSummaryPrompt(
        articles,
        diaries,
        weekStart,
        weekEnd,
        viewpoints: viewpoints,
        previousAppIdeas: previousAppIdeas,
      );
      final aiResult = await AiService.i.getCompletion(prompt);

      if (aiResult.isEmpty) {
        WeeklySummaryRepository.i.updateStatus(
          summary.id,
          WeeklySummaryStatus.failed,
        );
        return null;
      }

      _saveResult(summary.id, aiResult, articles, diaries, viewpoints);
      return WeeklySummaryRepository.i.find(summary.id);
    } catch (e, stackTrace) {
      logger.e('[周报服务] 生成周报失败', error: e, stackTrace: stackTrace);
      WeeklySummaryRepository.i.updateStatus(
        summary.id,
        WeeklySummaryStatus.failed,
      );
      return null;
    }
  }

  void _saveResult(
    int id,
    String aiResult,
    List<ArticleModel> articles,
    List<DiaryModel> diaries,
    List<BookViewpointModel> viewpoints,
  ) {
    WeeklySummaryRepository.i.updateContent(
      id,
      aiResult,
      articles.length,
      diaries.length,
      articles.map((a) => a.id.toString()).join(','),
      diaries.map((d) => d.id.toString()).join(','),
      viewpointIds: viewpoints.map((v) => v.id.toString()).join(','),
      viewpointCount: viewpoints.length,
      appIdeas: _extractAppIdeas(aiResult),
    );
  }

  List<ArticleModel> _getArticlesInRange(DateTime start, DateTime end) =>
      ArticleRepository.i.all().where((article) {
        final createdAt = article.createdAt;
        return createdAt.isAfter(start.subtract(const Duration(seconds: 1))) &&
            createdAt.isBefore(end.add(const Duration(days: 1)));
      }).toList();

  List<DiaryModel> _getDiariesInRange(DateTime start, DateTime end) =>
      DiaryRepository.i.findAll().where((diary) {
        final createdAt = diary.createdAt;
        return createdAt.isAfter(start.subtract(const Duration(seconds: 1))) &&
            createdAt.isBefore(end.add(const Duration(days: 1)));
      }).toList();

  List<BookViewpointModel> _getViewpointsInRange(
    DateTime start,
    DateTime end,
  ) => BookViewpointRepository.i.all().where((vp) {
    final createdAt = vp.createdAt;
    return createdAt.isAfter(start.subtract(const Duration(seconds: 1))) &&
        createdAt.isBefore(end.add(const Duration(days: 1)));
  }).toList();

  String _generateEmptySummary(DateTime weekStart, DateTime weekEnd) =>
      '''
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
