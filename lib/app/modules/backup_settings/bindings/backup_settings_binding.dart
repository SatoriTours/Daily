import 'package:get/get.dart';

import '../controllers/backup_settings_controller.dart';

class BackupSettingsBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<BackupSettingsController>(() => BackupSettingsController());
  }
}
