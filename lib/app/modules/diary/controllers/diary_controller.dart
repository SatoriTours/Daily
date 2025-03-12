import 'package:path_provider/path_provider.dart';
import 'dart:io';
import 'package:daily_satori/app_exports.dart';
import 'package:image_picker/image_picker.dart';
import '../utils/diary_utils.dart';

/// 日记列表控制器
class DiaryController extends BaseController with WidgetsBindingObserver {
  /// UI状态
  final isLoading = false.obs;
  final selectedDate = DateTime.now().obs;
  final searchQuery = ''.obs;
  final currentTag = ''.obs;

  /// 日记数据
  final diaries = <DiaryModel>[].obs;

  /// UI控制器
  final scrollController = ScrollController();
  final searchController = TextEditingController();
  final contentController = TextEditingController();
  final tagsController = TextEditingController(); // 添加标签控制器

  // 日记标签列表
  final tags = <String>[].obs;

  // ==== 生命周期方法 ====

  @override
  void onInit() {
    super.onInit();
    _loadDiaries();
    _extractTags();
    _createImageDirectory(); // 创建图片目录
  }

  @override
  void onClose() {
    scrollController.dispose();
    searchController.dispose();
    contentController.dispose();
    tagsController.dispose();
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

    isLoading.value = true;

    final diary = DiaryModel(content: content, tags: tags, mood: mood, images: images);

    DiaryRepository.i.save(diary);

    // 重新加载日记列表
    await _loadDiaries();

    // 清空输入框
    contentController.clear();

    isLoading.value = false;
  }

  /// 删除日记
  Future<void> deleteDiary(int id) async {
    isLoading.value = true;

    // 获取日记以删除相关图片
    final diary = DiaryRepository.i.getById(id);
    if (diary != null && diary.images != null && diary.images!.isNotEmpty) {
      _deleteImages(diary.images!.split(','));
    }

    DiaryRepository.i.delete(id);

    // 重新加载日记列表
    await _loadDiaries();

    isLoading.value = false;
  }

  /// 更新日记
  Future<void> updateDiary(DiaryModel diary) async {
    isLoading.value = true;

    // 获取原日记以比较图片变化
    final oldDiary = DiaryRepository.i.getById(diary.id);
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

    // 重新加载日记列表
    await _loadDiaries();

    isLoading.value = false;
  }

  /// 按标签筛选
  void filterByTag(String tag) {
    currentTag.value = tag;
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
    _loadDiaries();
  }

  /// 启用/禁用搜索
  void enableSearch(bool enable) {
    // 判断当前是否在搜索模式下（已有搜索内容或正在输入）
    final bool isSearchMode = searchQuery.isNotEmpty || searchController.text.isNotEmpty;

    if (isSearchMode) {
      // 如果是搜索模式且要关闭搜索，则清除搜索结果
      if (!enable) {
        clearFilters();
      }
    } else {
      // 如果不是搜索模式且要开启搜索，则准备搜索状态
      if (enable) {
        searchController.clear();
        // 不立即设置searchQuery，等待用户输入并提交
      }
    }
  }

  /// 获取图片保存路径
  Future<String> getImageSavePath() async {
    final directory = await getApplicationDocumentsDirectory();
    final path = '${directory.path}/diary_images';

    final dir = Directory(path);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }

    return path;
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
      final updatedDiary = DiaryModel(
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
    isLoading.value = true;

    final allDiaries = DiaryRepository.i.getAll();

    // 应用过滤条件
    if (searchQuery.isNotEmpty) {
      // 按内容搜索
      diaries.value =
          allDiaries.where((d) => d.content.toLowerCase().contains(searchQuery.value.toLowerCase())).toList();
    } else if (currentTag.isNotEmpty) {
      // 按标签筛选
      diaries.value =
          allDiaries
              .where((d) => d.tags != null && d.tags!.toLowerCase().contains(currentTag.value.toLowerCase()))
              .toList();
    } else {
      // 全部加载
      diaries.value = allDiaries;
    }

    // 排序：最新的在前面
    diaries.value.sort((a, b) => b.createdAt.compareTo(a.createdAt));

    isLoading.value = false;

    // 提取所有标签
    _extractTags();
  }

  /// 提取所有标签
  void _extractTags() {
    tags.clear();

    final Set<String> tagSet = {};

    for (var diary in DiaryRepository.i.getAll()) {
      if (diary.tags != null && diary.tags!.isNotEmpty) {
        final diaryTags = diary.tags!.split(',').map((t) => t.trim()).where((t) => t.isNotEmpty);
        tagSet.addAll(diaryTags);
      }
    }

    tags.value = tagSet.toList()..sort();
  }

  Future<void> _handleAppResume() async {
    logger.i('检查剪切板内容');
    await ClipboardUtils.checkAndNavigateToShareDialog();
  }
}
