import 'package:daily_satori/app/models/diary_model.dart';
import 'package:flutter/material.dart';

/// 日记仓储接口
/// 定义日记数据访问的标准接口
abstract class DiaryRepositoryInterface {
  /// 创建日记
  Future<int> create(DiaryModel diary);

  /// 更新日记
  Future<bool> update(DiaryModel diary);

  /// 删除日记
  Future<bool> delete(int id);

  /// 根据ID查找日记
  Future<DiaryModel?> find(int id);

  /// 获取所有日记
  Future<List<DiaryModel>> getAll();

  /// 条件查询日记
  Future<List<DiaryModel>> where({
    String? keyword,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int? pageSize,
    int? offset,
  });

  /// 获取某天的日记
  Future<List<DiaryModel>> getByDate(DateTime date);

  /// 获取某月的日记
  Future<List<DiaryModel>> getByMonth(DateTime date);

  /// 获取某年的日记
  Future<List<DiaryModel>> getByYear(int year);

  /// 获取日记日期范围
  Future<DateTimeRange?> getDateRange();

  /// 获取有日记的日期列表
  Future<List<DateTime>> getDatesWithEntries();

  /// 搜索日记
  Future<List<DiaryModel>> search(String query, {int limit = 20});

  /// 获取日记总数
  Future<int> count();

  /// 获取最近的日记
  Future<List<DiaryModel>> getRecent({int limit = 10});

  /// 根据标签获取日记
  Future<List<DiaryModel>> getByTags(List<int> tagIds);

  /// 获取标签统计
  Future<Map<String, int>> getTagStatistics();

  /// 获取每日日记统计
  Future<Map<DateTime, int>> getDailyDiaryCounts();

  /// 获取每月日记统计
  Future<Map<DateTime, int>> getMonthlyDiaryCounts();

  /// 批量删除日记
  Future<bool> batchDelete(List<int> ids);

  /// 批量更新日记标签
  Future<bool> batchUpdateTags(List<int> ids, List<int> tagIds);

  /// 获取导出数据
  Future<List<Map<String, dynamic>>> getExportData();

  /// 导入数据
  Future<bool> importData(List<Map<String, dynamic>> data);
}
