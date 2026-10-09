package com.dailysatori.encryption;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Grants the test provider's tree from its actual owning UID, like a documents picker. */
public class FixtureGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri tree = DocumentsContract.buildTreeDocumentUri("com.dailysatori.test.encryption.documents", "root");
        grantUriPermission("com.dailysatori", tree, Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
