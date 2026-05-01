/// 会话配置
class SessionConfig {
  SessionConfig._();

  static const Duration expireTime = Duration(minutes: 30); // 会话过期时间
  static const Duration inactivityTimeout = Duration(
    minutes: 1,
    seconds: 30,
  ); // 不活动超时时间
  static const Duration checkInterval = Duration(minutes: 15); // 会话检查间隔
}
