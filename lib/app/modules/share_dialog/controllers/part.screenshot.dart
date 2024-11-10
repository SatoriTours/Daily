part of 'share_dialog_controller.dart';

extension PartScreenshot on ShareDialogController {
  Future<void> _screenshotTask() async {
    final screenshotPath = await _takeScreenshot();
  }
}
