import 'package:daily_satori/app/models/article_model.dart';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/repositories/article_repository.dart' show ArticleStatus;

/// 文章仓储接口
/// 定义文章数据访问的标准接口，便于测试和解耦
abstract class ArticleRepositoryInterface {
  /// 创建文章
  Future<int> create(ArticleModel article);

  /// 更新文章
  Future<bool> update(ArticleModel article);

  /// 删除文章
  Future<bool> delete(int id);

  /// 根据ID查找文章
  Future<ArticleModel?> find(int id);

  /// 根据URL查找文章
  Future<ArticleModel?> findByUrl(String url);

  /// 获取所有文章
  Future<List<ArticleModel>> getAll();

  /// 条件查询文章
  Future<List<ArticleModel>> where({
    String? keyword,
    bool? isFavorite,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
    int? pageSize,
    int? offset,
  });

  /// 更新文章字段
  Future<bool> updateField(int id, String fieldName, dynamic value);

  /// 获取文章总数
  Future<int> count();

  /// 获取每天的文章统计
  Future<Map<DateTime, int>> getDailyArticleCounts();

  /// 获取收藏的文章
  Future<List<ArticleModel>> getFavorites();

  /// 根据标签获取文章
  Future<List<ArticleModel>> getByTags(List<int> tagIds);

  /// 搜索文章
  Future<List<ArticleModel>> search(String query, {int limit = 20});

  /// 分页获取文章
  Future<List<ArticleModel>> getPaged({
    required int page,
    required int pageSize,
    String? sortBy,
    bool ascending = false,
  });

  /// 获取文章状态统计
  Future<Map<String, int>> getStatusCounts();

  /// 标记文章为已读/未读
  Future<bool> markAsRead(int id, bool isRead);

  /// 切换文章收藏状态
  Future<bool> toggleFavorite(int id);

  /// 批量删除文章
  Future<bool> batchDelete(List<int> ids);

  /// 批量更新文章状态
  Future<bool> batchUpdateStatus(List<int> ids, ArticleStatus status);

  /// 获取最近的文章
  Future<List<ArticleModel>> getRecent({int limit = 10});

  /// 获取随机文章
  Future<List<ArticleModel>> getRandom({int limit = 1});

  /// 检查文章是否存在
  Future<bool> exists(int id);

  /// 获取文章创建时间范围
  Future<DateTimeRange?> getDateRange();

  /// 获取文章标签统计
  Future<Map<String, int>> getTagStatistics();
}
