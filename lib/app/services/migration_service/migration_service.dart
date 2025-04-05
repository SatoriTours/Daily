import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/migration_service/migration_task.dart';
import 'package:daily_satori/app/services/migration_service/migration_tasks.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:objectbox/objectbox.dart';

/// 迁移服务，用于处理数据模型和文件存储的迁移工作
///
/// 负责处理不同版本之间的数据结构变更、存储模式变更等迁移工作
/// 支持版本管理，确保迁移任务按顺序执行且不会重复执行
class MigrationService {
  // 单例模式
  MigrationService._();
  static final MigrationService _instance = MigrationService._();
  static MigrationService get i => _instance;

  // 数据库版本号的设置键
  static const String _dbVersionKey = 'db_version';

  /// 初始化服务
  Future<void> init() async {
    logger.i("🔄 [迁移服务] 初始化");

    try {
      // 获取当前数据库版本
      final currentVersion = _getCurrentDbVersion();

      // 获取所有迁移任务
      final migrationTasks = MigrationTasks.getAll();

      // 执行所有需要的迁移任务
      await _runMigrations(currentVersion, migrationTasks);
    } catch (e, stackTrace) {
      logger.e("❌ [迁移服务] 初始化失败: $e", stackTrace: stackTrace);
    }
  }

  /// 获取当前数据库版本
  int _getCurrentDbVersion() {
    final versionStr = SettingRepository.getSetting(_dbVersionKey, defaultValue: '0');
    try {
      return int.parse(versionStr);
    } catch (e) {
      logger.w("⚠️ [迁移服务] 版本号解析失败，将使用默认值0: $e");
      return 0;
    }
  }

  /// 更新数据库版本
  Future<void> _updateDbVersion(int version) async {
    await SettingRepository.saveSetting(_dbVersionKey, version.toString());
    logger.i("📝 [迁移服务] 数据库版本更新为: $version");
  }

  /// 执行所有需要的迁移任务
  Future<void> _runMigrations(int currentVersion, List<MigrationTask> tasks) async {
    // 最新版本号
    int latestVersion = currentVersion;

    // 如果没有任务需要执行，记录日志
    if (tasks.isEmpty) {
      logger.i("✅ [迁移服务] 没有迁移任务");
      return;
    }

    // 执行所有高于当前版本的迁移任务
    for (final task in tasks) {
      if (task.version > currentVersion) {
        // 检查是否需要运行此迁移任务
        bool shouldRun = await task.shouldRun();

        if (shouldRun) {
          logger.i("🔄 [迁移服务] 执行迁移任务 v${task.version}: ${task.description}");

          // 使用ObjectBox事务确保迁移任务和版本更新的原子性
          await ObjectboxService.i.store.runInTransaction(TxMode.write, () async {
            // 执行迁移任务
            await task.migrate();

            // 更新最新版本号
            latestVersion = task.version;

            // 更新数据库版本
            await _updateDbVersion(latestVersion);
          });

          logger.i("✅ [迁移服务] 迁移任务 v${task.version} 完成");
        } else {
          logger.i("⏭️ [迁移服务] 跳过迁移任务 v${task.version}: 不需要执行");

          // 虽然跳过了任务，但仍然需要更新版本号
          latestVersion = task.version;
          await _updateDbVersion(latestVersion);
        }
      }
    }

    // 如果没有任务需要执行，记录日志
    if (latestVersion == currentVersion) {
      logger.i("✅ [迁移服务] 数据库已是最新版本: v$currentVersion");
    }
  }
}
