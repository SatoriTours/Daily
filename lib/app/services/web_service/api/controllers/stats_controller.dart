import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api/middleware/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';

/// 统计 API 控制器（仪表盘数据）
class StatsController {
  Router get router {
    final router = Router();

    final authed = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    router.get('/overview', authed.addHandler(_getOverview));
    router.get('/recent', authed.addHandler(_getRecentActivity));
    router.get('/weekly-report', authed.addHandler(_getWeeklyReport));

    return router;
  }

  Future<Response> _getOverview(Request request) async {
    try {
      final articlesCount = ArticleRepository.i.count();
      final diariesCount = DiaryRepository.i.count();
      final booksCount = BookRepository.i.count();
      final viewpointsCount = BookViewpointRepository.i.count();
      final tagsCount = TagRepository.i.count();

      final favoriteArticlesCount = _getFavoriteArticlesCount();

      final today = DateTime.now();
      final todayStart = DateTime(today.year, today.month, today.day);

      final todayArticles = _countArticlesSince(todayStart);
      final todayDiaries = _countDiariesSince(todayStart);

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
      logger.e('[WebService][Stats] 获取概览统计失败', error: e);
      return ResponseUtils.serverError('获取统计数据时发生错误');
    }
  }

  Future<Response> _getRecentActivity(Request request) async {
    try {
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

      final recentDiaries = DiaryRepository.i.findAllPaginated(1);
      final diariesJson = recentDiaries.take(5).map((d) {
        final firstLine = d.content.split('\n').first.trim();
        final title = firstLine.length > 50
            ? '${firstLine.substring(0, 50)}...'
            : firstLine;
        return {
          'id': d.id,
          'type': 'diary',
          'title': title.isEmpty ? '日记' : title,
          'content': d.content.length > 100
              ? '${d.content.substring(0, 100)}...'
              : d.content,
          'createdAt': d.createdAt.toIso8601String(),
        };
      }).toList();

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

      return ResponseUtils.success({
        'articles': articlesJson,
        'diaries': diariesJson,
        'books': booksJson,
      });
    } catch (e) {
      logger.e('[WebService][Stats] 获取最近活动失败', error: e);
      return ResponseUtils.serverError('获取最近活动时发生错误');
    }
  }

  Future<Response> _getWeeklyReport(Request request) async {
    try {
      final recentReports = WeeklySummaryRepository.i.findRecent(5);
      if (recentReports.isEmpty) return ResponseUtils.success([]);

      final reportList = recentReports
          .map(
            (report) => {
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
            },
          )
          .toList();

      return ResponseUtils.success(reportList);
    } catch (e) {
      logger.e('[WebService][Stats] 获取周报失败', error: e);
      return ResponseUtils.serverError('获取周报时发生错误');
    }
  }

  int _getFavoriteArticlesCount() {
    try {
      final articles = ArticleRepository.i.allModels();
      return articles.where((a) => a.isFavorite).length;
    } catch (_) {
      return 0;
    }
  }

  int _countArticlesSince(DateTime since) {
    try {
      final articles = ArticleRepository.i.allModels();
      return articles.where((a) => a.createdAt.isAfter(since)).length;
    } catch (_) {
      return 0;
    }
  }

  int _countDiariesSince(DateTime since) {
    try {
      final diaries = DiaryRepository.i.all();
      return diaries.where((d) => d.createdAt.isAfter(since)).length;
    } catch (_) {
      return 0;
    }
  }
}
