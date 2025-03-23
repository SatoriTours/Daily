part of 'ai_service.dart';

/// HTML转Markdown扩展
///
/// 提供将HTML内容转换为格式良好的Markdown的功能
extension PartHtmlToMarkdown on AiService {
  /// 将HTML内容转换为Markdown格式
  ///
  /// 分析HTML内容,保持原始内容不变,生成格式良好的Markdown
  /// [htmlContent] 原始HTML内容
  /// [title] 文章标题
  /// [updatedAt] 更新时间
  Future<String> convertHtmlToMarkdown(String htmlContent, {String? title, DateTime? updatedAt}) async {
    if (htmlContent.isEmpty) return '';

    // 检查AI是否启用
    if (!SettingRepository.aiEnabled(SettingService.openAITokenKey, SettingService.openAIAddressKey)) {
      logger.i("[AI转换] AI服务未启用,跳过HTML到Markdown的转换");
      return '';
    }

    logger.i("[AI转换] 将HTML转换为Markdown中...");

    // 构建系统提示
    final roleTemplate = '''
你是一个精通HTML和Markdown格式的专业排版专家。你的任务是将HTML内容转换为格式精美的Markdown,就像专业出版物一样。

请将以下HTML内容精准转换为符合阅读习惯的Markdown格式,要求如下：
1. 内容忠实度,严格保持原文文字内容(不增/删/改原意)
2. 保留所有数据：列表、表格、代码块、图片链接等元素
3. 仅允许调整空格、换行、标点间距等排版细节, 并且可以调整和合并段落，使段落更符合阅读习惯
4. markdown排版规范
  - 采用紧凑型排版规则
  - 中文段落首行**不加空格**
  - 中英文混排时自动添加空格(例："使用 GitHub 仓库")
  - 列表层级使用`-`符号,嵌套列表用2个空格缩进
  - 代码块标注语言类型(例：```python)
  - 图片链接转为`![描述](URL)`格式
  - 删除HTML注释、冗余空行和<meta>标签
5. 阅读体验优化
  - 确保移动端友好(段落长度<5行,避免超长句子)
  - 技术文档保留代码高亮标识
  - 复杂表格转换为管道表格(保持对齐)
  - 重点内容使用粗体/_斜体_标记(若原文有强调语义)
6. 禁止行为
  - 不添加任何原文没有的解释性内容
  - 不遗漏任何文本
  - 不改变原有信息顺序
7. 请只返回转换后的Markdown内容,不要添加任何解释或说明。
''';

    // 发送请求
    final response = await _sendRequest(roleTemplate, htmlContent);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI转换] HTML到Markdown转换失败");
      return '';
    }

    logger.i("[AI转换] HTML到Markdown转换完成: ${getSubstring(result)}");
    return result;
  }
}
