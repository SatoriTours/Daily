import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/message_config.dart';

void main() {
  group('MessageConfig', () {
    group('error messages', () {
      test('should have network error message', () {
        expect(MessageConfig.errorNetwork.isNotEmpty, isTrue);
      });

      test('should have server error message', () {
        expect(MessageConfig.errorServer.isNotEmpty, isTrue);
      });

      test('should have timeout error message', () {
        expect(MessageConfig.errorTimeout.isNotEmpty, isTrue);
      });

      test('should have unknown error message', () {
        expect(MessageConfig.errorUnknown.isNotEmpty, isTrue);
      });

      test('should have validation error message', () {
        expect(MessageConfig.errorValidation.isNotEmpty, isTrue);
      });

      test('should have permission error message', () {
        expect(MessageConfig.errorPermission.isNotEmpty, isTrue);
      });

      test('should have not found error message', () {
        expect(MessageConfig.errorNotFound.isNotEmpty, isTrue);
      });

      test('should have duplicate error message', () {
        expect(MessageConfig.errorDuplicate.isNotEmpty, isTrue);
      });
    });

    group('success messages', () {
      test('should have save success message', () {
        expect(MessageConfig.successSave.isNotEmpty, isTrue);
      });

      test('should have delete success message', () {
        expect(MessageConfig.successDelete.isNotEmpty, isTrue);
      });

      test('should have update success message', () {
        expect(MessageConfig.successUpdate.isNotEmpty, isTrue);
      });

      test('should have import success message', () {
        expect(MessageConfig.successImport.isNotEmpty, isTrue);
      });

      test('should have export success message', () {
        expect(MessageConfig.successExport.isNotEmpty, isTrue);
      });

      test('should have backup success message', () {
        expect(MessageConfig.successBackup.isNotEmpty, isTrue);
      });

      test('should have restore success message', () {
        expect(MessageConfig.successRestore.isNotEmpty, isTrue);
      });
    });

    group('hint messages', () {
      test('should have search hint', () {
        expect(MessageConfig.hintSearch.isNotEmpty, isTrue);
      });

      test('should have comment hint', () {
        expect(MessageConfig.hintComment.isNotEmpty, isTrue);
      });

      test('should have URL hint', () {
        expect(MessageConfig.hintUrl.isNotEmpty, isTrue);
      });

      test('should have tag hint', () {
        expect(MessageConfig.hintTag.isNotEmpty, isTrue);
      });

      test('should have title hint', () {
        expect(MessageConfig.hintTitle.isNotEmpty, isTrue);
      });

      test('should have content hint', () {
        expect(MessageConfig.hintContent.isNotEmpty, isTrue);
      });
    });

    group('empty messages', () {
      test('should have empty articles message', () {
        expect(MessageConfig.emptyArticles.isNotEmpty, isTrue);
      });

      test('should have empty diary message', () {
        expect(MessageConfig.emptyDiary.isNotEmpty, isTrue);
      });

      test('should have empty books message', () {
        expect(MessageConfig.emptyBooks.isNotEmpty, isTrue);
      });

      test('should have empty tags message', () {
        expect(MessageConfig.emptyTags.isNotEmpty, isTrue);
      });

      test('should have empty search message', () {
        expect(MessageConfig.emptySearch.isNotEmpty, isTrue);
      });

      test('should have empty favorites message', () {
        expect(MessageConfig.emptyFavorites.isNotEmpty, isTrue);
      });
    });

    group('placeholder messages', () {
      test('should have placeholder title', () {
        expect(MessageConfig.placeholderTitle.isNotEmpty, isTrue);
      });

      test('should have placeholder content', () {
        expect(MessageConfig.placeholderContent.isNotEmpty, isTrue);
      });

      test('should have placeholder summary', () {
        expect(MessageConfig.placeholderSummary.isNotEmpty, isTrue);
      });

      test('should have placeholder URL', () {
        expect(MessageConfig.placeholderUrl.isNotEmpty, isTrue);
        expect(MessageConfig.placeholderUrl.startsWith('https://'), isTrue);
      });

      test('should have placeholder date', () {
        expect(MessageConfig.placeholderDate.isNotEmpty, isTrue);
      });

      test('should have placeholder author', () {
        expect(MessageConfig.placeholderAuthor.isNotEmpty, isTrue);
      });
    });

    group('book messages', () {
      test('should have add book title', () {
        expect(MessageConfig.addBookTitle.isNotEmpty, isTrue);
      });

      test('should have add book hint', () {
        expect(MessageConfig.addBookHint.isNotEmpty, isTrue);
      });

      test('should have add book confirm text', () {
        expect(MessageConfig.addBookConfirm.isNotEmpty, isTrue);
      });

      test('should have add book cancel text', () {
        expect(MessageConfig.addBookCancel.isNotEmpty, isTrue);
      });
    });
  });
}
