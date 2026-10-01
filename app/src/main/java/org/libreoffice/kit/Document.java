package org.libreoffice.kit;

import java.nio.ByteBuffer;

/** Minimal LibreOfficeKit document binding. */
public final class Document {
    public final ByteBuffer handle;
    public Document(ByteBuffer pointer){handle=pointer;bindMessageCallback();}
    private native void bindMessageCallback();
    public native void destroy();
    public native void saveAs(String url,String format,String options);
    private void messageRetrieved(int type,String payload){}
}
