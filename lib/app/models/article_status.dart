/// 文章状态枚举
enum ArticleStatus {
  pending('pending', '待处理'),
  webContentFetched('web_content_fetched', '网页内容已获取'),
  completed('completed', '已完成'),
  error('error', '错误');

  const ArticleStatus(this.value, this.label);

  final String value;
  final String label;

  /// 从字符串值获取枚举
  static ArticleStatus fromValue(String value) {
    return ArticleStatus.values.firstWhere((status) => status.value == value, orElse: () => ArticleStatus.error);
  }

  @override
  String toString() => value;
}
