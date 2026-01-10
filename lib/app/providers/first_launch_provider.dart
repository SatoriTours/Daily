/// 首次启动引导 Provider
///
/// 管理首次启动时的必要配置检测和引导流程

library;

import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

part 'first_launch_provider.g.dart';

/// 首次启动状态
@riverpod
class FirstLaunchController extends _$FirstLaunchController {
  @override
  FirstLaunchState build() {
    ref.keepAlive();
    return _checkSetupStatus();
  }

  /// 检查设置完成状态
  FirstLaunchState _checkSetupStatus() {
    // 1. 检查 AI 配置（必填）
    final aiConfigs = AIConfigRepository.i.allModels();
    final hasAIConfig = aiConfigs.any(
      (config) =>
          config.apiToken.isNotEmpty &&
          config.apiAddress.isNotEmpty &&
          config.modelName.isNotEmpty,
    );

    // logger.i('[首次启动] AI配置: ${hasAIConfig ? "已配置" : "未配置"}');

    // 2. 检查 Google Cloud API Key（选填）
    final googleCloudKey = SettingRepository.i.getSetting(
      SettingService.googleCloudApiKeyKey,
    );
    final hasGoogleCloudKey = googleCloudKey.isNotEmpty;

    // logger.i('[首次启动] Google Cloud Key: ${hasGoogleCloudKey ? "已配置" : "未配置"}');

    // 3. 检查备份目录（选填）
    final backupDir = SettingRepository.i.getSetting(
      SettingService.backupDirKey,
    );
    final hasBackupDir = backupDir.isNotEmpty;

    // logger.i('[首次启动] 备份目录: ${hasBackupDir ? "已配置" : "未配置"}');

    // 检查 AI 配置和备份目录是否完成（必填项）
    final isSetupComplete = hasAIConfig && hasBackupDir;

    // logger.i('[首次启动] 配置状态: ${isSetupComplete ? "已完成" : "未完成"}');

    return FirstLaunchState(
      isSetupComplete: isSetupComplete,
      hasAIConfig: hasAIConfig,
      hasGoogleCloudKey: hasGoogleCloudKey,
      hasBackupDir: hasBackupDir,
    );
  }

  /// 标记配置为已完成（用于跳过引导）
  void markSetupComplete() {
    logger.i('[首次启动] 手动标记配置为已完成');
    state = state.copyWith(isSetupComplete: true);
  }
}

/// 首次启动状态模型
class FirstLaunchState {
  /// 是否已完成所有必要配置
  final bool isSetupComplete;

  /// 是否已配置 AI
  final bool hasAIConfig;

  /// 是否已配置 Google Cloud API Key
  final bool hasGoogleCloudKey;

  /// 是否已配置备份目录
  final bool hasBackupDir;

  const FirstLaunchState({
    required this.isSetupComplete,
    required this.hasAIConfig,
    required this.hasGoogleCloudKey,
    required this.hasBackupDir,
  });

  /// 复制并修改部分字段
  FirstLaunchState copyWith({
    bool? isSetupComplete,
    bool? hasAIConfig,
    bool? hasGoogleCloudKey,
    bool? hasBackupDir,
  }) {
    return FirstLaunchState(
      isSetupComplete: isSetupComplete ?? this.isSetupComplete,
      hasAIConfig: hasAIConfig ?? this.hasAIConfig,
      hasGoogleCloudKey: hasGoogleCloudKey ?? this.hasGoogleCloudKey,
      hasBackupDir: hasBackupDir ?? this.hasBackupDir,
    );
  }

  /// 获取未完成配置的数量
  int get pendingCount {
    int count = 0;
    if (!hasAIConfig) count++;
    if (!hasGoogleCloudKey) count++;
    if (!hasBackupDir) count++;
    return count;
  }
}
