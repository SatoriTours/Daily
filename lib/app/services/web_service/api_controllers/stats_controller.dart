import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api_utils/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';

/// 统计控制器 - 提供仪表盘数据
class StatsController {
  /// 创建路由
  Router get router {
    final router = Router();

    // 应用认证中间件
    final pipeline = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    // 概览统计
    router.get('/overview', pipeline.addHandler(_getOverview));

    // 最近活动
    router.get('/recent', pipeline.addHandler(_getRecentActivity));

    // 周报
    router.get('/weekly-report', pipeline.addHandler(_getWeeklyReport));

    return router;
  }

  /// 获取概览统计数据
  Future<Response> _getOverview(Request request) async {
    try {
      // 获取各类数据总数
      final articlesCount = ArticleRepository.i.count();
      final diariesCount = DiaryRepository.i.count();
      final booksCount = BookRepository.i.count();
      final viewpointsCount = BookViewpointRepository.i.count();
      final tagsCount = TagRepository.i.count();

      // 获取收藏文章数
      final favoriteArticlesCount = _getFavoriteArticlesCount();

      // 获取今日新增数据
      final today = DateTime.now();
      final todayStart = DateTime(today.year, today.month, today.day);

      final todayArticles = _countArticlesSince(todayStart);
      final todayDiaries = _countDiariesSince(todayStart);

      // 获取本周新增数据
      final weekStart = todayStart.subtract(Duration(days: today.weekday - 1));
      final weekArticles = _countArticlesSince(weekStart);
      final weekDiaries = _countDiariesSince(weekStart);

      return ResponseUtils.success({
        'totals': {
          'articles': articlesCount,
          'diaries': diariesCount,
          'books': booksCount,
          'viewpoints': viewpointsCount,
          'tags': tagsCount,
          'favoriteArticles': favoriteArticlesCount,
        },
        'today': {'articles': todayArticles, 'diaries': todayDiaries},
        'thisWeek': {'articles': weekArticles, 'diaries': weekDiaries},
      });
    } catch (e) {
      logger.e('获取概览统计失败: $e');
      return ResponseUtils.serverError('获取统计数据时发生错误');
    }
  }

  /// 获取最近活动
  Future<Response> _getRecentActivity(Request request) async {
    try {
      // 获取最近5篇文章
      final recentArticles = ArticleRepository.i.allPaginated(page: 1);
      final articlesJson = recentArticles
          .take(5)
          .map(
            (a) => {
              'id': a.id,
              'type': 'article',
              'title': a.title ?? '',
              'subtitle': a.aiTitle ?? '',
              'createdAt': a.createdAt.toIso8601String(),
            },
          )
          .toList();

      // 获取最近5篇日记
      final recentDiaries = DiaryRepository.i.findAllPaginated(1);
      final diariesJson = recentDiaries.take(5).map((d) {
        final firstLine = d.content.split('\n').first.trim();
        final title = firstLine.length > 50 ? '${firstLine.substring(0, 50)}...' : firstLine;
        return {
          'id': d.id,
          'type': 'diary',
          'title': title.isEmpty ? '日记' : title,
          'content': d.content.length > 100 ? '${d.content.substring(0, 100)}...' : d.content,
          'createdAt': d.createdAt.toIso8601String(),
        };
      }).toList();

      // 获取最近3本书
      final recentBooks = BookRepository.i.allPaginated(page: 1);
      final booksJson = recentBooks
          .take(3)
          .map(
            (b) => {
              'id': b.id,
              'type': 'book',
              'title': b.title,
              'author': b.author,
              'createdAt': b.createdAt.toIso8601String(),
            },
          )
          .toList();

      return ResponseUtils.success({'articles': articlesJson, 'diaries': diariesJson, 'books': booksJson});
    } catch (e) {
      logger.e('获取最近活动失败: $e');
      return ResponseUtils.serverError('获取最近活动时发生错误');
    }
  }

  /// 获取收藏文章数量
  int _getFavoriteArticlesCount() {
    try {
      final articles = ArticleRepository.i.allModels();
      return articles.where((a) => a.isFavorite).length;
    } catch (e) {
      return 0;
    }
  }

  /// 统计指定日期之后的文章数
  int _countArticlesSince(DateTime since) {
    try {
      final articles = ArticleRepository.i.allModels();
      return articles.where((a) => a.createdAt.isAfter(since)).length;
    } catch (e) {
      return 0;
    }
  }

  /// 统计指定日期之后的日记数
  int _countDiariesSince(DateTime since) {
    try {
      final diaries = DiaryRepository.i.all();
      return diaries.where((d) => d.createdAt.isAfter(since)).length;
    } catch (e) {
      return 0;
    }
  }

  /// 获取最新周报
  Future<Response> _getWeeklyReport(Request request) async {
    try {
      // 获取最近一条已完成的周报
      final recentReports = WeeklySummaryRepository.i.findRecent(1);

      if (recentReports.isEmpty) {
        return ResponseUtils.success(null);
      }

      final report = recentReports.first;

      return ResponseUtils.success({
        'id': report.id,
        'content': report.content,
        'weekStart': report.weekStartDate.toIso8601String(),
        'weekEnd': report.weekEndDate.toIso8601String(),
        'articleCount': report.articleCount,
        'diaryCount': report.diaryCount,
        'viewpointCount': report.viewpointCount,
        'status': report.status.value,
        'createdAt': report.createdAt.toIso8601String(),
        'updatedAt': report.updatedAt.toIso8601String(),
      });
    } catch (e) {
      logger.e('获取周报失败: $e');
      return ResponseUtils.serverError('获取周报时发生错误');
    }
  }
}
