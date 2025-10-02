import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';

/// 图片选择测试辅助类
class ImagePickerTestHelper {
  static XFile? _mockImageFile;

  /// 设置模拟图片文件
  static void setMockImageFile(XFile? file) {
    _mockImageFile = file;
  }

  /// 创建模拟图片文件
  static XFile createMockImageFile() {
    return XFile('/mock/path/test_image.jpg');
  }

  /// 模拟从相册选择图片
  static Future<XFile?> mockPickFromGallery() async {
    // 模拟用户选择图片的延迟
    await Future.delayed(const Duration(milliseconds: 500));
    return _mockImageFile;
  }

  /// 模拟拍照
  static Future<XFile?> mockPickFromCamera() async {
    // 模拟拍照的延迟
    await Future.delayed(const Duration(milliseconds: 1000));
    return _mockImageFile;
  }

  /// 模拟选择多张图片
  static Future<List<XFile>> mockPickMultipleImages() async {
    await Future.delayed(const Duration(milliseconds: 800));
    if (_mockImageFile != null) {
      return [_mockImageFile!];
    }
    return [];
  }

  /// 在测试中设置图片选择行为
  static void setupImagePickerMocking() {
    // 这里可以设置实际的 mock 逻辑
    // 由于 image_picker 不支持直接 mock，这个文件主要提供测试工具方法
  }

  /// 验证图片选择对话框出现
  static void verifyImagePickerDialog(WidgetTester tester) {
    expect(find.byKey(const Key('image_picker_dialog')), findsOneWidget);
  }

  /// 验证相册选择按钮存在
  static void verifyGalleryButton(WidgetTester tester) {
    expect(find.byKey(const Key('select_image_from_gallery')), findsOneWidget);
  }

  /// 验证相机按钮存在
  static void verifyCameraButton(WidgetTester tester) {
    expect(find.byKey(const Key('select_image_from_camera')), findsOneWidget);
  }

  /// 模拟点击相册选择
  static Future<void> tapGallerySelection(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('select_image_from_gallery')));
    await tester.pumpAndSettle();
  }

  /// 模拟点击相机选择
  static Future<void> tapCameraSelection(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('select_image_from_camera')));
    await tester.pumpAndSettle();
  }

  /// 验证图片预览存在
  static void verifyImagePreview(WidgetTester tester) {
    expect(find.byKey(const Key('image_preview')), findsAtLeastNWidgets(1));
  }

  /// 验证图片已添加标记
  static void verifyImageAdded(WidgetTester tester) {
    expect(find.byKey(const Key('image_added_indicator')), findsOneWidget);
  }

  /// 模拟删除图片
  static Future<void> deleteImage(WidgetTester tester, int imageIndex) async {
    final deleteButtonFinder = find.byKey(Key('delete_image_$imageIndex'));
    if (deleteButtonFinder.evaluate().isNotEmpty) {
      await tester.tap(deleteButtonFinder);
      await tester.pumpAndSettle();
    }
  }

  /// 验证图片数量
  static void verifyImageCount(WidgetTester tester, int expectedCount) {
    expect(find.byKey(const Key('diary_image_item')), findsNWidgets(expectedCount));
  }
}

/// 图片选择测试用例集合
class ImagePickerTestCases {
  /// 测试完整的图片选择流程
  static Future<void> testCompleteImageSelectionFlow(WidgetTester tester) async {
    // 1. 点击添加图片按钮
    await tester.tap(find.byKey(const Key('add_image_button')));
    await tester.pumpAndSettle();

    // 2. 验证图片选择对话框出现
    ImagePickerTestHelper.verifyImagePickerDialog(tester);

    // 3. 验证选择选项存在
    ImagePickerTestHelper.verifyGalleryButton(tester);
    ImagePickerTestHelper.verifyCameraButton(tester);

    // 4. 模拟选择相册
    await ImagePickerTestHelper.tapGallerySelection(tester);
    await tester.pumpAndSettle();

    // 5. 验证图片加载完成（在实际测试中需要mock）
    await tester.pumpAndSettle(const Duration(seconds: 2));

    // 6. 验证图片预览
    ImagePickerTestHelper.verifyImagePreview(tester);
  }

  /// 测试多图片选择
  static Future<void> testMultipleImageSelection(WidgetTester tester) async {
    // 1. 添加第一张图片
    await testCompleteImageSelectionFlow(tester);

    // 2. 再次点击添加图片
    await tester.tap(find.byKey(const Key('add_image_button')));
    await tester.pumpAndSettle();

    // 3. 选择第二张图片
    await ImagePickerTestHelper.tapGallerySelection(tester);
    await tester.pumpAndSettle();

    // 4. 验证有两张图片
    ImagePickerTestHelper.verifyImageCount(tester, 2);
  }

  /// 测试删除图片
  static Future<void> testImageDeletion(WidgetTester tester) async {
    // 1. 添加图片
    await testCompleteImageSelectionFlow(tester);

    // 2. 删除图片
    await ImagePickerTestHelper.deleteImage(tester, 0);
    await tester.pumpAndSettle();

    // 3. 验证图片被删除
    ImagePickerTestHelper.verifyImageCount(tester, 0);
  }

  /// 测试图片保存
  static Future<void> testImageSaving(WidgetTester tester) async {
    // 1. 添加图片到日记
    await testCompleteImageSelectionFlow(tester);

    // 2. 保存日记
    await tester.tap(find.byKey(const Key('save_diary_button')));
    await tester.pumpAndSettle();

    // 3. 验证保存成功
    expect(find.byKey(const Key('diary_saved_success')), findsOneWidget);

    // 4. 验证图片保存成功
    expect(find.byKey(const Key('images_saved_success')), findsOneWidget);
  }

  /// 测试图片加载失败处理
  static Future<void> testImageLoadFailure(WidgetTester tester) async {
    // 1. 设置模拟加载失败
    // (在实际测试中需要mock图片加载失败的情况)

    // 2. 尝试添加图片
    await tester.tap(find.byKey(const Key('add_image_button')));
    await tester.pumpAndSettle();

    // 3. 选择图片
    await ImagePickerTestHelper.tapGallerySelection(tester);
    await tester.pumpAndSettle();

    // 4. 验证错误提示
    expect(find.byKey(const Key('image_load_error')), findsOneWidget);
  }

  /// 测试图片格式不支持
  static Future<void> testUnsupportedImageFormat(WidgetTester tester) async {
    // 1. 设置模拟不支持的图片格式
    ImagePickerTestHelper.setMockImageFile(XFile('/mock/path/test_file.txt'));

    // 2. 尝试添加文件
    await tester.tap(find.byKey(const Key('add_image_button')));
    await tester.pumpAndSettle();

    // 3. 选择文件
    await ImagePickerTestHelper.tapGallerySelection(tester);
    await tester.pumpAndSettle();

    // 4. 验证格式不支持提示
    expect(find.byKey(const Key('unsupported_format_error')), findsOneWidget);
  }
}