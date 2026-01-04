import 'package:daily_satori/app_exports.dart';

/// 表单部分标题
///
/// 用于显示表单区域的标题，包含图标和文本
class FormSectionHeader extends StatelessWidget {
  /// 标题文本
  final String title;

  /// 图标
  final IconData icon;

  const FormSectionHeader({super.key, required this.title, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: Dimensions.iconSizeS, color: AppColors.getPrimary(context)),
        Dimensions.horizontalSpacerM,
        Text(title, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.w500)),
      ],
    );
  }
}

/// 表单文本输入字段
///
/// 带有统一样式的文本输入框，支持密码输入模式
class FormTextField extends StatelessWidget {
  /// 文本控制器
  final TextEditingController controller;

  /// 提示文本
  final String hintText;

  /// 是否为密码输入
  final bool isPassword;

  /// 文本变化回调
  final Function(String)? onChanged;

  const FormTextField({
    super.key,
    required this.controller,
    required this.hintText,
    this.isPassword = false,
    this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      obscureText: isPassword,
      style: AppTypography.bodyMedium,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurface(context).withValues(alpha: Opacities.mediumLow),
        ),
        contentPadding: Dimensions.paddingInput,
        border: _buildBorder(context, AppColors.getOutline(context), 1),
        enabledBorder: _buildBorder(context, AppColors.getOutline(context), 1),
        focusedBorder: _buildBorder(context, AppColors.getPrimary(context), 2),
        filled: true,
        fillColor: AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
      ),
      onChanged: onChanged,
    );
  }

  /// 构建边框样式
  OutlineInputBorder _buildBorder(BuildContext context, Color color, double width) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(color: color, width: width),
    );
  }
}

/// 选择字段
///
/// 可点击的选择器组件，显示当前选中的值
class SelectionField extends StatelessWidget {
  /// 当前选中的值
  final String value;

  /// 点击回调
  final VoidCallback onTap;

  const SelectionField({super.key, required this.value, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      child: Container(
        width: double.infinity,
        padding: Dimensions.paddingInput,
        decoration: BoxDecoration(
          color: AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          border: Border.all(color: AppColors.getOutline(context), width: 1),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(value, style: AppTypography.bodyMedium, overflow: TextOverflow.ellipsis),
            ),
            Icon(Icons.arrow_drop_down, color: AppColors.getOnSurface(context), size: Dimensions.iconSizeM),
          ],
        ),
      ),
    );
  }
}