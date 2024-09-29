import 'package:logger/logger.dart';

late Logger logger;

bool get isProduction => const bool.fromEnvironment("dart.vm.product");
