/// AI配置
class AIConfig {
  AIConfig._();

  static const Duration timeout = Duration(seconds: 30); // AI请求超时时间
  static const int maxSummaryLength = 500; // 最大摘要长度
  static const int maxContentLength = 10000; // 最大内容长度
  static const int maxTitleLength = 100; // 最大标题长度
  static const int maxTagsPerArticle = 10; // 每篇文章最大标签数
  static const double defaultTemperature = 0.5; // 默认温度参数
  static const int maxProcessContentLength = 50000; // AI处理内容最大长度

  // 文章处理常量
  static const int minHtmlLength = 50; // HTML内容最小长度
  static const int minTextLength = 20; // 文本内容最小长度
  static const int longTitleThreshold = 50; // 标题过长阈值

  // 书籍推荐相关
  static const int randomRecommendationCount = 10; // 随机推荐数量
}
