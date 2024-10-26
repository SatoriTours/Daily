import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';
import 'package:daily_satori/init_app.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app/routes/app_pages.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  PlatformInAppWebViewController.debugLoggingSettings.enabled = false;
  await initApp();

  runApp(
    GetMaterialApp(
      title: "Daily Satori",
      theme: ThemeData.light().copyWith(
        textTheme: ThemeData.light().textTheme.apply(fontFamily: GoogleFonts.lato().fontFamily),
      ),
      darkTheme: ThemeData.dark().copyWith(
        textTheme: ThemeData.dark().textTheme.apply(fontFamily: GoogleFonts.lato().fontFamily),
      ),
      themeMode: ThemeMode.system,
      initialRoute: AppPages.INITIAL,
      getPages: AppPages.routes,
    ),
  );
}
