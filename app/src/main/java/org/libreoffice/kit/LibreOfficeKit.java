package org.libreoffice.kit;

import android.content.res.AssetManager;
import java.nio.ByteBuffer;

/** JNI names are fixed by the LibreOffice Android bootstrap. */
public final class LibreOfficeKit {
    private LibreOfficeKit() {}
    public static native boolean initializeNative(String dataDir,String cacheDir,String apkFile,AssetManager assets);
    public static native ByteBuffer getLibreOfficeKitHandle();
    public static native void putenv(String entry);
    public static native void redirectStdio(boolean enabled);
}
