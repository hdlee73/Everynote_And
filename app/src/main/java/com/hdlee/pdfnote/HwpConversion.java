package com.hdlee.pdfnote;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.*;

/** Local-only HWP/HWPX to PDF bridge. Only bundled assets and the chosen file are exposed. */
final class HwpConversion {
    interface Callback { void status(String text); void success(File pdf); void failure(String reason); }
    private final Activity activity;
    private final ViewGroup parent;
    private final Callback callback;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile boolean ended;
    private WebView web;
    private File input,output;
    private OutputStream stream;
    private long expected,written;
    private final Runnable timeout=()->fail("변환 시간이 초과되었습니다");
    private final String[] fonts={"/system/fonts/NotoSansCJK-Regular.ttc","/system/fonts/Roboto-Regular.ttf","/system/fonts/NotoSerifCJK-Regular.ttc"};

    HwpConversion(Activity activity,ViewGroup parent,Callback callback){this.activity=activity;this.parent=parent;this.callback=callback;}
    void start(Uri source){
        ui.postDelayed(timeout,180000);
        new Thread(()->{
            try{
                synchronized(this){if(ended)return;input=File.createTempFile("hwp-input-",".bin",activity.getCacheDir());output=File.createTempFile("hwp-result-",".pdf",activity.getCacheDir());}
                try(InputStream in=activity.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(input)){
                    if(in==null)throw new IOException("문서를 읽을 수 없습니다");byte[] buf=new byte[65536];int n;long size=0;
                    while((n=in.read(buf))!=-1){if(ended)return;size+=n;if(size>48L*1024*1024)throw new IOException("48MB 이하의 한글 문서를 선택하세요");out.write(buf,0,n);}
                }
                ui.post(this::launch);
            }catch(Exception e){fail(e.getMessage());}
        },"hwp-input").start();
    }
    private void launch(){
        if(ended||activity.isFinishing()||activity.isDestroyed()){cancel();return;}
        try{
            web=new WebView(activity);web.setVisibility(View.INVISIBLE);parent.addView(web,new ViewGroup.LayoutParams(1,1));
            web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
            web.getSettings().setBlockNetworkLoads(true);web.addJavascriptInterface(new Bridge(),"NativePdf");
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    Uri u=request.getUrl();String path=u.getPath();
                    if(!"https".equals(u.getScheme())||!"pdfnote.local".equals(u.getHost())||path==null)return missing();
                    try{
                        if(path.equals("/hwp/document"))return response("application/octet-stream",new FileInputStream(input));
                        for(int i=0;i<fonts.length;i++)if(path.equals("/hwp/font"+i))return response("font/ttf",new FileInputStream(fonts[i]));
                        String name=path.substring(path.lastIndexOf('/')+1);
                        if(!name.equals("convert.html")&&!name.equals("rhwptopdf.umd.js")&&!name.equals("rhwptopdf.umd_bg.wasm"))return missing();
                        return response(name.endsWith(".wasm")?"application/wasm":name.endsWith(".js")?"application/javascript":"text/html",activity.getAssets().open("hwp/"+name));
                    }catch(Exception e){return missing();}
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
                @Override public void onPageFinished(WebView view,String url){if(!ended&&url.equals("https://pdfnote.local/hwp/convert.html"))view.evaluateJavascript("convertDocument()",null);}
                @Override public boolean onRenderProcessGone(WebView view,android.webkit.RenderProcessGoneDetail detail){fail("변환 엔진이 종료되었습니다. 더 작은 문서로 시도하세요");return true;}
            });
            web.loadUrl("https://pdfnote.local/hwp/convert.html");
        }catch(Exception e){fail(e.getMessage());}
    }
    /** True when the converted PDF looks like printed two-page spreads: wide landscape pages, or landscape pages whose text sits in two halves with an empty gutter down the middle. */
    static boolean looksLikeSpread(Activity activity,File pdf){
        try{com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(activity.getApplicationContext());
            try(com.tom_roush.pdfbox.pdmodel.PDDocument doc=com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf,com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())){
                int n=Math.min(6,doc.getNumberOfPages());if(n==0)return false;
                int wide=0,landscape=0,judged=0,gutter=0;
                for(int i=0;i<n;i++){com.tom_roush.pdfbox.pdmodel.PDPage page=doc.getPage(i);com.tom_roush.pdfbox.pdmodel.common.PDRectangle box=page.getCropBox();int rot=((page.getRotation()%360)+360)%360;float w=rot%180==0?box.getWidth():box.getHeight(),h=rot%180==0?box.getHeight():box.getWidth();
                    if(w<=h*1.2f)continue;landscape++;if(w>=1000f&&w>h*1.25f)wide++;
                    int[] c=centersShare(doc,i+1,rot%180==0);if(c==null)continue;judged++;if(c[0]>=250&&c[1]>=250&&c[2]<=15)gutter++;}
                if(landscape==0)return false;
                if(wide==landscape)return true;
                return judged>0&&gutter*10>=judged*7;}
        }catch(Throwable e){return false;}
    }
    /** Share of characters (in tenths of a percent) in the left half, the right half and the 45–55% centre band of a page; null when the page has too little text. */
    private static int[] centersShare(com.tom_roush.pdfbox.pdmodel.PDDocument doc,int pageNumber,boolean upright){
        try{final int[] count=new int[3];final int[] total={0};
            com.tom_roush.pdfbox.text.PDFTextStripper stripper=new com.tom_roush.pdfbox.text.PDFTextStripper(){
                @Override protected void writeString(String text,java.util.List<com.tom_roush.pdfbox.text.TextPosition> positions){
                    for(com.tom_roush.pdfbox.text.TextPosition t:positions){if(t.getUnicode()==null||t.getUnicode().trim().isEmpty())continue;float pw=getCurrentPage().getCropBox().getWidth();float x=(t.getXDirAdj()+t.getWidthDirAdj()/2f)/pw;total[0]++;if(x>=.45f&&x<=.55f)count[2]++;else if(x<.5f)count[0]++;else count[1]++;}
                }};
            stripper.setStartPage(pageNumber);stripper.setEndPage(pageNumber);stripper.getText(doc);
            if(!upright||total[0]<30)return null;
            return new int[]{count[0]*1000/total[0],count[1]*1000/total[0],count[2]*1000/total[0]};
        }catch(Throwable e){return null;}
    }
    /** Cuts every page in the middle into a left and a right page (same content, two crop boxes) and writes the result to {@code out}. */
    static void splitSpreads(Activity activity,File pdf,File out) throws IOException{
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(activity.getApplicationContext());
        try(com.tom_roush.pdfbox.pdmodel.PDDocument doc=com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf,com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())){
            java.util.List<com.tom_roush.pdfbox.pdmodel.PDPage> pages=new java.util.ArrayList<>();for(com.tom_roush.pdfbox.pdmodel.PDPage page:doc.getPages())pages.add(page);
            for(com.tom_roush.pdfbox.pdmodel.PDPage page:pages){
                com.tom_roush.pdfbox.pdmodel.common.PDRectangle box=page.getCropBox();if(page.getRotation()%180!=0||box.getWidth()<=box.getHeight())continue;
                float mid=box.getLowerLeftX()+box.getWidth()/2f;
                com.tom_roush.pdfbox.pdmodel.PDPage right=new com.tom_roush.pdfbox.pdmodel.PDPage(new com.tom_roush.pdfbox.cos.COSDictionary(page.getCOSObject()));
                com.tom_roush.pdfbox.pdmodel.common.PDRectangle l=new com.tom_roush.pdfbox.pdmodel.common.PDRectangle(box.getLowerLeftX(),box.getLowerLeftY(),mid-box.getLowerLeftX(),box.getHeight());
                com.tom_roush.pdfbox.pdmodel.common.PDRectangle r=new com.tom_roush.pdfbox.pdmodel.common.PDRectangle(mid,box.getLowerLeftY(),box.getUpperRightX()-mid,box.getHeight());
                page.setMediaBox(l);page.setCropBox(l);right.setMediaBox(r);right.setCropBox(r);
                doc.getPages().insertAfter(right,page);
            }
            doc.save(out);
        }
    }
    private static WebResourceResponse response(String mime,InputStream in){return new WebResourceResponse(mime,"UTF-8",in);}
    private static WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private void dispose(){ui.removeCallbacks(timeout);if(web!=null){parent.removeView(web);web.removeJavascriptInterface("NativePdf");web.stopLoading();web.destroy();web=null;}if(input!=null)input.delete();}
    synchronized void cancel(){if(ended)return;ended=true;closeStream();if(output!=null)output.delete();ui.post(this::dispose);}
    private void closeStream(){if(stream!=null)try{stream.close();}catch(IOException ignored){}stream=null;}
    private synchronized void fail(String reason){if(ended)return;ended=true;closeStream();if(output!=null)output.delete();ui.post(()->{dispose();callback.failure(reason==null?"알 수 없는 오류":reason);});}
    final class Bridge {
        @JavascriptInterface public void status(String text){ui.post(()->{if(!ended)callback.status(text);});}
        @JavascriptInterface public boolean begin(int size){synchronized(HwpConversion.this){if(ended)return false;try{if(size<5||size>128*1024*1024)throw new IOException("변환 결과가 너무 크거나 비어 있습니다");expected=size;written=0;stream=new FileOutputStream(output);return true;}catch(Exception e){fail(e.getMessage());return false;}}}
        @JavascriptInterface public boolean chunk(String text){synchronized(HwpConversion.this){if(ended)return false;try{if(stream==null||text.length()>45000)throw new IOException("잘못된 PDF 데이터");byte[] bytes=Base64.decode(text,Base64.NO_WRAP);written+=bytes.length;if(written>expected)throw new IOException("PDF 크기가 일치하지 않습니다");stream.write(bytes);return true;}catch(Exception e){fail(e.getMessage());return false;}}}
        @JavascriptInterface public void complete(){synchronized(HwpConversion.this){if(ended)return;try{closeStream();if(written!=expected)throw new IOException("PDF 저장이 완료되지 않았습니다");try(InputStream in=new FileInputStream(output)){byte[] head=new byte[5];if(in.read(head)!=5||!new String(head,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("변환 결과가 PDF가 아닙니다");}ended=true;ui.post(()->{dispose();callback.success(output);});}catch(Exception e){fail(e.getMessage());}}}
        @JavascriptInterface public void error(String reason){fail(reason);}
    }
}
