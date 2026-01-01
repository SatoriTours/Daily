/// Weekly Summary Controller Provider
///
/// 周报控制器，管理周报页面的状态和交互。

library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/weekly_summary_service.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

part 'weekly_summary_controller_provider.freezed.dart';
part 'weekly_summary_controller_provider.g.dart';

/// WeeklySummaryController 状态
@freezed
abstract class WeeklySummaryControllerState with _$WeeklySummaryControllerState {
  const factory WeeklySummaryControllerState({
    @Default([]) List<WeeklySummaryModel> summaries,
    WeeklySummaryModel? currentSummary,
    @Default(false) bool isGenerating,
    @Default('') String generatingMessage,
    @Default(false) bool isLoading,
  }) = _WeeklySummaryControllerState;
}

/// WeeklySummaryController Provider
@riverpod
class WeeklySummaryController extends _$WeeklySummaryController {
  @override
  WeeklySummaryControllerState build() {
    Future.microtask(() => _initializeAndCheckSummary());
    return const WeeklySummaryControllerState();
  }

  Future<void> _initializeAndCheckSummary() async {
    await _loadSummaries();
    await checkAndGenerate();
  }

  Future<void> refreshSummaries() => _loadSummaries();

  void selectSummary(WeeklySummaryModel summary) => state = state.copyWith(currentSummary: summary);

  Future<void> checkAndGenerate() async {
    if (state.isGenerating) return;
    final needGenerate = await WeeklySummaryService.i.checkAndGenerateSummaries();
    if (needGenerate) await _generateLatestSummary();
  }

  Future<void> regenerateCurrentSummary() async {
    if (state.currentSummary == null || state.isGenerating) return;
    final summary = state.currentSummary!;
    await _generateSummary(summary.weekStartDate, summary.weekEndDate);
  }

  Future<void> _loadSummaries() async {
    state = state.copyWith(isLoading: true);
    try {
      final list = WeeklySummaryService.i.getAllSummaries();
      state = state.copyWith(
        summaries: list,
        currentSummary: (list.isNotEmpty && state.currentSummary == null) ? list.first : state.currentSummary,
        isLoading: false,
      );
    } catch (_) {
      state = state.copyWith(isLoading: false);
    }
  }

  Future<void> _generateLatestSummary() async {
    final weekRange = WeeklySummaryService.i.getLastCompletedWeekRange();
    if (weekRange == null) return;
    final (weekStart, weekEnd) = weekRange;
    await _generateSummary(weekStart, weekEnd);
  }

  Future<void> _generateSummary(DateTime weekStart, DateTime weekEnd) async {
    state = state.copyWith(isGenerating: true, generatingMessage: 'weekly_summary.generating'.t);

    try {
      final result = await WeeklySummaryService.i.generateWeeklySummary(weekStart, weekEnd);

      if (result != null) {
        await _loadSummaries();
        state = state.copyWith(currentSummary: result, isGenerating: false, generatingMessage: '');
        UIUtils.showSuccess('weekly_summary.generate_success'.t);
      } else {
        state = state.copyWith(isGenerating: false, generatingMessage: '');
        UIUtils.showError('weekly_summary.generate_failed'.t);
      }
    } catch (_) {
      state = state.copyWith(isGenerating: false, generatingMessage: '');
      UIUtils.showError('weekly_summary.generate_failed'.t);
    }
  }
}
