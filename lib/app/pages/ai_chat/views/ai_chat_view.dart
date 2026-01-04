import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/ai_chat/providers/ai_chat_controller_provider.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import '../../../components/ai_chat/chat_interface.dart';

class AIChatView extends ConsumerStatefulWidget {
  const AIChatView({super.key});

  @override
  ConsumerState<AIChatView> createState() => _AIChatViewState();
}

class _AIChatViewState extends ConsumerState<AIChatView> {
  final ScrollController _scrollController = ScrollController();
  final TextEditingController _inputController = TextEditingController();

  @override
  void dispose() {
    _scrollController.dispose();
    _inputController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(aIChatControllerProvider.select((s) => s.messages.length), (previous, next) {
      if (next > (previous ?? 0)) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (_scrollController.hasClients) {
            _scrollController.animateTo(
              _scrollController.position.maxScrollExtent,
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeOut,
            );
          }
        });
      }
    });

    final state = ref.watch(aIChatControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: SAppBar(
        title: Text('ai_chat.title'.t, style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.bold)),
        backgroundColorLight: AppColors.surface,
        backgroundColorDark: AppColors.surfaceDark,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(Icons.refresh_rounded, color: AppColors.getOnSurface(context)),
            onPressed: () => ref.read(aIChatControllerProvider.notifier).clearMessages(),
            tooltip: 'ai_chat.new_chat'.t,
          ),
          IconButton(
            icon: Icon(Icons.info_outline_rounded, color: AppColors.getOnSurface(context)),
            onPressed: () => _showHelpDialog(context),
            tooltip: 'ai_chat.help'.t,
          ),
        ],
      ),
      body: ChatInterface(
        messages: state.messages,
        onSendMessage: (msg) => ref.read(aIChatControllerProvider.notifier).sendMessage(msg),
        onRetryMessage: (msg) => ref.read(aIChatControllerProvider.notifier).retryMessage(msg),
        scrollController: _scrollController,
        inputController: _inputController,
        isLoading: state.isProcessing,
        showHeader: false,
        headerSubtitle: state.isProcessing ? state.currentStep : null,
      ),
    );
  }

  void _showHelpDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.help_outline, color: AppColors.getPrimary(context)),
            Dimensions.horizontalSpacerM,
            Text('ai_chat.help_title'.t),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              _HelpSection(
                title: 'ai_chat.help_features_title'.t,
                content: 'ai_chat.help_features_content'.t,
                icon: Icons.star_outline,
              ),
              Dimensions.verticalSpacerM,
              _HelpSection(
                title: 'ai_chat.help_usage_title'.t,
                content: 'ai_chat.help_usage_content'.t,
                icon: Icons.chat_bubble_outline,
              ),
              Dimensions.verticalSpacerM,
              _HelpSection(
                title: 'ai_chat.help_tips_title'.t,
                content: 'ai_chat.help_tips_content'.t,
                icon: Icons.lightbulb_outline,
              ),
            ],
          ),
        ),
        actions: [TextButton(onPressed: () => AppNavigation.back(), child: Text('ai_chat.got_it'.t))],
      ),
    );
  }
}

class _HelpSection extends StatelessWidget {
  final String title;
  final String content;
  final IconData icon;

  const _HelpSection({required this.title, required this.content, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: Dimensions.paddingM,
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainer(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: Dimensions.iconSizeM, color: AppColors.getPrimary(context)),
              Dimensions.horizontalSpacerM,
              Text(title, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.w600)),
            ],
          ),
          Dimensions.verticalSpacerS,
          Text(content, style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
        ],
      ),
    );
  }
}
