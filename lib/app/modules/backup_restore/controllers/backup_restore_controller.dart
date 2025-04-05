import 'dart:io';

import 'package:daily_satori/app_exports.dart';
import 'package:archive/archive.dart' as archive;
import 'package:intl/intl.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class BackupRestoreController extends BaseController {
  var selectedBackupIndex = 0.obs;
  var backupList = <String>[].obs;

  @override
  void onInit() {
    super.onInit();
    reloadBackupList();
  }

  Future<void> reloadBackupList() async {
    backupList.clear();
    logger.i("开始加载备份列表");
    String backupDir = SettingRepository.getSetting(SettingService.backupDirKey);
    final directory = Directory(backupDir);

    if (await directory.exists()) {
      var entities = await directory.list().toList();
      entities.sort((a, b) => b.path.compareTo(a.path)); // 根据文件名倒序排序
      for (var entity in entities) {
        logger.i("得等到目录 ${entity.path}");
        if (entity is Directory && entity.path.contains('daily_satori_backup_')) {
          String dirName = entity.path.split('_').last;
          backupList.add(dirName);
        }
      }
    }

    logger.i("加载备份列表完成, 获取 ${backupList.length} 个备份");
  }

  String? getBackupTime(String backupName) {
    RegExp regExp = RegExp(r'(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2})');
    Match? match = regExp.firstMatch(backupName);
    String? dateStr = match?.group(1);
    String formattedDateStr = dateStr!.replaceAllMapped(
      RegExp(r'T(\d{2})-(\d{2})-(\d{2})'),
      (match) => 'T${match[1]}:${match[2]}:${match[3]}',
    );
    DateTime dateTime = DateTime.parse(formattedDateStr);
    return DateFormat('yyyy年MM月dd日 HH:mm:ss').format(dateTime);
  }

  Future<bool> restoreBackup() async {
    String backupName = backupList[selectedBackupIndex.value];
    String backupDir = SettingRepository.getSetting(SettingService.backupDirKey);
    String backupFolder = path.join(backupDir, 'daily_satori_backup_$backupName');

    final imagesFile = File(path.join(backupFolder, 'images.zip'));
    final screenshotsFile = File(path.join(backupFolder, 'screenshots.zip'));
    final databaseFile = File(path.join(backupFolder, 'objectbox.zip'));

    String appDocDir = (await getApplicationDocumentsDirectory()).path;
    UIUtils.showLoading();

    if (await imagesFile.exists() && await screenshotsFile.exists() && await databaseFile.exists()) {
      try {
        logger.i("恢复备份 images 目录");
        await _extractZipFile(imagesFile, path.join(appDocDir, 'images'));
        logger.i("恢复备份 screenshots 目录");
        await _extractZipFile(screenshotsFile, path.join(appDocDir, 'screenshots'));
        logger.i("恢复备份数据库文件");
        await _extractZipFile(databaseFile, path.join(appDocDir, ObjectboxService.dbDir));
        logger.i("恢复备份完成: $backupFolder => $appDocDir");
        Get.back();
        return true;
      } catch (e) {
        logger.e("恢复备份失败: $e");
        Get.back();
        return false;
      }
    } else {
      logger.e("备份文件不存在, 无法恢复");
      Get.back();
      return false;
    }
  }

  Future<void> _extractZipFile(File zipFile, String destinationPath) async {
    final bytes = await zipFile.readAsBytes();
    final zipArchive = archive.ZipDecoder().decodeBytes(bytes);

    for (final file in zipArchive) {
      final filename = file.name;
      if (file.isFile) {
        final data = file.content as List<int>;
        File(path.join(destinationPath, filename))
          ..createSync(recursive: true)
          ..writeAsBytesSync(data);
      } else {
        Directory(path.join(destinationPath, filename)).createSync(recursive: true);
      }
    }
  }
}
