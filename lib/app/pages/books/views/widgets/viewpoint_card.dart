import 'package:daily_satori/app_exports.dart';
import 'package:intl/intl.dart';

/// 观点卡片组件
class ViewpointCard extends StatelessWidget {
  final BookViewpointModel viewpoint;
  final BookModel? book;

  const ViewpointCard({super.key, required this.viewpoint, this.book});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusM),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          Dimensions.spacingM,
          Dimensions.spacingS,
          Dimensions.spacingM,
          Dimensions.spacingM,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Dimensions.verticalSpacerL,
            _Title(text: viewpoint.title),
            Dimensions.verticalSpacerS,
            _BookInfo(book: book),
            Dimensions.verticalSpacerL,
            _Content(text: viewpoint.content),
            if (viewpoint.example.isNotEmpty) ...[Dimensions.verticalSpacerL, _Example(text: viewpoint.example)],
            Dimensions.verticalSpacerL,
            _Footer(date: viewpoint.createdAt),
          ],
        ),
      ),
    );
  }
}

class _Title extends StatelessWidget {
  final String text;

  const _Title({required this.text});

  @override
  Widget build(BuildContext context) {
    return SelectableText(
      text,
      style: AppTypography.headingMedium.copyWith(
        fontWeight: FontWeight.bold,
        height: 1.3,
        color: AppColors.getOnSurface(context),
      ),
    );
  }
}

class _Content extends StatelessWidget {
  final String text;

  const _Content({required this.text});

  @override
  Widget build(BuildContext context) {
    return SelectableText(
      text,
      style: AppTypography.bodyLarge.copyWith(
        height: 1.8,
        letterSpacing: 0.5,
        color: AppColors.getOnSurface(context).withValues(alpha: 0.9),
      ),
    );
  }
}

class _Example extends StatelessWidget {
  final String text;

  const _Example({required this.text});

  @override
  Widget build(BuildContext context) {
    final primary = AppColors.getPrimary(context);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingM, horizontal: Dimensions.spacingXs),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceVariant(context).withValues(alpha: 0.3),
        borderRadius: Dimensions.borderRadiusM,
        border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.1)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.lightbulb_outline, size: Dimensions.iconSizeS, color: primary),
              Dimensions.horizontalSpacerS,
              Text(
                '书籍案例',
                style: AppTypography.labelLarge.copyWith(color: primary, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          Dimensions.verticalSpacerS,
          SelectableText(
            text,
            style: AppTypography.bodyLarge.copyWith(
              height: 1.8,
              letterSpacing: 0.5,
              color: AppColors.getOnSurface(context).withValues(alpha: 0.8),
            ),
          ),
        ],
      ),
    );
  }
}

class _BookInfo extends StatelessWidget {
  final BookModel? book;

  const _BookInfo({this.book});

  @override
  Widget build(BuildContext context) {
    final text = book != null ? '《${book!.title}》· ${book!.author}' : '未知书籍';

    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        const Icon(Icons.menu_book, size: 12, color: Colors.grey),
        const SizedBox(width: 6),
        Text(text, style: AppTypography.bodySmall.copyWith(color: Colors.grey)),
      ],
    );
  }
}

class _Footer extends StatelessWidget {
  final DateTime date;

  const _Footer({required this.date});

  @override
  Widget build(BuildContext context) {
    final formattedDate = DateFormat('yyyy-MM-dd').format(date);

    return Row(
      children: [
        const Icon(Icons.calendar_today, size: 12, color: Colors.grey),
        const SizedBox(width: 6),
        Text(formattedDate, style: AppTypography.labelSmall.copyWith(color: Colors.grey)),
      ],
    );
  }
}
