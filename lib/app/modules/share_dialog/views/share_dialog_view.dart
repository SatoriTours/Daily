import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/buttons/app_button.dart';
import 'package:daily_satori/app/components/buttons/button_group.dart';
import 'package:daily_satori/app/components/common/labeled_section.dart';
import 'package:daily_satori/app/components/inputs/comment_field.dart';

/// 分享页面视图
/// 用于保存链接或添加/更新文章备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: _buildAppBar(context),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                child: Padding(
                  padding: Dimensions.paddingPage,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildArticleInfo(context),
                      Dimensions.verticalSpacerL,
                      _buildCommentSection(context),
                      Dimensions.verticalSpacerL,
                    ],
                  ),
                ),
              ),
            ),
            Padding(padding: Dimensions.paddingPage, child: _buildSaveButton(context)),
          ],
        ),
      ),
    );
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Obx(() => Text(controller.isUpdate.value ? '更新文章' : '保存链接', style: MyFontStyle.appBarTitleStyle)),
      automaticallyImplyLeading: !controller.isUpdate.value,
    );
  }

  // 构建文章信息区域
  Widget _buildArticleInfo(BuildContext context) {
    return Obx(() {
      if (controller.isUpdate.value) {
        return _buildArticleTitle(context);
      } else {
        return _buildArticleURL(context);
      }
    });
  }

  // 构建文章标题区域
  Widget _buildArticleTitle(BuildContext context) {
    return LabeledSection(
      icon: Icons.article_outlined,
      label: "文章标题",
      showCardBackground: true,
      child: Text(
        controller.articleTitle.value,
        style: MyFontStyle.bodyLarge,
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  // 构建文章URL区域
  Widget _buildArticleURL(BuildContext context) {
    return LabeledSection(
      icon: Icons.link_rounded,
      label: controller.getShortUrl(controller.shareURL.value),
      showCardBackground: true,
      child: Text(
        controller.shareURL.value,
        style: MyFontStyle.bodyMedium,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  // 构建备注信息区域
  Widget _buildCommentSection(BuildContext context) {
    return LabeledSection(
      icon: Icons.comment_outlined,
      label: "备注信息",
      showCardBackground: true,
      child: CommentField(controller: controller.commentController, hintText: "添加备注信息（可选）"),
    );
  }

  // 构建保存按钮
  Widget _buildSaveButton(BuildContext context) {
    return ButtonGroup(
      children: [
        AppButton(title: "取消", type: AppButtonType.secondary, onPressed: () => Get.back()),
        AppButton(title: "保存", type: AppButtonType.primary, onPressed: () => controller.onSaveButtonPressed()),
      ],
    );
  }
}
