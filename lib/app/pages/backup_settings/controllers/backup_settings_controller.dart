import 'package:daily_satori/app_exports.dart';
import 'package:file_picker/file_picker.dart';
import 'package:permission_handler/permission_handler.dart';

class BackupSettingsController extends BaseController {
  final backupDirectory = ''.obs;

  // 备份进度状态，从BackupService获取
  RxBool get isBackingUp => BackupService.i.isBackingUp;
  RxDouble get backupProgress => BackupService.i.backupProgress;

  @override
  void onInit() {
    super.onInit();
    _loadBackupDirectory();
  }

  /// 加载备份目录
  void _loadBackupDirectory() {
    String dir = SettingRepository.i.getSetting(SettingService.backupDirKey);
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
      SettingRepository.i.saveSetting(SettingService.backupDirKey, selectedDirectory);
    }
  }

  /// 执行备份操作
  Future<bool> performBackup() async {
    if (backupDirectory.value.isEmpty) {
      UIUtils.showError('请先选择备份目录');
      return false;
    }

    // 使用 BackupService 执行备份
    return await BackupService.i.checkAndBackup(immediateBackup: true);
  }
}
