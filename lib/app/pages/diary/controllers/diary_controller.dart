import 'dart:io';
import 'package:daily_satori/app_exports.dart';
import 'package:image_picker/image_picker.dart';
import '../utils/diary_utils.dart';

/// 日记列表控制器
class DiaryController extends BaseController with WidgetsBindingObserver {
  // ========== 构造函数 ==========
  DiaryController(super._appStateService, this._diaryStateService);

  /// UI状态
  final selectedDate = DateTime.now().obs;
  final searchQuery = ''.obs;
  final isSearchVisible = false.obs;
  final selectedFilterDate = Rx<DateTime?>(null); // 添加日期过滤状态
  final currentTag = ''.obs;

  /// 状态服务
  final DiaryStateService _diaryStateService;

  /// 日记数据 - 引用自StateService
  RxList<DiaryModel> get diaries => _diaryStateService.diaries;
  RxBool get isLoadingDiaries => _diaryStateService.isLoading;

  /// UI控制器
  final scrollController = ScrollController();
  final searchController = TextEditingController();
  final contentController = TextEditingController();
  final tagsController = TextEditingController(); // 添加标签控制器
  final searchFocusNode = FocusNode(); // 添加搜索焦点节点

  // 日记标签列表
  final tags = <String>[].obs;

  // ==== 生命周期方法 ====

  @override
  void onInit() {
    super.onInit();
    _initDiaryListeners();
    _loadDiaries();
    _extractTags();
    _createImageDirectory(); // 创建图片目录

    // 添加搜索控制器监听
    searchController.addListener(_onSearchTextChanged);
  }

  /// 初始化日记相关监听器
  void _initDiaryListeners() {
    // 监听全局标签过滤
    ever(_diaryStateService.globalTagFilter, (tag) {
      if (tag.isNotEmpty) {
        currentTag.value = tag;
        _loadDiaries();
      }
    });

    // 监听全局日期过滤
    ever(_diaryStateService.globalDateFilter, (date) {
      selectedFilterDate.value = date;
      _loadDiaries();
    });

    // 监听日记更新
    everAll([_diaryStateService.diaryUpdates], (_) {
      _refreshDiaryUpdates();
    });
  }

  void _refreshDiaryUpdates() {
    // 检查是否有需要更新的日记
    for (final diaryId in _diaryStateService.diaryUpdates.keys) {
      _diaryStateService.updateDiaryInList(diaryId);
    }
  }

  /// 搜索文本变化处理
  void _onSearchTextChanged() {
    // 当用户在搜索框中输入文本但尚未提交搜索时
    // 这里不触发实际搜索，只确保UI状态一致
    if (isSearchVisible.value && searchController.text.isEmpty && searchQuery.isNotEmpty) {
      // 如果搜索框可见，但文本被清空，且之前有搜索内容
      // 则清空搜索结果
      searchQuery.value = '';
      _loadDiaries();
    }
  }

  @override
  void onClose() {
    searchController.removeListener(_onSearchTextChanged);
    scrollController.dispose();
    searchController.dispose();
    contentController.dispose();
    tagsController.dispose();
    searchFocusNode.dispose(); // 释放焦点节点
    super.onClose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed) {
      await _handleAppResume();
    }
  }

  // ==== 公共方法 ====

  /// 加载日记列表
  Future<void> loadDiaries() async {
    _loadDiaries();
  }

  /// 创建新日记
  Future<void> createDiary(String content, {String? tags, String? mood, String? images}) async {
    if (content.trim().isEmpty) return;

    // 确保键盘隐藏
    FocusManager.instance.primaryFocus?.unfocus();

    isLoading.value = true;

    final diary = DiaryModel.create(content: content, tags: tags, mood: mood, images: images);
    final id = DiaryRepository.i.save(diary);

    // 添加到StateService列表
    final savedDiary = DiaryRepository.i.find(id);
    if (savedDiary != null) {
      _diaryStateService.addDiaryToList(savedDiary);
    }

    // 清空输入框
    contentController.clear();

    isLoading.value = false;
  }

  /// 删除日记
  Future<void> deleteDiary(int id) async {
    isLoading.value = true;

    // 获取日记以删除相关图片
    final diary = DiaryRepository.i.find(id);
    if (diary != null && diary.images != null && diary.images!.isNotEmpty) {
      _deleteImages(diary.images!.split(','));
    }

    DiaryRepository.i.delete(id);

    // 从StateService列表移除
    _diaryStateService.removeDiaryFromList(id);

    isLoading.value = false;
  }

  /// 更新日记
  Future<void> updateDiary(DiaryModel diary) async {
    // 确保键盘隐藏
    FocusManager.instance.primaryFocus?.unfocus();

    isLoading.value = true;

    // 获取原日记以比较图片变化
    final oldDiary = DiaryRepository.i.find(diary.id);
    if (oldDiary != null && oldDiary.images != null && diary.images != oldDiary.images) {
      // 如果图片发生变化，删除不再使用的图片
      final oldImages = oldDiary.images!.split(',');
      final newImages = diary.images != null ? diary.images!.split(',') : <String>[];

      final imagesToDelete = oldImages.where((img) => !newImages.contains(img)).toList();
      if (imagesToDelete.isNotEmpty) {
        _deleteImages(imagesToDelete);
      }
    }

    // 更新修改时间
    diary.updatedAt = DateTime.now();
    DiaryRepository.i.save(diary);

    // 通知全局状态服务日记已更新
    _diaryStateService.notifyDiaryUpdated(diary);

    // 更新StateService列表中的日记
    _diaryStateService.updateDiaryInList(diary.entity.id);

    isLoading.value = false;
  }

  /// 按标签筛选
  void filterByTag(String tag) {
    currentTag.value = tag;
    // 设置全局标签过滤
    _diaryStateService.setGlobalTagFilter(tag);
    _loadDiaries();
  }

  /// 按日期筛选
  void filterByDate(DateTime date) {
    // 设置全局日期过滤
    _diaryStateService.setGlobalDateFilter(date);
    _loadDiaries();
  }

  /// 按内容搜索
  void search(String query) {
    if (query.trim().isEmpty) {
      // 如果搜索词为空，清除搜索状态
      clearFilters();
      return;
    }

    // 清除标签筛选
    currentTag.value = '';

    // 设置搜索词并重新加载日记
    searchQuery.value = query.trim();
    _loadDiaries();
  }

  /// 清除筛选条件
  void clearFilters() {
    currentTag.value = '';
    searchQuery.value = '';
    searchController.clear();
    isSearchVisible.value = false;
    // 清除全局过滤状态
    _diaryStateService.clearAllFilters();
    _loadDiaries();
  }

  /// 启用/禁用搜索
  void enableSearch(bool enable) {
    // 设置搜索栏的可见状态
    isSearchVisible.value = enable;

    if (enable) {
      // 如果要开启搜索，清空文本并准备接收输入
      searchController.clear();
      // 延迟一下再激活焦点，确保UI已经构建完成
      Future.delayed(const Duration(milliseconds: 100), () {
        searchFocusNode.requestFocus();
      });
    } else {
      // 如果是关闭搜索，并且当前有搜索结果，则清除
      if (searchQuery.isNotEmpty || searchController.text.isNotEmpty) {
        clearFilters();
      }
    }
  }

  /// 获取图片保存路径
  Future<String> getImageSavePath() async {
    return FileService.i.diaryImagesBasePath;
  }

  /// 选择并保存图片
  Future<void> pickAndSaveImages(
    BuildContext context,
    Function(void Function()) setState,
    List<String> imagesList,
  ) async {
    final picker = ImagePicker();
    final pickedImages = await picker.pickMultiImage();

    if (pickedImages.isNotEmpty) {
      // 保存图片并获取路径
      List<String> newImagePaths = [];
      final String dirPath = await getImageSavePath();

      for (int i = 0; i < pickedImages.length; i++) {
        final XFile image = pickedImages[i];
        final String fileName = 'diary_img_${DateTime.now().millisecondsSinceEpoch}_$i.jpg';
        final String filePath = '$dirPath/$fileName';

        // 复制图片到应用目录
        final File savedImage = File(filePath);
        await savedImage.writeAsBytes(await image.readAsBytes());

        newImagePaths.add(filePath);
      }

      setState(() {
        imagesList.addAll(newImagePaths);
      });
    }
  }

  /// 更新日记并处理图片
  Future<bool> updateDiaryWithImages(
    BuildContext context,
    DiaryModel diary,
    TextEditingController contentController,
    List<String> currentImages,
    List<String> imagesToDelete,
  ) async {
    if (contentController.text.trim().isNotEmpty) {
      // 删除被标记的图片
      if (imagesToDelete.isNotEmpty) {
        await _deleteImages(imagesToDelete);
      }

      // 从内容中提取标签
      final String tags = DiaryUtils.extractTags(contentController.text);

      // 创建更新后的日记
      final updatedDiary = DiaryModel.create(
        id: diary.id,
        content: contentController.text,
        tags: tags,
        mood: diary.mood,
        images: currentImages.isEmpty ? null : currentImages.join(','),
        createdAt: diary.createdAt,
      );

      // 调用原有的updateDiary方法
      await updateDiary(updatedDiary);

      // 返回成功结果，而不是直接操作导航
      return true;
    }
    return false;
  }

  /// 获取每日日记数量统计
  Map<DateTime, int> getDailyDiaryCounts() {
    final Map<DateTime, int> counts = {};
    final allDiaries = DiaryRepository.i.findAll();

    for (final diary in allDiaries) {
      // 只考虑年月日，忽略时分秒
      final dateKey = DateTime(diary.createdAt.year, diary.createdAt.month, diary.createdAt.day);

      if (counts.containsKey(dateKey)) {
        counts[dateKey] = counts[dateKey]! + 1;
      } else {
        counts[dateKey] = 1;
      }
    }

    return counts;
  }

  // ==== 私有方法 ====

  /// 创建图片目录
  Future<void> _createImageDirectory() async {
    await getImageSavePath();
  }

  /// 删除图片文件
  Future<void> _deleteImages(List<String> imagePaths) async {
    for (final path in imagePaths) {
      final file = File(path);
      if (await file.exists()) {
        await file.delete();
      }
    }
  }

  /// 加载日记列表
  Future<void> _loadDiaries() async {
    // 准备过滤参数
    String? keyword;
    String? tag;
    DateTime? date;

    if (searchQuery.isNotEmpty) {
      keyword = searchQuery.value;
    } else if (_diaryStateService.globalTagFilter.isNotEmpty) {
      tag = _diaryStateService.globalTagFilter.value;
    } else if (currentTag.isNotEmpty) {
      tag = currentTag.value;
    } else if (_diaryStateService.globalDateFilter.value != null) {
      date = _diaryStateService.globalDateFilter.value;
    } else if (selectedFilterDate.value != null) {
      date = selectedFilterDate.value;
    }

    // 调用StateService加载数据
    await _diaryStateService.loadDiaries(keyword: keyword, tag: tag, date: date);

    // 提取所有标签
    _extractTags();
  }

  /// 提取所有标签
  void _extractTags() {
    tags.clear();

    final Set<String> tagSet = {};

    for (var diary in DiaryRepository.i.findAll()) {
      if (diary.tags != null && diary.tags!.isNotEmpty) {
        final diaryTags = diary.tags!.split(',').map((t) => t.trim()).where((t) => t.isNotEmpty);
        tagSet.addAll(diaryTags);
      }
    }

    tags.value = tagSet.toList()..sort();
  }

  Future<void> _handleAppResume() async {
    // 剪贴板检查由 ClipboardMonitorService 在应用层统一处理
  }
}
