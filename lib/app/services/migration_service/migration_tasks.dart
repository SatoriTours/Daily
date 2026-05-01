import 'package:daily_satori/app/services/migration_service/migration_task.dart';
import 'package:daily_satori/app/services/migration_service/tasks/v1_ai_config_migration.dart';
import 'package:daily_satori/app/services/migration_service/tasks/v2_image_migration.dart';
import 'package:daily_satori/app/services/migration_service/tasks/v3_path_migration.dart';

/// 迁移任务注册类 - 管理所有迁移任务
class MigrationTasks {
  // 私有构造函数，防止实例化
  MigrationTasks._();

  /// 获取所有注册的迁移任务
  ///
  /// 返回按版本号排序的迁移任务列表
  static List<MigrationTask> getAll() {
    final List<MigrationTask> tasks = [
      // 在这里注册所有迁移任务，按版本号顺序
      AIConfigMigrationTask(),
      ImageMigrationTask(),
      PathMigrationTask(),

      // 添加新的迁移任务...
      // ExampleMigrationTask(),
    ];

    // 按版本号排序
    tasks.sort((a, b) => a.version.compareTo(b.version));

    return tasks;
  }
}
