import 'package:get/get.dart';

import '../controllers/backup_restore_controller.dart';

class BackupRestoreBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<BackupRestoreController>(
      () => BackupRestoreController(),
    );
  }
}
