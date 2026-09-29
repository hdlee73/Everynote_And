package org.libreoffice.kit;

import java.nio.ByteBuffer;

/** Minimal LibreOfficeKit JNI binding. */
public final class Office {
    public final ByteBuffer handle;
    public static final long FEATURE_DOCUMENT_PASSWORD=1L;
    public static final int LOK_CALLBACK_DOCUMENT_PASSWORD=20;
    public interface Callback { void onMessage(int type,String payload); }
    public volatile Callback callback;
    public Office(ByteBuffer pointer){handle=pointer;bindMessageCallback();}
    private native void bindMessageCallback();
    public native String getError();
    private native ByteBuffer documentLoadNative(String url);
    public Document documentLoad(String url){ByteBuffer p=documentLoadNative(url);return p==null?null:new Document(p);}
    public native void setDocumentPassword(String url,String password);
    public native void setOptionalFeatures(long features);
    public native void destroy();
    private void messageRetrievedLOKit(int type,String payload){Callback c=callback;if(c!=null)c.onMessage(type,payload);}
}
