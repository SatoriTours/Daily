import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/services/weekly_summary_service.dart';

/// 周报控制器
///
/// 管理周报页面的状态和交互
class WeeklySummaryController extends BaseController {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 周报列表
  final RxList<WeeklySummaryModel> summaries = <WeeklySummaryModel>[].obs;

  /// 当前选中的周报
  final Rx<WeeklySummaryModel?> currentSummary = Rx<WeeklySummaryModel?>(null);

  /// 是否正在生成周报
  final RxBool isGenerating = false.obs;

  /// 生成进度提示
  final RxString generatingMessage = ''.obs;

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void onInit() {
    super.onInit();
    logger.d('[WeeklySummaryController] 初始化');
    _initializeAndCheckSummary();
  }

  /// 初始化并检查周报
  Future<void> _initializeAndCheckSummary() async {
    await _loadSummaries();
    // 自动检查并生成周报
    await checkAndGenerate();
  }

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 刷新周报列表
  Future<void> refreshSummaries() async {
    await _loadSummaries();
  }

  /// 选择周报
  void selectSummary(WeeklySummaryModel summary) {
    logger.i('[WeeklySummaryController] 选择周报: ${summary.weekLabel}');
    currentSummary.value = summary;
  }

  /// 检查并生成周报
  Future<void> checkAndGenerate() async {
    if (isGenerating.value) return;

    logger.i('[WeeklySummaryController] 检查是否需要生成周报');

    final needGenerate = await WeeklySummaryService.i.checkAndGenerateSummaries();
    if (needGenerate) {
      await _generateLatestSummary();
    }
  }

  /// 重新生成当前周报
  Future<void> regenerateCurrentSummary() async {
    if (currentSummary.value == null || isGenerating.value) return;

    logger.i('[WeeklySummaryController] 重新生成周报');
    final summary = currentSummary.value!;
    await _generateSummary(summary.weekStartDate, summary.weekEndDate);
  }

  /// 打开文章详情
  void openArticle(int articleId) {
    logger.i('[WeeklySummaryController] 打开文章: $articleId');
    Get.toNamed(Routes.articleDetail, arguments: articleId);
  }

  /// 打开日记详情
  ///
  /// 获取日记数据，触发 View 显示对话框
  DiaryModel? openDiary(int diaryId) {
    logger.i('[WeeklySummaryController] 打开日记: $diaryId');

    final diary = DiaryRepository.i.find(diaryId);
    if (diary == null) {
      showError('weekly_summary.diary_not_found'.t);
      return null;
    }

    return diary;
  }

  // ========================================================================
  // 私有方法
  // ========================================================================

  /// 加载周报列表
  Future<void> _loadSummaries() async {
    isLoading.value = true;
    try {
      final list = WeeklySummaryService.i.getAllSummaries();
      summaries.value = list;

      // 如果有周报，默认选中最新的
      if (list.isNotEmpty && currentSummary.value == null) {
        currentSummary.value = list.first;
      }

      logger.i('[WeeklySummaryController] 加载了 ${list.length} 个周报');
    } finally {
      isLoading.value = false;
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
    isGenerating.value = true;
    generatingMessage.value = 'weekly_summary.generating'.t;

    try {
      final result = await WeeklySummaryService.i.generateWeeklySummary(weekStart, weekEnd);

      if (result != null) {
        // 刷新列表并选中新生成的周报
        await _loadSummaries();
        currentSummary.value = result;
        showSuccess('weekly_summary.generate_success'.t);
      } else {
        showError('weekly_summary.generate_failed'.t);
      }
    } catch (e) {
      logger.e('[WeeklySummaryController] 生成周报失败', error: e);
      showError('weekly_summary.generate_failed'.t);
    } finally {
      isGenerating.value = false;
      generatingMessage.value = '';
    }
  }
}
