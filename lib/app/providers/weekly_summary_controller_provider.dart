/// Weekly Summary Controller Provider
///
/// 周报控制器，管理周报页面的状态和交互。

library;

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/weekly_summary_service.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

part 'weekly_summary_controller_provider.freezed.dart';
part 'weekly_summary_controller_provider.g.dart';

/// WeeklySummaryController 状态
@freezed
abstract class WeeklySummaryControllerState with _$WeeklySummaryControllerState {
  const factory WeeklySummaryControllerState({
    /// 周报列表
    @Default([]) List<WeeklySummaryModel> summaries,

    /// 当前选中的周报
    WeeklySummaryModel? currentSummary,

    /// 是否正在生成周报
    @Default(false) bool isGenerating,

    /// 生成进度提示
    @Default('') String generatingMessage,

    /// 是否正在加载
    @Default(false) bool isLoading,
  }) = _WeeklySummaryControllerState;
}

/// WeeklySummaryController Provider
@riverpod
class WeeklySummaryController extends _$WeeklySummaryController {
  @override
  WeeklySummaryControllerState build() {
    _initializeAndCheckSummary();
    return const WeeklySummaryControllerState();
  }

  /// 初始化并检查周报
  Future<void> _initializeAndCheckSummary() async {
    await _loadSummaries();
    await checkAndGenerate();
  }

  /// 刷新周报列表
  Future<void> refreshSummaries() async {
    await _loadSummaries();
  }

  /// 选择周报
  void selectSummary(WeeklySummaryModel summary) {
    logger.i('[WeeklySummaryController] 选择周报: ${summary.weekLabel}');
    state = state.copyWith(currentSummary: summary);
  }

  /// 检查并生成周报
  Future<void> checkAndGenerate() async {
    if (state.isGenerating) return;

    logger.i('[WeeklySummaryController] 检查是否需要生成周报');

    final needGenerate = await WeeklySummaryService.i.checkAndGenerateSummaries();
    if (needGenerate) {
      await _generateLatestSummary();
    }
  }

  /// 重新生成当前周报
  Future<void> regenerateCurrentSummary() async {
    if (state.currentSummary == null || state.isGenerating) return;

    logger.i('[WeeklySummaryController] 重新生成周报');
    final summary = state.currentSummary!;
    await _generateSummary(summary.weekStartDate, summary.weekEndDate);
  }

  /// 加载周报列表
  Future<void> _loadSummaries() async {
    state = state.copyWith(isLoading: true);
    try {
      final list = WeeklySummaryService.i.getAllSummaries();
      state = state.copyWith(
        summaries: list,
        currentSummary: (list.isNotEmpty && state.currentSummary == null) ? list.first : state.currentSummary,
        isLoading: false,
      );
      logger.i('[WeeklySummaryController] 加载了 ${list.length} 个周报');
    } catch (e) {
      state = state.copyWith(isLoading: false);
      logger.e('[WeeklySummaryController] 加载周报失败: $e');
    }
  }

  /// 生成最新的周报
  Future<void> _generateLatestSummary() async {
    final weekRange = WeeklySummaryService.i.getLastCompletedWeekRange();
    if (weekRange == null) return;

    final (weekStart, weekEnd) = weekRange;
    await _generateSummary(weekStart, weekEnd);
  }

  /// 生成周报
  Future<void> _generateSummary(DateTime weekStart, DateTime weekEnd) async {
    state = state.copyWith(isGenerating: true, generatingMessage: 'weekly_summary.generating'.t);

    try {
      final result = await WeeklySummaryService.i.generateWeeklySummary(weekStart, weekEnd);

      if (result != null) {
        // 刷新列表并选中新生成的周报
        await _loadSummaries();
        state = state.copyWith(currentSummary: result, isGenerating: false, generatingMessage: '');
        UIUtils.showSuccess('weekly_summary.generate_success'.t);
      } else {
        state = state.copyWith(isGenerating: false, generatingMessage: '');
        UIUtils.showError('weekly_summary.generate_failed'.t);
      }
    } catch (e) {
      logger.e('[WeeklySummaryController] 生成周报失败', error: e);
      state = state.copyWith(isGenerating: false, generatingMessage: '');
      UIUtils.showError('weekly_summary.generate_failed'.t);
    }
  }
}
