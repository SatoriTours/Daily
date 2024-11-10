part of 'ai_service.dart';

extension PartSummarize on AiService {
  Future<String> summarizeOneLine(String text) async {
    // logger.i("[AI总结]: ${getSubstring(text)}");
    final res = await _sendRequest(
      '你是一个文章读者, 能总结文章的核心内容',
      ' 一句话总结一下内容：```$text``` ',
    );
    return res?.choices.first.message.content ?? '';
  }

  Future<(String, List<String>)> summarize(String text) async {
    logger.i("[AI总结]: ${getSubstring(text)}");

    final res = await _sendRequest(
      '你是一个文章读者, 能用中文总结文章的核心内容以及符合的标签',
      _summarizePrompt(text),
      responseFormat: _summarizeResponseFormat,
    );

    final json = jsonDecode(res?.choices.first.message.content ?? '');
    final summary = json['summary'] as String;
    final tags = json['tags'] as List<String>;

    return (summary, tags);
  }

  ResponseFormat get _summarizeResponseFormat {
    return ResponseFormat.jsonSchema(
      jsonSchema: JsonSchemaObject(
        name: 'result',
        description: '内容解析结果',
        strict: true,
        schema: {
          'type': 'object',
          'properties': {
            'summary': {'type': 'string'},
            'tags': {
              'type': 'array',
              'items': {'type': 'string'}
            },
          },
          'additionalProperties': false,
          'required': ['summary', 'tags'],
        },
      ),
    );
  }

  String _summarizePrompt(String text) => '''
请对以下文章进行总结成新的文章：
```
$text
```
下面是已有的标签, 用逗号分割:
```
${TagsService.i.getTagsString()}
```
注意事项：
1. 提取文章的主要观点和关键信息, 一定用中文输出.
2. 确保总结简洁明了, 直接给结果，不要给其他任何的解释.
3. 保持原文的意思，不要添加个人观点.
4. 请注意```, 不包括 ```, 内的内容是附加信息，翻译时要保持其完整性.
5. 尽量匹配给定的标签,如果都匹配不上,请给出新的合理标签,并且新增标签数量不超过2个.
6. 每个标签的名字不超过5个字.
''';
}
