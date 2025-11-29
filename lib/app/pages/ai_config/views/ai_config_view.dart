import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import '../controllers/ai_config_controller.dart';
import '../models/ai_config_types.dart';
import 'widgets/ai_config_info_dialog.dart';

/// AI配置页面
///
/// 用于管理不同功能的AI配置，包括：
/// - 通用配置：所有AI功能的基础配置
/// - 文章总结：生成文章摘要和关键点
/// - 书本解读：解析书籍内容和笔记
/// - 日记总结：分析和生成日记内容
class AIConfigView extends GetView<AIConfigController> {
  const AIConfigView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getSurface(context),
      appBar: _buildAppBar(context),
      body: _buildBody(context),
    );
  }

  // ========================================================================
  // AppBar 构建
  // ========================================================================

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return SAppBar(
      title: const Text('AI 配置管理', style: TextStyle(color: Colors.white)),
      centerTitle: true,
      elevation: 0,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
      actions: [
        IconButton(
          icon: const Icon(Icons.info_outline, color: Colors.white),
          tooltip: 'AI配置说明',
          onPressed: () => _showInfoDialog(context),
        ),
      ],
    );
  }

  // ========================================================================
  // Body 构建
  // ========================================================================

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.isLoading.value) {
        return StyleGuide.getLoadingState(context);
      }

      final allConfigs = controller.configs;
      if (allConfigs.isEmpty) {
        return _buildEmptyState(context);
      }

      return _buildConfigList(context, allConfigs);
    });
  }

  /// 构建空状态提示
  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Text(
        '没有配置',
        style: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurface(context).withValues(alpha: Opacities.mediumLow),
        ),
      ),
    );
  }

  /// 构建配置列表
  Widget _buildConfigList(BuildContext context, List<dynamic> configs) {
    return ListView.separated(
      padding: Dimensions.paddingPage,
      itemCount: configs.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) => _buildConfigCard(context, configs[index]),
    );
  }

  // ========================================================================
  // 配置卡片构建
  // ========================================================================

  /// 构建配置卡片
  Widget _buildConfigCard(BuildContext context, dynamic config) {
    final color = AIConfigTypes.getColor(config.functionType);
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        onTap: () => controller.editConfig(config),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        child: Padding(
          padding: Dimensions.paddingCard,
          child: Row(
            children: [
              _buildConfigIcon(config.functionType, color),
              Dimensions.horizontalSpacerM,
              _buildConfigInfo(context, config),
              _buildChevronIcon(context),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建配置图标
  Widget _buildConfigIcon(int type, Color color) {
    return FeatureIcon(
      icon: AIConfigTypes.getIcon(type),
      iconColor: color,
      containerSize: Dimensions.iconSizeL,
      iconSize: Dimensions.iconSizeS,
    );
  }

  /// 构建配置信息
  Widget _buildConfigInfo(BuildContext context, dynamic config) {
    final isInheriting = config.apiAddress.isEmpty && config.functionType != 0;

    return Expanded(
      child: Row(
        children: [
          // 左侧：配置名称和继承标签
          Expanded(child: _buildConfigNameWithBadge(context, config, isInheriting)),
          // 右侧：状态信息或模型名称
          _buildConfigStatus(context, config),
        ],
      ),
    );
  }

  /// 构建配置名称（带继承标签）
  Widget _buildConfigNameWithBadge(BuildContext context, dynamic config, bool isInheriting) {
    return Row(
      children: [
        Text(config.name, style: AppTypography.titleSmall),
        if (isInheriting) ...[Dimensions.horizontalSpacerS, _buildInheritBadge(context)],
      ],
    );
  }

  /// 构建继承标签
  Widget _buildInheritBadge(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow),
        borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
      ),
      child: Text('继承', style: AppTypography.labelSmall.copyWith(color: AppColors.getPrimary(context))),
    );
  }

  /// 构建配置状态
  Widget _buildConfigStatus(BuildContext context, dynamic config) {
    if (config.apiAddress.isEmpty) {
      return Text(
        config.functionType == 0 ? '未配置' : '使用通用配置',
        style: AppTypography.bodySmall.copyWith(
          color: AppColors.getOnSurface(context).withValues(alpha: Opacities.mediumLow),
        ),
      );
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          Icons.smart_toy,
          size: Dimensions.iconSizeXs,
          color: AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
        ),
        Dimensions.horizontalSpacerXs,
        Text(
          config.modelName,
          style: AppTypography.bodySmall.copyWith(
            color: AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }

  /// 构建右侧箭头图标
  Widget _buildChevronIcon(BuildContext context) {
    return Icon(
      Icons.chevron_right,
      color: AppColors.getOnSurface(context).withValues(alpha: Opacities.mediumLow),
      size: Dimensions.iconSizeS,
    );
  }

  // ========================================================================
  // 对话框
  // ========================================================================

  /// 显示AI配置信息对话框
  void _showInfoDialog(BuildContext context) {
    showDialog(context: context, builder: (context) => const AIConfigInfoDialog());
  }
}
