package com.dailysatori.encryption;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.IOException;

/** Test APK only. Java is required: the separate provider UID does not load target APK Kotlin. */
public class FixtureDocumentsProvider extends DocumentsProvider {
    private File root() {
        File root = new File(getContext().getFilesDir(), "encryption-documents");
        root.mkdirs();
        return root;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        String[] cols = projection != null ? projection : new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS};
        MatrixCursor cursor = new MatrixCursor(cols);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for(String col : cols) {
            Object value = null;
            if(col.equals(Root.COLUMN_ROOT_ID) || col.equals(Root.COLUMN_DOCUMENT_ID)) value = "root";
            if(col.equals(Root.COLUMN_TITLE)) value = "Encryption lab";
            if(col.equals(Root.COLUMN_FLAGS)) value = Root.FLAG_SUPPORTS_CREATE;
            row.add(col, value);
        }
        return cursor;
    }
    @Override public Cursor queryDocument(String id, String[] projection) { return documents(new File[]{resolve(id)}, projection); }
    @Override public Cursor queryChildDocuments(String id, String[] projection, String order) { return documents(resolve(id).listFiles(), projection); }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws java.io.FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(id), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String createDocument(String parent, String mime, String name) {
        validateName(name);
        File file = new File(resolve(parent), name);
        try {
            if(!(mime.equals(Document.MIME_TYPE_DIR) ? file.mkdir() : file.createNewFile())) throw new IllegalStateException("Cannot create fixture document");
        } catch(IOException e) { throw new IllegalStateException(e); }
        return id(file);
    }
    @Override public String renameDocument(String original, String name) {
        validateName(name);
        File from = resolve(original), to = new File(from.getParentFile(), name);
        if(!from.renameTo(to)) throw new IllegalStateException("Rename failed");
        return id(to);
    }
    @Override public void deleteDocument(String id) { if(!resolve(id).delete()) throw new IllegalStateException("Delete failed"); }
    @Override public boolean isChildDocument(String parent, String child) { return resolve(child).getPath().startsWith(resolve(parent).getPath() + "/"); }
    private File resolve(String id) {
        try {
            File base = root().getCanonicalFile();
            File file = (id.equals("root") ? base : new File(base, id)).getCanonicalFile();
            if(!file.equals(base) && !file.getPath().startsWith(base.getPath() + "/")) throw new IllegalArgumentException("Invalid document ID");
            return file;
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    private String id(File file) {
        try {
            String base = root().getCanonicalPath(), path = file.getCanonicalPath();
            return path.equals(base) ? "root" : path.substring(base.length() + 1);
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    private void validateName(String name) { if(name.contains("/") || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("Invalid name"); }
    private Cursor documents(File[] files, String[] projection) {
        String[] cols = projection != null ? projection : new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS};
        MatrixCursor cursor = new MatrixCursor(cols);
        if(files == null) return cursor;
        for(File file : files) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            for(String col : cols) {
                Object value = null;
                if(col.equals(Document.COLUMN_DOCUMENT_ID)) value = id(file);
                if(col.equals(Document.COLUMN_DISPLAY_NAME)) value = file.getName();
                if(col.equals(Document.COLUMN_MIME_TYPE)) value = file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream";
                if(col.equals(Document.COLUMN_SIZE)) value = file.length();
                if(col.equals(Document.COLUMN_FLAGS)) value = file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_RENAME | Document.FLAG_SUPPORTS_DELETE;
                row.add(col, value);
            }
        }
        return cursor;
    }
}
