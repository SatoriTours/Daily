import 'package:get/get.dart';

import '../controllers/backup_restore_controller.dart';

class BackupRestoreBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<BackupRestoreController>(() => BackupRestoreController())];
  }
}
