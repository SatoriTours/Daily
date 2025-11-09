/// 消息文本配置
class MessageConfig {
  MessageConfig._();

  // ==================== 错误消息 ====================

  static const String errorNetwork = '网络连接错误，请检查网络后重试';
  static const String errorServer = '服务器错误，请稍后重试';
  static const String errorTimeout = '请求超时，请稍后重试';
  static const String errorUnknown = '发生未知错误，请稍后重试';
  static const String errorValidation = '输入数据无效，请检查后重试';
  static const String errorPermission = '权限不足，请联系管理员';
  static const String errorNotFound = '请求的资源不存在';
  static const String errorDuplicate = '数据已存在，无法重复添加';

  // ==================== 成功消息 ====================

  static const String successSave = '保存成功';
  static const String successDelete = '删除成功';
  static const String successUpdate = '更新成功';
  static const String successImport = '导入成功';
  static const String successExport = '导出成功';
  static const String successBackup = '备份成功';
  static const String successRestore = '恢复成功';

  // ==================== 提示消息 ====================

  static const String hintSearch = '输入关键词搜索...';
  static const String hintComment = '添加备注...';
  static const String hintUrl = '输入网址...';
  static const String hintTag = '添加标签...';
  static const String hintTitle = '输入标题...';
  static const String hintContent = '输入内容...';

  // ==================== 空状态消息 ====================

  static const String emptyArticles = '暂无文章，快去添加吧';
  static const String emptyDiary = '暂无日记，开始记录吧';
  static const String emptyBooks = '暂无书籍，快去阅读吧';
  static const String emptyTags = '暂无标签';
  static const String emptySearch = '没有找到匹配的结果';
  static const String emptyFavorites = '暂无收藏';

  // ==================== 占位符文本 ====================

  static const String placeholderTitle = '无标题';
  static const String placeholderContent = '暂无内容';
  static const String placeholderSummary = '暂无摘要';
  static const String placeholderUrl = 'https://example.com';
  static const String placeholderDate = '暂无日期';
  static const String placeholderAuthor = '未知作者';

  // ==================== 处理中消息 ====================

  static const String processing = '处理中...';

  // ==================== 书籍相关 ====================

  static const String addBookTitle = '添加书籍';
  static const String addBookHint = '请输入书名';
  static const String addBookConfirm = '添加';
  static const String addBookCancel = '取消';
}
