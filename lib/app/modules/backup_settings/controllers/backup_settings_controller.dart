import 'dart:io';

import 'package:archive/archive.dart' as archive_lib;
import 'package:daily_satori/app_exports.dart';
import 'package:file_picker/file_picker.dart';
import 'package:intl/intl.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

class BackupSettingsController extends BaseController {
  final backupDirectory = ''.obs;
  final isBackingUp = false.obs;
  final backupProgress = 0.0.obs;

  @override
  void onInit() {
    super.onInit();
    _loadBackupDirectory();
  }

  /// 加载备份目录
  void _loadBackupDirectory() {
    String dir = SettingRepository.getSetting(SettingService.backupDirKey);
    backupDirectory.value = dir;
  }

  /// 选择备份目录
  Future<void> selectBackupDirectory() async {
    // 检查是否有权限
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();
    if (!manageExternalStoragePermission.isGranted) {
      UIUtils.showError('请授予应用管理外部存储的权限');
      return;
    }

    final String? selectedDirectory = await FilePicker.platform.getDirectoryPath(
      dialogTitle: '选择备份文件夹',
      initialDirectory: backupDirectory.value.isNotEmpty ? backupDirectory.value : null,
    );

    if (selectedDirectory != null) {
      backupDirectory.value = selectedDirectory;
      logger.i('选择备份目录: $selectedDirectory');
      SettingRepository.saveSetting(SettingService.backupDirKey, selectedDirectory);
    }
  }

  /// 执行备份操作
  Future<bool> performBackup() async {
    if (backupDirectory.value.isEmpty) {
      UIUtils.showError('请先选择备份目录');
      return false;
    }

    isBackingUp.value = true;
    backupProgress.value = 0.0;

    try {
      final appDocDir = (await getApplicationDocumentsDirectory()).path;
      final now = DateTime.now();
      final timestamp = DateFormat('yyyy-MM-ddTHH-mm-ss').format(now);
      final backupFolderName = 'daily_satori_backup_$timestamp';
      final backupFolder = path.join(backupDirectory.value, backupFolderName);

      // 创建备份目录
      await Directory(backupFolder).create(recursive: true);

      backupProgress.value = 0.1;

      // 备份数据库
      final dbDir = path.join(appDocDir, ObjectboxService.dbDir);
      await _compressDir(dbDir, path.join(backupFolder, 'objectbox.zip'));

      backupProgress.value = 0.4;

      // 备份图片
      final imagesDir = path.join(appDocDir, 'images');
      if (await Directory(imagesDir).exists()) {
        await _compressDir(imagesDir, path.join(backupFolder, 'images.zip'));
      }

      backupProgress.value = 0.7;

      // 备份截图
      final screenshotsDir = path.join(appDocDir, 'screenshots');
      if (await Directory(screenshotsDir).exists()) {
        await _compressDir(screenshotsDir, path.join(backupFolder, 'screenshots.zip'));
      }

      backupProgress.value = 1.0;
      logger.i('备份完成: $backupFolder');

      return true;
    } catch (e) {
      logger.e('备份失败: $e');
      return false;
    } finally {
      isBackingUp.value = false;
    }
  }

  /// 压缩目录
  Future<void> _compressDir(String dirPath, String zipFilePath) async {
    final dir = Directory(dirPath);
    final zipFile = File(zipFilePath);
    final fileArchive = archive_lib.Archive();

    await for (final file in dir.list(recursive: true)) {
      if (file is File) {
        final relativePath = path.relative(file.path, from: dirPath);
        final data = await file.readAsBytes();
        final archiveFile = archive_lib.ArchiveFile(relativePath, data.length, data);
        fileArchive.addFile(archiveFile);
      }
    }

    final bytes = archive_lib.ZipEncoder().encode(fileArchive);
    if (bytes != null) {
      await zipFile.writeAsBytes(bytes);
    }
  }
}
