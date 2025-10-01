import 'package:daily_satori/app_exports.dart';

/// 全局日记状态管理服务
///
/// 负责管理日记相关的全局状态，包括当前选中日记、
/// 标签过滤状态等，避免控制器之间的紧耦合
class DiaryStateService extends GetxService {
  /// 当前活跃的日记ID
  final RxInt _activeDiaryId = RxInt(-1);

  /// 当前活跃的日记引用
  final Rxn<DiaryModel> _activeDiary = Rxn<DiaryModel>();

  /// 全局标签过滤状态
  final RxString globalTagFilter = ''.obs;

  /// 全局日期过滤状态
  final Rx<DateTime?> globalDateFilter = Rx<DateTime?>(null);

  /// 日记更新通知流
  final RxMap<int, DiaryModel> diaryUpdates = RxMap<int, DiaryModel>();

  /// 获取当前活跃的日记ID
  int get activeDiaryId => _activeDiaryId.value;

  /// 获取当前活跃的日记
  DiaryModel? get activeDiary => _activeDiary.value;

  /// 设置活跃日记
  void setActiveDiary(DiaryModel diary) {
    _activeDiaryId.value = diary.id;
    _activeDiary.value = diary;
    logger.i('设置活跃日记: ${diary.content.substring(0, 20)}... (ID: ${diary.id})');
  }

  /// 清除活跃日记
  void clearActiveDiary() {
    _activeDiaryId.value = -1;
    _activeDiary.value = null;
    logger.i('清除活跃日记');
  }

  /// 设置全局标签过滤
  void setGlobalTagFilter(String tag) {
    globalTagFilter.value = tag;
    logger.i('设置全局标签过滤: $tag');
  }

  /// 清除全局标签过滤
  void clearGlobalTagFilter() {
    globalTagFilter.value = '';
    logger.i('清除全局标签过滤');
  }

  /// 设置全局日期过滤
  void setGlobalDateFilter(DateTime? date) {
    globalDateFilter.value = date != null ? DateTime(date.year, date.month, date.day) : null;
    if (date != null) {
      logger.i('设置全局日期过滤: ${date.year}-${date.month}-${date.day}');
    } else {
      logger.i('清除全局日期过滤');
    }
  }

  /// 清除所有过滤条件
  void clearAllFilters() {
    globalTagFilter.value = '';
    globalDateFilter.value = null;
    logger.i('清除所有过滤条件');
  }

  /// 通知日记更新
  void notifyDiaryUpdated(DiaryModel diary) {
    diaryUpdates[diary.id] = diary;
    logger.i('通知日记更新: ID ${diary.id}');

    // 如果是当前活跃日记，更新活跃日记引用
    if (_activeDiaryId.value == diary.id) {
      _activeDiary.value = diary;
    }
  }

  /// 获取日记更新通知
  DiaryModel? getDiaryUpdate(int diaryId) {
    return diaryUpdates.remove(diaryId);
  }

  /// 监听特定日记的更新
  void listenDiaryUpdates(int diaryId, Function(DiaryModel) onUpdate) {
    ever<Map<int, DiaryModel>>(diaryUpdates, (updates) {
      if (updates.containsKey(diaryId)) {
        onUpdate(updates[diaryId]!);
      }
    });
  }

  @override
  void onInit() {
    super.onInit();
    logger.i('DiaryStateService 初始化完成');
  }

  @override
  void onClose() {
    diaryUpdates.clear();
    _activeDiaryId.close();
    _activeDiary.close();
    globalTagFilter.close();
    globalDateFilter.close();
    super.onClose();
    logger.i('DiaryStateService 已关闭');
  }
}