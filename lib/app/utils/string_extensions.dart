/// 字符串扩展工具
extension StringExtension on String? {
  /// 检查字符串是否为null或空
  bool get isNullOrEmpty => this == null || this!.isEmpty;

  /// 检查字符串是否不为null且不为空
  bool get isNotNullOrEmpty => !isNullOrEmpty;

  /// 获取安全的字符串值，如果为null则返回空字符串
  String get orEmpty => this ?? '';

  /// 获取安全的字符串值，如果为null或空则返回提供的默认值
  String orDefault(String defaultValue) => isNullOrEmpty ? defaultValue : this!;
}
