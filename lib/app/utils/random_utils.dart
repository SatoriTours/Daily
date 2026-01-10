import 'dart:math';

/// 随机工具类
class RandomUtils {
  /// 生成随机设备ID
  static String generateDeviceId() {
    final random = Random();
    final codeUnits = List.generate(
      10,
      (index) => random.nextInt(26) + 65,
    ); // A-Z
    return String.fromCharCodes(codeUnits);
  }

  /// 生成随机密码
  /// [length] 密码长度，默认为6
  static String generateRandomPassword({int length = 6}) {
    const chars =
        'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    final random = Random();
    return String.fromCharCodes(
      List.generate(
        length,
        (index) => chars.codeUnitAt(random.nextInt(chars.length)),
      ),
    );
  }
}
