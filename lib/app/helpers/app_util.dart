import 'dart:math';

class AppUtil {
  static String generateDeviceId() {
    final random = Random();
    final codeUnits = List.generate(10, (index) => random.nextInt(26) + 65); // A-Z
    return String.fromCharCodes(codeUnits);
  }

  static String generateRandomPassword({int length = 6}) {
    const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    final random = Random();
    return String.fromCharCodes(
      List.generate(length, (index) => chars.codeUnitAt(random.nextInt(chars.length))),
    );
  }
}
