/// 备份配置
class BackupConfig {
  BackupConfig._();

  static const Duration interval = Duration(hours: 6); // 备份间隔时间
  static const int productionIntervalHours = 6; // 生产环境备份间隔(小时)
  static const int developmentIntervalHours = 24; // 开发环境备份间隔(小时)
  static const String fileExtension = '.zip'; // 备份文件扩展名
  static const String dateFormat = 'yyyy-MM-dd_HH-mm-ss'; // 备份日期格式
}
