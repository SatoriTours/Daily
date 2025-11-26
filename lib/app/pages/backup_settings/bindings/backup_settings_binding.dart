import 'package:get/get.dart';

import '../controllers/backup_settings_controller.dart';

class BackupSettingsBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<BackupSettingsController>(() => BackupSettingsController())];
  }
}
