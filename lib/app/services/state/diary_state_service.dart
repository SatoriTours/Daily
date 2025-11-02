import 'package:daily_satori/app_exports.dart';

/// 全局日记状态管理服务
///
/// 负责管理日记相关的全局状态和数据，包括：
/// - 日记列表数据缓存
/// - 当前选中日记
/// - 标签和日期过滤状态
/// - 避免控制器之间的紧耦合
class DiaryStateService extends GetxService {
  // ===== 数据层（唯一数据源） =====

  /// 日记列表数据
  final RxList<DiaryModel> diaries = <DiaryModel>[].obs;

  /// 加载状态
  final RxBool isLoading = false.obs;

  // ===== 当前活跃日记 =====

  /// 当前活跃的日记ID
  final RxInt _activeDiaryId = RxInt(-1);

  /// 当前活跃的日记引用
  final Rxn<DiaryModel> _activeDiary = Rxn<DiaryModel>();

  // ===== 过滤状态 =====

  /// 全局标签过滤状态
  final RxString globalTagFilter = ''.obs;

  /// 全局日期过滤状态
  final Rx<DateTime?> globalDateFilter = Rx<DateTime?>(null);

  /// 日记更新通知流
  final RxMap<int, DiaryModel> diaryUpdates = <int, DiaryModel>{}.obs;

  // ===== Getters =====

  /// 获取当前活跃的日记ID
  int get activeDiaryId => _activeDiaryId.value;

  /// 获取当前活跃的日记
  DiaryModel? get activeDiary => _activeDiary.value;

  // ===== 数据操作方法 =====

  /// 加载日记列表
  Future<void> loadDiaries({String? keyword, String? tag, DateTime? date}) async {
    isLoading.value = true;
    try {
      logger.i('加载日记列表: keyword=$keyword, tag=$tag, date=$date');

      List<DiaryModel> result;

      if (keyword != null && keyword.isNotEmpty) {
        // 按关键词搜索
        result = DiaryRepository.i.findByContent(keyword);
      } else if (tag != null && tag.isNotEmpty) {
        // 按标签过滤
        result = DiaryRepository.i.findByTag(tag);
      } else if (date != null) {
        // 按日期过滤
        result = DiaryRepository.i.findByCreatedDate(date);
      } else {
        // 获取所有日记
        result = DiaryRepository.i.findAll();
      }

      diaries.assignAll(result);
      logger.d('日记列表加载完成: ${diaries.length}条');
    } catch (e) {
      logger.e('加载日记列表失败', error: e);
    } finally {
      isLoading.value = false;
    }
  }

  /// 更新列表中的日记
  void updateDiaryInList(int id) {
    final diary = DiaryRepository.i.find(id);
    if (diary == null) return;

    final index = diaries.indexWhere((d) => d.id == id);
    if (index != -1) {
      diaries[index] = diary;
    }
    logger.d('更新列表中的日记: ID=$id');
  }

  /// 从列表中移除日记
  void removeDiaryFromList(int id) {
    diaries.removeWhere((item) => item.id == id);
    logger.d('从列表移除日记: ID=$id');
  }

  /// 添加日记到列表
  void addDiaryToList(DiaryModel diary) {
    diaries.insert(0, diary); // 添加到列表开头
    logger.d('添加日记到列表: ID=${diary.id}');
  }

  /// 获取日记引用
  DiaryModel? getDiaryRef(int id) {
    final index = diaries.indexWhere((item) => item.id == id);
    if (index != -1) {
      setActiveDiary(diaries[index]);
      return diaries[index];
    }
    return DiaryRepository.i.find(id);
  }

  // ===== 活跃日记管理 =====

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
