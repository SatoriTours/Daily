import 'package:logger/logger.dart';

late Logger logger;
String? shareText;

bool get isProduction => const bool.fromEnvironment("dart.vm.product");
