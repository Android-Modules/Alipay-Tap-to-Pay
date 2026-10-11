package de.robv.android.xposed;

import java.io.File;
import java.util.Map;
import java.util.Set;

/** Compile-time stub. The real implementation is provided by the Xposed framework at runtime. */
public class XSharedPreferences implements android.content.SharedPreferences {

    public XSharedPreferences(String packageName) {
        throw new IllegalStateException("stub");
    }

    public XSharedPreferences(String packageName, String prefFileName) {
        throw new IllegalStateException("stub");
    }

    public XSharedPreferences(File prefFile) {
        throw new IllegalStateException("stub");
    }

    public static void makeWorldReadable(File file) {}

    public boolean getFileCanRead() {
        throw new IllegalStateException("stub");
    }

    public long getFileLastModifiedTime() {
        throw new IllegalStateException("stub");
    }

    public File getFile() {
        throw new IllegalStateException("stub");
    }

    public void reload() {}

    public boolean hasFileChanged() {
        throw new IllegalStateException("stub");
    }

    @Override
    public Map<String, ?> getAll() {
        throw new IllegalStateException("stub");
    }

    @Override
    public String getString(String key, String defValue) {
        throw new IllegalStateException("stub");
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        throw new IllegalStateException("stub");
    }

    @Override
    public int getInt(String key, int defValue) {
        throw new IllegalStateException("stub");
    }

    @Override
    public long getLong(String key, long defValue) {
        throw new IllegalStateException("stub");
    }

    @Override
    public float getFloat(String key, float defValue) {
        throw new IllegalStateException("stub");
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        throw new IllegalStateException("stub");
    }

    @Override
    public boolean contains(String key) {
        throw new IllegalStateException("stub");
    }

    @Override
    public Editor edit() {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
}
