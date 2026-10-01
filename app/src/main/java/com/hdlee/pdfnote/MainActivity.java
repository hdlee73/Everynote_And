package com.hdlee.pdfnote;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.common.model.DownloadConditions;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity implements PdfPageView.Listener {
    private final class PageListener implements PdfPageView.Listener {
        PdfPageView view;
        private void active(){if(view!=null&&renderer!=null&&pageView!=view){pageView=view;currentPage=view.getPageNumber();activeSession.page=currentPage;updateBookmarkButton();refreshStudyPanel();}}
        @Override public void onHighlightCreated(AnnotationStore.Mark mark){active();MainActivity.this.onHighlightCreated(mark);}
        @Override public void onMarkTapped(AnnotationStore.Mark mark){active();MainActivity.this.onMarkTapped(mark);}
        @Override public void onMemoPointRequested(int page, float x, float y){active();MainActivity.this.onMemoPointRequested(page,x,y);}
        @Override public void onZoomGestureStarted(){active();MainActivity.this.onZoomGestureStarted();}
        @Override public void onPageSwipe(int direction){active();MainActivity.this.onPageSwipe(direction);}
        @Override public void onOutlinePointRequested(int page, float x, float y){active();MainActivity.this.onOutlinePointRequested(page,x,y);}
        @Override public void onInkChanged(){active();MainActivity.this.onInkChanged();}
        @Override public void onTextSelectionFinished(PdfPageView.TextSelection selection, float anchorX, float anchorY){active();MainActivity.this.onTextSelectionFinished(selection,anchorX,anchorY);}
        @Override public void onTranslationTapped(AnnotationStore.TranslationNote note){active();MainActivity.this.onTranslationTapped(note);}
        @Override public void onSelectionAdjustStarted(){active();MainActivity.this.onSelectionAdjustStarted();}
        @Override public void onLassoSelectionFinished(){active();MainActivity.this.onLassoSelectionFinished();}
        @Override public void onElementTapped(AnnotationStore.PageElement element){active();MainActivity.this.onElementTapped(element);}
    }
    private static final class DocumentSession {
        Uri uri; String title; ParcelFileDescriptor descriptor; PdfRenderer renderer;
        AnnotationStore store; int page; File officePreview;
        final Deque<AnnotationStore.InkStroke> redoStrokes=new ArrayDeque<>();
        final Map<Integer,List<PdfPageView.TextRegion>> textRegions=new HashMap<>();
    }
    private static final int EXPORT_PDF=30,IMPORT_IMAGE=31;
    private LibraryRepository library; private LibraryDialog libraryDialog;
    private final Set<String> importing=new HashSet<>(); private final Set<DocumentSession> appending=new HashSet<>(); private boolean restoringSessions;
    private File libraryFolder;private Uri exportSource;private String exportSnapshot;private int exportPageCount;
    private String placementKind="",placementAsset="";
    private java.util.concurrent.atomic.AtomicBoolean searchCanceled;
    private static final int OPEN_PDF=10, EXPORT_JSON=11, NAVY=0xFF173B63, ACCENT=0xFF2563EB;
    private PdfRenderer renderer; private ParcelFileDescriptor descriptor; private Uri documentUri;
    private String documentTitle="PDF"; private int currentPage, selectedColor=0x66FFDE59;
    private PdfPageView pageView,firstPageView,secondPageView;
    private boolean twoPage; private TextView titleView,pageLabel;
    private ImageButton bookmarkButton,memoButton,inkButton,fullscreenExit,previousOverlay,nextOverlay; private LinearLayout header,bottomBar;
    private FrameLayout root; private AnnotationStore store; private boolean highlightMode,memoMode,outlineMode,fullscreen;
    private boolean verticalPageSwipe;
    private boolean fingerInk,swipeEnabled;
    private HwpConversion hwpConversion;
    private final List<DocumentSession> sessions=new ArrayList<>(); private DocumentSession activeSession;
    private LinearLayout tabRow,thumbnailList; private HorizontalScrollView tabStrip; private ScrollView thumbnailPanel;
    private int thumbnailGeneration; private boolean sidebarVisible;
    private int inkMode,inkColor=0xFF172033; private float inkWidth=0.004f;
    private SharedPreferences recentPrefs;
    private TextRecognizer latinRecognizer,koreanRecognizer;
    private int ocrGeneration;
    private static final int TRANSLATE_EXTERNAL=12;
    private String pendingSource;
    private RectF pendingBounds;
    private DocumentSession pendingSession;
    private int pendingPage;
    private TextToSpeech speech;
    private boolean speechReady;
    private String speechPending;
    private Locale speechLocale=Locale.US;
    private boolean pageAnimating;
    private LinearLayout studySplit, studyRows, studyPanel;
    private FrameLayout pdfArea;
    private TextView studyHeading;
    private boolean studyVisible, basketOnly;
    private static final int EXPORT_STUDY=20, IMPORT_SIDECAR=21;
    private static final int EXPORT_CAPTURE=22;
    private File pendingCaptureExport;
    private byte[] pendingExport;
    private AnnotationStore importTarget;
    private DocumentSession importSession;
    private String pendingJsonExport;
    private boolean awaitingOfficeReturn;
    private boolean officeConverting;

    private void translateText(String source,RectF bounds){
        Intent intent=new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
        List<android.content.pm.ResolveInfo> handlers=getPackageManager().queryIntentActivities(intent,0);
        android.content.pm.ActivityInfo translation=null;
        for(android.content.pm.ResolveInfo handler:handlers){
            if(!handler.activityInfo.exported)continue;
            String name=handler.loadLabel(getPackageManager()).toString().trim();
            if(name.equalsIgnoreCase("Translate")||name.equals("번역")){
                translation=handler.activityInfo;
                if(handler.activityInfo.packageName.equals("com.google.android.apps.translate"))break;
            }
        }
        if(translation==null){
            new AlertDialog.Builder(this).setTitle("Translate 앱을 찾을 수 없습니다")
                .setMessage("기기의 텍스트 선택 메뉴에 Translate가 나타나면 해당 앱을 활성화하세요. 지금은 기기 내 번역을 사용할 수 있습니다.")
                .setPositiveButton("기기 내 번역",(d,w)->translateOffline(source,bounds)).setNegativeButton("닫기",null).show();return;
        }
        pendingSource=source;pendingBounds=new RectF(bounds);pendingSession=activeSession;pendingPage=currentPage;
        Intent request=new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .setClassName(translation.packageName,translation.name)
            .putExtra(Intent.EXTRA_PROCESS_TEXT,source)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,false);
        try{startActivityForResult(request,TRANSLATE_EXTERNAL);}
        catch(ActivityNotFoundException|SecurityException error){pendingSource=null;pendingSession=null;toast("Translate를 열 수 없습니다");}
    }

    private void readAloud(String source){
        String[] labels={"미국식 영어", "영국식 영어", "호주식 영어", "한국어", "읽기 중지"};
        Locale[] locales={Locale.US,Locale.UK,new Locale("en","AU"),Locale.KOREAN};
        new AlertDialog.Builder(this).setTitle("읽어주기 · 발음 선택").setItems(labels,(d,index)->{
            if(index==4){if(speech!=null)speech.stop();return;}
            speechLocale=locales[index];speechPending=source;
            if(speech==null){speech=new TextToSpeech(getApplicationContext(),status->{speechReady=status==TextToSpeech.SUCCESS;
                if(speechReady)runOnUiThread(this::speakPending);else runOnUiThread(()->toast("음성 엔진을 시작할 수 없습니다"));});}
            else if(speechReady)speakPending();
        }).show();
    }

    private void speakPending(){
        if(speech==null||!speechReady||speechPending==null)return;
        int available=speech.setLanguage(speechLocale);
        if(available==TextToSpeech.LANG_MISSING_DATA||available==TextToSpeech.LANG_NOT_SUPPORTED){toast("선택한 발음의 음성이 기기에 없습니다");speechPending=null;return;}
        speech.speak(speechPending,TextToSpeech.QUEUE_FLUSH,null,"pdf-note-selection");speechPending=null;
    }

    private void receiveExternalTranslation(int resultCode,Intent data){
        if(pendingSource==null||pendingSession==null)return;
        String source=pendingSource;RectF bounds=new RectF(pendingBounds);
        DocumentSession target=pendingSession;int page=pendingPage;
        pendingSource=null;pendingSession=null;
        if(!sessions.contains(target)){toast("원래 PDF를 다시 열어 번역하세요");return;}
        switchDocument(target);showPage(page);
        CharSequence translated=resultCode==RESULT_OK&&data!=null?data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT):null;
        showTranslationResult(source,translated==null?"":translated.toString(),bounds);
    }

    @Override protected void onCreate(Bundle state){super.onCreate(state);recentPrefs=getSharedPreferences("recent_documents",MODE_PRIVATE);verticalPageSwipe=recentPrefs.getBoolean("vertical_page_swipe",false);fingerInk=recentPrefs.getBoolean("finger_ink",false);swipeEnabled=recentPrefs.getBoolean("page_swipe_enabled_v2",true);twoPage=recentPrefs.getBoolean("two_page",false);library=new LibraryRepository(this);com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());libraryFolder=library.root;buildUi();pageView.setFingerInk(fingerInk);pageView.setPageSwipeEnabled(swipeEnabled);pageView.setVerticalPageSwipe(verticalPageSwipe);syncOtherTools();Uri u=getIntent().getData();if(u!=null)openPdf(u);else if(!restoreSession())showWelcome();}
    @Override protected void onResume(){super.onResume();if(awaitingOfficeReturn){awaitingOfficeReturn=false;root.post(()->new AlertDialog.Builder(this).setTitle("문서로 돌아왔습니다")
        .setMessage("문서 앱에서 PDF로 내보냈다면 파일을 가져와 필기와 주석을 이어갈 수 있습니다.")
        .setPositiveButton("PDF 가져오기",(d,w)->chooseConvertedPdf()).setNegativeButton("나중에",null).show());}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private ImageButton icon(int image,String label,int tint,View.OnClickListener click){ImageButton b=new ImageButton(this);b.setImageResource(image);b.setColorFilter(tint);b.setContentDescription(label);b.setBackgroundColor(Color.TRANSPARENT);b.setScaleType(ImageView.ScaleType.CENTER);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setOnClickListener(click);return b;}

    private void buildUi(){
        root=new FrameLayout(this);root.setBackgroundColor(0xFFF1F3F5);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(4),0,dp(4),0);header.setBackgroundColor(NAVY);
        header.addView(icon(R.drawable.ic_folder_open,"문서함",Color.WHITE,v->showLibrary()),new LinearLayout.LayoutParams(dp(52),dp(56)));
        titleView=new TextView(this);titleView.setTextColor(Color.WHITE);titleView.setTextSize(19);titleView.setSingleLine();titleView.setGravity(Gravity.CENTER_VERTICAL);titleView.setPadding(dp(8),0,dp(8),0);header.addView(titleView,new LinearLayout.LayoutParams(0,dp(56),1));
        header.addView(icon(R.drawable.ic_thumbnails,"페이지 목록",Color.WHITE,v->toggleSidebar()),new LinearLayout.LayoutParams(dp(48),dp(56)));
        header.addView(icon(R.drawable.ic_search,"문서·필기 검색",Color.WHITE,v->searchDocument()),new LinearLayout.LayoutParams(dp(48),dp(56)));
        header.addView(icon(R.drawable.ic_fullscreen,"전체 화면",Color.WHITE,v->toggleFullscreen()),new LinearLayout.LayoutParams(dp(48),dp(56)));
        header.addView(icon(R.drawable.ic_more_vert,"도구",Color.WHITE,v->showTools()),new LinearLayout.LayoutParams(dp(48),dp(56)));content.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        tabStrip=new HorizontalScrollView(this);tabStrip.setHorizontalScrollBarEnabled(false);tabStrip.setBackgroundColor(0xFFF8FAFC);tabRow=new LinearLayout(this);tabRow.setGravity(Gravity.CENTER_VERTICAL);tabRow.setPadding(dp(6),dp(4),dp(6),dp(4));tabStrip.addView(tabRow,new HorizontalScrollView.LayoutParams(-2,-1));content.addView(tabStrip,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout viewerRow=new LinearLayout(this);viewerRow.setOrientation(LinearLayout.HORIZONTAL);
        thumbnailPanel=new ScrollView(this);thumbnailPanel.setBackgroundColor(0xFFF1F5F9);thumbnailPanel.setVisibility(View.GONE);thumbnailList=new LinearLayout(this);thumbnailList.setOrientation(LinearLayout.VERTICAL);thumbnailList.setPadding(dp(7),dp(8),dp(7),dp(8));thumbnailPanel.addView(thumbnailList,new ScrollView.LayoutParams(-1,-2));viewerRow.addView(thumbnailPanel,new LinearLayout.LayoutParams(dp(116),-1));
        FrameLayout viewport=new FrameLayout(this);LinearLayout papers=new LinearLayout(this);
        PageListener firstListener=new PageListener(),secondListener=new PageListener();
        firstPageView=new PdfPageView(this,firstListener);secondPageView=new PdfPageView(this,secondListener);firstListener.view=firstPageView;secondListener.view=secondPageView;pageView=firstPageView;
        papers.addView(firstPageView,new LinearLayout.LayoutParams(0,-1,1));papers.addView(secondPageView,new LinearLayout.LayoutParams(0,-1,1));secondPageView.setVisibility(twoPage?View.VISIBLE:View.GONE);viewport.addView(papers,new FrameLayout.LayoutParams(-1,-1));
        previousOverlay=icon(R.drawable.ic_chevron_left,"이전 페이지",NAVY,v->animatePage(-1));nextOverlay=icon(R.drawable.ic_chevron_right,"다음 페이지",NAVY,v->animatePage(1));
        ImageButton[] arrows={previousOverlay,nextOverlay};for(int i=0;i<arrows.length;i++){ImageButton arrow=arrows[i];arrow.setBackground(round(0xDDF8FAFC,26));arrow.setAlpha(0.38f);arrow.setElevation(dp(3));FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.CENTER_VERTICAL|(i==0?Gravity.START:Gravity.END));ap.setMargins(dp(8),0,dp(8),0);viewport.addView(arrow,ap);}
        viewerRow.addView(viewport,new LinearLayout.LayoutParams(0,-1,1));
        pdfArea=new FrameLayout(this);pdfArea.addView(viewerRow,new FrameLayout.LayoutParams(-1,-1));
        studySplit=new LinearLayout(this);studySplit.addView(pdfArea);buildStudyPanel();studySplit.addView(studyPanel);
        content.addView(studySplit,new LinearLayout.LayoutParams(-1,0,1));layoutStudyPanel();
        bottomBar=new LinearLayout(this);bottomBar.setGravity(Gravity.CENTER_VERTICAL);bottomBar.setPadding(dp(4),dp(4),dp(4),dp(4));bottomBar.setBackgroundColor(Color.WHITE);bottomBar.setElevation(dp(10));
        pageLabel=new TextView(this);pageLabel.setGravity(Gravity.CENTER);pageLabel.setTextColor(0xFF475569);pageLabel.setTextSize(14);pageLabel.setSingleLine();pageLabel.setBackground(round(0xFFF1F5F9,18));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(38),1);pp.setMargins(dp(2),0,dp(2),0);bottomBar.addView(pageLabel,pp);
        memoButton=icon(R.drawable.ic_note_add,"메모 추가",NAVY,v->toggleMemoMode());bottomBar.addView(memoButton,new LinearLayout.LayoutParams(dp(44),dp(56)));
        inkButton=icon(R.drawable.ic_ink,"필기도구",NAVY,v->showInkTools());bottomBar.addView(inkButton,new LinearLayout.LayoutParams(dp(44),dp(56)));
        bookmarkButton=icon(R.drawable.ic_star_outline,"즐겨찾기",NAVY,v->toggleBookmark());bottomBar.addView(bookmarkButton,new LinearLayout.LayoutParams(dp(48),dp(56)));content.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(64)));
        fullscreenExit=icon(R.drawable.ic_fullscreen_exit,"전체 화면 종료",Color.WHITE,v->toggleFullscreen());fullscreenExit.setBackground(round(0xAA0F172A,24));fullscreenExit.setVisibility(View.GONE);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);ep.setMargins(0,dp(12),dp(12),0);root.addView(fullscreenExit,ep);
        root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());top=b.top;bottom=b.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}if(!fullscreen){header.setPadding(dp(4),top,dp(4),0);header.getLayoutParams().height=dp(56)+top;bottomBar.setPadding(dp(4),dp(4),dp(4),dp(4)+bottom);bottomBar.getLayoutParams().height=dp(64)+bottom;}return insets;});setContentView(root);
    }
    private void showWelcome(){previousOverlay.setVisibility(View.GONE);nextOverlay.setVisibility(View.GONE);titleView.setText("PDF Note");pageLabel.setText("PDF를 열어 시작하세요");}
    private void saveSessionState(){if(restoringSessions||!importing.isEmpty())return;try{JSONArray a=new JSONArray();for(DocumentSession s:sessions)a.put(new JSONObject().put("uri",s.uri.toString()).put("page",s==activeSession?currentPage:s.page).put("text_only",s.officePreview!=null));recentPrefs.edit().putString("open_sessions",a.toString()).putString("active_uri",activeSession==null?"":activeSession.uri.toString()).apply();}catch(JSONException ignored){}}
    private boolean restoreSession(){
        String raw=recentPrefs.getString("open_sessions",null);if(raw==null)return false;
        restoringSessions=true;try{JSONArray saved=new JSONArray(raw);String active=recentPrefs.getString("active_uri","");for(int i=0;i<saved.length();i++){JSONObject entry=saved.getJSONObject(i);Uri uri=Uri.parse(entry.getString("uri"));openPdf(uri,entry.optBoolean("text_only",false),Math.max(0,entry.optInt("page",0)),uri.toString().equals(active));}}catch(Exception ignored){}finally{restoringSessions=false;}
        if(importing.isEmpty())saveSessionState();return !sessions.isEmpty()||!importing.isEmpty();
    }
    private void choosePdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","application/x-hwp","application/vnd.hancom.hwp","application/vnd.hancom.hwpx","application/octet-stream"});startActivityForResult(i,OPEN_PDF);}
    private void chooseConvertedPdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");startActivityForResult(i,OPEN_PDF);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==EXPORT_PDF){receivePdfExport(result,data);return;}if(req==IMPORT_IMAGE){if(result==RESULT_OK&&data!=null&&data.getData()!=null)importImage(data.getData());return;}if(req==EXPORT_CAPTURE){receiveCaptureExport(result,data);return;}if(req==EXPORT_STUDY||req==IMPORT_SIDECAR){receiveStudyResult(req,result,data);return;}if(req==TRANSLATE_EXTERNAL){receiveExternalTranslation(result,data);return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(req==OPEN_PDF){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}openPdf(u);}else if(req==EXPORT_JSON){try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){if(out==null||pendingJsonExport==null)throw new IOException("다시 백업하세요");out.write(pendingJsonExport.getBytes(java.nio.charset.StandardCharsets.UTF_8));toast("주석을 내보냈습니다");}catch(Exception e){toast("내보내기 실패: "+e.getMessage());}}}
    private boolean isOfficeDocument(String name){String value=name.toLowerCase(Locale.ROOT);return value.endsWith(".hwp")||value.endsWith(".hwpx")||value.endsWith(".doc")||value.endsWith(".docx")||value.endsWith(".ppt")||value.endsWith(".pptx")||value.endsWith(".xls")||value.endsWith(".xlsx");}
    private boolean canConvertOffice(String name){String lower=name.toLowerCase(Locale.ROOT);return lower.endsWith(".doc")||lower.endsWith(".docx")||lower.endsWith(".ppt")||lower.endsWith(".pptx")||lower.endsWith(".xls")||lower.endsWith(".xlsx");}
    private void convertHwp(Uri source,String name){
        if(officeConverting){toast("다른 문서를 변환하고 있습니다");return;}officeConverting=true;
        TextView status=new TextView(this);status.setText("한글 문서를 준비하고 있습니다");status.setTextSize(16);status.setPadding(dp(24),dp(20),dp(24),dp(20));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("한글 문서 → PDF").setView(status).setCancelable(false).setNegativeButton("취소",null).create();dialog.show();
        hwpConversion=new HwpConversion(this,root,new HwpConversion.Callback(){
            public void status(String text){status.setText(text);}
            public void failure(String reason){hwpConversion=null;officeConverting=false;dialog.dismiss();if(!isFinishing()&&!isDestroyed()){new AlertDialog.Builder(MainActivity.this).setTitle("한글 문서 변환 실패").setMessage("PDF 변환을 완료하지 못했습니다.\n\n"+reason).setPositiveButton("다른 방법으로 열기",(d,w)->offerOfficeImport(source,name)).setNegativeButton("닫기",null).show();}}
            public void success(File pdf){
                hwpConversion=null;status.setText("PDF 사본을 저장하는 중");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                new Thread(()->{try{Uri saved=saveConvertedPdf(pdf,name);runOnUiThread(()->{officeConverting=false;dialog.dismiss();if(!isFinishing()&&!isDestroyed()){openPdf(saved);toast("한글 문서를 PDF로 저장했습니다");}});}
                    catch(Exception e){runOnUiThread(()->{officeConverting=false;dialog.dismiss();if(!isFinishing()&&!isDestroyed())toast("PDF 저장 실패: "+e.getMessage());});}finally{pdf.delete();}},"hwp-save").start();
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{if(hwpConversion!=null)hwpConversion.cancel();hwpConversion=null;officeConverting=false;dialog.dismiss();});
        hwpConversion.start(source);
    }
    private void convertOffice(Uri source,String name){
        if(officeConverting){toast("다른 문서를 변환하고 있습니다");return;}
        if(!OfficeEngine.supported()){offerOfficeImport(source,name);return;}
        officeConverting=true;
        boolean[] finished={false};
        File[] temporary=new File[2];
        android.os.Handler conversionHandler=new android.os.Handler(android.os.Looper.getMainLooper());
        TextView status=new TextView(this);status.setText("문서를 준비하고 있습니다");status.setPadding(dp(24),dp(20),dp(24),dp(20));status.setTextSize(16);
        AlertDialog progress=new AlertDialog.Builder(this).setTitle("PDF로 변환").setView(status).setCancelable(false).setNegativeButton("취소",null).create();progress.show();
        Runnable abort=()->{if(finished[0])return;finished[0]=true;officeConverting=false;stopService(new Intent(this,OfficeConversionService.class));progress.dismiss();for(File f:temporary)if(f!=null)f.delete();toast("변환이 중단되었습니다");};
        progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->abort.run());
        conversionHandler.postDelayed(abort,600000);
        new Thread(()->{
            File input=null,output=null;
            try{
                String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
                input=File.createTempFile("office-source-","."+ext,getCacheDir());
                output=File.createTempFile("office-result-",".pdf",getCacheDir());
                temporary[0]=input;temporary[1]=output;
                try(java.io.InputStream in=getContentResolver().openInputStream(source);
                    java.io.OutputStream out=new java.io.FileOutputStream(input)){
                    if(in==null)throw new IOException("파일을 읽을 수 없습니다");
                    byte[] buf=new byte[65536];int count;while((count=in.read(buf))!=-1)out.write(buf,0,count);
                }
                if(ext.equals("xls")||ext.equals("xlsx"))SpreadsheetImport.prepare(input);
                File finalInput=input,finalOutput=output;
                runOnUiThread(()->{
                    if(finished[0]||isFinishing()||isDestroyed()){finalInput.delete();finalOutput.delete();return;}
                    status.setText("원본 서식을 PDF로 변환하는 중");
                    android.os.ResultReceiver receiver=new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())){
                        @Override protected void onReceiveResult(int code,android.os.Bundle data){
                            if(finished[0])return;
                            if(code==2){status.setText(data.getString("stage","변환하는 중"));return;}
                            finalInput.delete();
                            if(code!=0){finished[0]=true;conversionHandler.removeCallbacks(abort);progress.dismiss();officeConverting=false;finalOutput.delete();toast("자동 변환 실패: "+data.getString("error","알 수 없는 오류"));offerOfficeImport(source,name);return;}
                            conversionHandler.removeCallbacks(abort);
                            status.setText("변환된 PDF를 저장하는 중");progress.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                            new Thread(()->{
                                try{Uri saved=saveConvertedPdf(finalOutput,name);runOnUiThread(()->{finished[0]=true;conversionHandler.removeCallbacks(abort);progress.dismiss();officeConverting=false;if(!isFinishing()&&!isDestroyed()){openPdf(saved);toast("다운로드/PDF Note에 PDF를 저장했습니다");}});}
                                catch(Exception e){runOnUiThread(()->{finished[0]=true;conversionHandler.removeCallbacks(abort);progress.dismiss();officeConverting=false;toast("PDF 저장 실패: "+e.getMessage());});}
                                finally{finalOutput.delete();}
                            },"office-save").start();
                        }
                    };
                    Intent task=new Intent(this,OfficeConversionService.class).putExtra("source",finalInput.getAbsolutePath()).putExtra("output",finalOutput.getAbsolutePath()).putExtra("receiver",receiver);
                    try{startService(task);}catch(Exception e){finished[0]=true;conversionHandler.removeCallbacks(abort);progress.dismiss();officeConverting=false;finalInput.delete();finalOutput.delete();toast("변환을 시작할 수 없습니다");offerOfficeImport(source,name);}
                });
            }catch(Exception e){
                if(input!=null)input.delete();if(output!=null)output.delete();
                String reason=e.getMessage();runOnUiThread(()->{if(finished[0])return;finished[0]=true;conversionHandler.removeCallbacks(abort);progress.dismiss();officeConverting=false;toast("자동 변환 실패: "+reason);offerOfficeImport(source,name);});
            }
        },"office-install").start();
    }
    private Uri saveConvertedPdf(File pdf,String sourceName)throws IOException{
        String base=sourceName.replaceFirst("(?i)\\.(docx?|pptx?)$","").replaceAll("[\\\\/:*?\"<>|]","_");
        String name=base+"-"+System.currentTimeMillis()+".pdf";
        if(Build.VERSION.SDK_INT>=29){
            android.content.ContentValues values=new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/pdf");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS+"/PDF Note");
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,1);
            Uri target=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(target==null)throw new IOException("다운로드 폴더를 만들 수 없습니다");
            try(java.io.InputStream in=new java.io.FileInputStream(pdf);java.io.OutputStream out=getContentResolver().openOutputStream(target)){
                if(out==null)throw new IOException("저장 파일을 열 수 없습니다");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }catch(IOException e){getContentResolver().delete(target,null,null);throw e;}
            android.content.ContentValues complete=new android.content.ContentValues();complete.put(android.provider.MediaStore.MediaColumns.IS_PENDING,0);
            getContentResolver().update(target,complete,null,null);return target;
        }
        File documents=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);if(documents==null)documents=new File(getFilesDir(),"Documents");
        File dir=new File(documents,"PDF Note");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("저장 폴더를 만들 수 없습니다");
        File target=new File(dir,name);
        try(java.io.InputStream in=new java.io.FileInputStream(pdf);java.io.OutputStream out=new java.io.FileOutputStream(target)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
        return Uri.fromFile(target);
    }
    private void openOfficeOriginal(Uri uri,String name){
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String mime=ext.equals("hwp")?"application/x-hwp":ext.equals("hwpx")?"application/vnd.hancom.hwpx":ext.equals("doc")?"application/msword":ext.equals("docx")?"application/vnd.openxmlformats-officedocument.wordprocessingml.document":ext.equals("ppt")?"application/vnd.ms-powerpoint":"application/vnd.openxmlformats-officedocument.presentationml.presentation";
        Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser=Intent.createChooser(view,"원본 문서 보기");
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,new ComponentName[]{new ComponentName(this,MainActivity.class)});
        try{awaitingOfficeReturn=true;startActivity(chooser);}
        catch(ActivityNotFoundException e){awaitingOfficeReturn=false;toast("원본 형식을 열 수 있는 문서 앱이 없습니다");}
    }
    private void offerOfficeImport(Uri uri,String name){
        boolean textPreview=OfficeImporter.isOffice(name);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(name)
            .setMessage("원본 서식·표·그림은 설치된 문서 앱에서 확인할 수 있습니다. 해당 앱에서 PDF로 내보낸 뒤 다시 가져오면 PDF Note에서 필기와 주석을 사용할 수 있습니다.")
            .setPositiveButton("원본 보기",(d,w)->openOfficeOriginal(uri,name))
            .setNegativeButton("PDF 가져오기",(d,w)->chooseConvertedPdf());
        if(textPreview)dialog.setNeutralButton("본문만 미리보기",(d,w)->openOfficeText(uri));
        dialog.show();
    }
    private void openOfficeText(Uri uri){openPdf(uri,true);}
    private void openPdf(Uri uri){openPdf(uri,false);}
    private void openPdf(Uri uri,boolean textOnly){openPdf(uri,textOnly,-1,true);}
    private void openPdf(Uri uri,boolean textOnly,int requestedPage,boolean activate){
        String title=queryName(uri);
        if(isOfficeDocument(title)&&!textOnly){if(title.toLowerCase(Locale.ROOT).endsWith(".hwp")||title.toLowerCase(Locale.ROOT).endsWith(".hwpx"))convertHwp(uri,title);else if(canConvertOffice(title))convertOffice(uri,title);else offerOfficeImport(uri,title);return;}
        if(!textOnly&&!library.managed(uri)){
            File saved=library.imported(uri);if(saved!=null){openPdf(Uri.fromFile(saved),false,requestedPage,activate);return;}
            importPdfToLibrary(uri,title,requestedPage,activate);return;
        }
        for(DocumentSession session:sessions)if(session.uri.equals(uri)){if(requestedPage>=0)session.page=Math.min(requestedPage,session.renderer.getPageCount()-1);if(activate||activeSession==null)switchDocument(session);return;}
        DocumentSession session=new DocumentSession();try{
            session.title=title;
            if(textOnly&&OfficeImporter.isOffice(title)){session.officePreview=OfficeImporter.createPreview(this,uri,title);session.descriptor=ParcelFileDescriptor.open(session.officePreview,ParcelFileDescriptor.MODE_READ_ONLY);toast("본문 글자 미리보기로 열었습니다. 표·그림·원본 서식은 반영되지 않습니다");}
            else session.descriptor="file".equals(uri.getScheme())?ParcelFileDescriptor.open(new File(uri.getPath()),ParcelFileDescriptor.MODE_READ_ONLY):getContentResolver().openFileDescriptor(uri,"r");
            if(session.descriptor==null)throw new IOException("파일을 읽을 수 없습니다");session.renderer=new PdfRenderer(session.descriptor);session.uri=uri;session.store=new AnnotationStore(this);session.store.open(uri);session.page=requestedPage<0?0:Math.min(requestedPage,session.renderer.getPageCount()-1);sessions.add(session);
            recentPrefs.edit().putString("last_uri",uri.toString()).putString("last_title",title).apply();if(activate||activeSession==null)switchDocument(session);else updateTabs();saveSessionState();
        }catch(Exception error){if(session.renderer!=null)session.renderer.close();if(session.descriptor!=null)try{session.descriptor.close();}catch(IOException ignored){}if(session.officePreview!=null)session.officePreview.delete();toast("문서 열기 실패: "+error.getMessage());if(sessions.isEmpty())showWelcome();}
    }
    private void importPdfToLibrary(Uri source,String title,int page,boolean activate){
        if(!importing.add(source.toString()))return;File destination=libraryFolder!=null&&libraryFolder.isDirectory()?libraryFolder:library.root;boolean announce=!restoringSessions;
        ProgressDialog progress=announce?ProgressDialog.show(this,"PDF 가져오기","문서함에 저장하는 중…",true,false):null;
        new Thread(()->{try{File saved=library.importPdf(source,title,destination);runOnUiThread(()->{importing.remove(source.toString());if(isFinishing()||isDestroyed())return;if(progress!=null)progress.dismiss();openPdf(Uri.fromFile(saved),false,page,activate);if(announce)toast("문서함에 자동 저장했습니다");saveSessionState();});}catch(Exception error){runOnUiThread(()->{importing.remove(source.toString());if(isFinishing()||isDestroyed())return;if(progress!=null)progress.dismiss();toast("가져오기 실패: "+error.getMessage());});}},"library-import").start();
    }
    private void switchDocument(DocumentSession s){library.opened(s.uri);if(activeSession!=null)activeSession.page=currentPage;activeSession=s;renderer=s.renderer;descriptor=s.descriptor;documentUri=s.uri;documentTitle=s.title;store=s.store;titleView.setText(documentTitle);highlightMode=memoMode=outlineMode=false;inkMode=0;pageView.setLassoMode(false);pageView.stopTextSelection();pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(false);pageView.setInkTool(0,inkColor,inkWidth);updateToolStates();updateInkButton();updateTabs();showPage(Math.min(s.page,renderer.getPageCount()-1));rebuildThumbnails();saveSessionState();}
    private void closeDocument(DocumentSession s){int oldIndex=sessions.indexOf(s);sessions.remove(s);if(s.renderer!=null)s.renderer.close();if(s.descriptor!=null)try{s.descriptor.close();}catch(IOException ignored){}if(s.officePreview!=null)s.officePreview.delete();if(s==activeSession){activeSession=null;if(sessions.isEmpty()){renderer=null;descriptor=null;documentUri=null;store=null;firstPageView.clearPage();secondPageView.clearPage();thumbnailList.removeAllViews();refreshStudyPanel();updateTabs();showWelcome();}else switchDocument(sessions.get(Math.max(0,Math.min(oldIndex,sessions.size()-1))));}else updateTabs();saveSessionState();}
    private void updateTabs(){tabRow.removeAllViews();for(DocumentSession s:sessions){LinearLayout chip=new LinearLayout(this);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setPadding(dp(10),0,dp(2),0);chip.setBackground(round(s==activeSession?0xFFDCEAFE:0xFFEFF2F6,14));TextView name=new TextView(this);name.setText(s.title);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setTextColor(s==activeSession?ACCENT:0xFF475569);name.setTextSize(13);name.setGravity(Gravity.CENTER_VERTICAL);name.setIncludeFontPadding(false);name.setPadding(0,0,0,0);name.setOnClickListener(v->switchDocument(s));name.setOnLongClickListener(v->{renameDocument(s);return true;});chip.addView(name,new LinearLayout.LayoutParams(dp(132),dp(34)));TextView close=new TextView(this);close.setText("×");close.setGravity(Gravity.CENTER);close.setTextSize(21);close.setTextColor(0xFF64748B);close.setContentDescription(s.title+" 닫기");close.setOnClickListener(v->closeDocument(s));chip.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(34));cp.setMargins(dp(3),0,dp(3),0);tabRow.addView(chip,cp);}TextView add=new TextView(this);add.setText("＋");add.setGravity(Gravity.CENTER);add.setTextSize(24);add.setTextColor(ACCENT);add.setContentDescription("문서 추가");add.setOnClickListener(v->showAddDocumentMenu());tabRow.addView(add,new LinearLayout.LayoutParams(dp(44),dp(34)));}
    private String queryName(Uri u){try(android.database.Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}return u.getLastPathSegment()==null?"PDF":u.getLastPathSegment();}
    private void toggleSidebar(){sidebarVisible=!sidebarVisible;thumbnailPanel.setVisibility(sidebarVisible?View.VISIBLE:View.GONE);if(sidebarVisible)rebuildThumbnails();}
    private void rebuildThumbnails(){
        final int generation=++thumbnailGeneration;thumbnailList.removeAllViews();if(!sidebarVisible||renderer==null)return;
        TextView add=new TextView(this);add.setText("＋ 페이지");add.setTextSize(13);add.setTextColor(ACCENT);add.setGravity(Gravity.CENTER);add.setContentDescription("미리보기에서 페이지 추가");add.setTag("add_page");add.setBackground(round(0xFFDCEAFE,8));add.setOnClickListener(v->chooseAddedPage());thumbnailList.addView(add,new LinearLayout.LayoutParams(-1,dp(46)));
        for(int i=0;i<renderer.getPageCount();i++){final int page=i;LinearLayout item=new LinearLayout(this);item.setTag(page);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(4),dp(5),dp(4),dp(7));ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.WHITE);item.addView(preview,new LinearLayout.LayoutParams(dp(94),dp(126)));TextView number=new TextView(this);number.setText(String.valueOf(i+1));number.setGravity(Gravity.CENTER);number.setTextSize(12);number.setTextColor(0xFF475569);item.addView(number,new LinearLayout.LayoutParams(-1,dp(24)));item.setOnClickListener(v->showPage(page));thumbnailList.addView(item,new LinearLayout.LayoutParams(-1,dp(160)));}updateThumbnailSelection();renderThumbnail(0,generation,activeSession);
    }
    private void renderThumbnail(int index,int generation,DocumentSession session){if(generation!=thumbnailGeneration||session!=activeSession||renderer==null||index>=renderer.getPageCount())return;thumbnailList.post(()->{if(generation!=thumbnailGeneration||session!=activeSession)return;try(PdfRenderer.Page page=renderer.openPage(index)){int width=188;float ratio=(float)width/page.getWidth();Bitmap image=Bitmap.createBitmap(width,Math.max(1,(int)(page.getHeight()*ratio)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.postScale(ratio,ratio);page.render(image,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);AnnotationPainter.all(this,new Canvas(image),new RectF(0,0,image.getWidth(),image.getHeight()),session.store,index);View item=thumbnailList.findViewWithTag(index);if(item instanceof LinearLayout){View child=((LinearLayout)item).getChildAt(0);if(child instanceof ImageView)((ImageView)child).setImageBitmap(image);}}catch(Exception ignored){}renderThumbnail(index+1,generation,session);});}
    private void updateThumbnailSelection(){for(int i=0;i<thumbnailList.getChildCount();i++){View v=thumbnailList.getChildAt(i);Object tag=v.getTag();if(!(tag instanceof Integer))continue;boolean selected=((Integer)tag)==currentPage;GradientDrawable bg=round(selected?0xFFDCEAFE:Color.TRANSPARENT,8);if(selected)bg.setStroke(dp(2),ACCENT);v.setBackground(bg);}View selected=thumbnailList.findViewWithTag(currentPage);if(sidebarVisible&&selected!=null)selected.post(()->thumbnailPanel.smoothScrollTo(0,Math.max(0,selected.getTop()-dp(16))));}
    private Bitmap renderPage(PdfRenderer target,int index){try(PdfRenderer.Page page=target.openPage(index)){int width=Math.max(1080,getResources().getDisplayMetrics().widthPixels*(twoPage?1:2));float ratio=Math.min(2.5f,(float)width/page.getWidth());Bitmap image=Bitmap.createBitmap(Math.max(1,(int)(page.getWidth()*ratio)),Math.max(1,(int)(page.getHeight()*ratio)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.postScale(ratio,ratio);page.render(image,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return image;}}
    private void showPage(int index){
        onSelectionAdjustStarted();if(renderer==null||index<0||index>=renderer.getPageCount())return;++ocrGeneration;
        int first=twoPage?(index/2)*2:index;firstPageView.showPage(renderPage(renderer,first),first,store.marks,store.strokes,store.translations);firstPageView.setAnnotationStore(store);
        if(twoPage&&first+1<renderer.getPageCount()){secondPageView.setVisibility(View.VISIBLE);secondPageView.showPage(renderPage(renderer,first+1),first+1,store.marks,store.strokes,store.translations);secondPageView.setAnnotationStore(store);}else{secondPageView.clearPage();secondPageView.setVisibility(twoPage?View.INVISIBLE:View.GONE);}
        pageView=twoPage&&index!=first?secondPageView:firstPageView;currentPage=index;activeSession.page=index;syncOtherTools();
        previousOverlay.setVisibility(first>0?View.VISIBLE:View.GONE);nextOverlay.setVisibility(first+(twoPage?2:1)<renderer.getPageCount()||isNotebook(activeSession)?View.VISIBLE:View.GONE);nextOverlay.setContentDescription(first+(twoPage?2:1)>=renderer.getPageCount()&&isNotebook(activeSession)?"새 페이지 추가":"다음 페이지");
        pageLabel.setText(twoPage?(first+1)+"–"+Math.min(first+2,renderer.getPageCount())+" / "+renderer.getPageCount():(index+1)+" / "+renderer.getPageCount());
        updateBookmarkButton();updateThumbnailSelection();refreshStudyPanel();saveSessionState();
        loadViewText(firstPageView);if(twoPage&&secondPageView.getVisibility()==View.VISIBLE)loadViewText(secondPageView);
    }
    private void loadViewText(PdfPageView view){List<PdfPageView.TextRegion> cached=activeSession.textRegions.get(view.getPageNumber());if(cached!=null)view.setTextRegions(cached,false);else view.post(()->recognizeViewText(view,false));}
    private void syncOtherTools(){if(firstPageView!=null&&secondPageView!=null){PdfPageView other=pageView==firstPageView?secondPageView:firstPageView;other.copyToolsFrom(pageView);}}
    private void toggleTwoPage(){twoPage=!twoPage;recentPrefs.edit().putBoolean("two_page",twoPage).apply();if(renderer!=null)showPage(currentPage);else secondPageView.setVisibility(twoPage?View.INVISIBLE:View.GONE);toast(twoPage?"두 쪽 보기 · 각 페이지를 터치해 필기하세요":"한 쪽 보기");}
    private void toggleHighlight(){if(renderer==null)return;highlightMode=!highlightMode;memoMode=outlineMode=false;stopInk();updateToolStates();pageView.setMemoMode(false);pageView.setOutlineMode(false);pageView.setHighlightMode(highlightMode,selectedColor);toast(highlightMode?"문장을 따라 좌우로 드래그하세요":"하이라이트를 종료했습니다");}
    private void toggleMemoMode(){placementKind="";if(renderer==null)return;memoMode=!memoMode;highlightMode=outlineMode=false;stopInk();updateToolStates();pageView.setHighlightMode(false,selectedColor);pageView.setOutlineMode(false);pageView.setMemoMode(memoMode);toast(memoMode?"메모를 놓을 위치를 탭하세요":"메모 추가를 종료했습니다");}
    private void toggleOutlineMode(){if(renderer==null)return;outlineMode=!outlineMode;highlightMode=memoMode=false;stopInk();pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(outlineMode);updateToolStates();toast(outlineMode?"개요로 저장할 정확한 위치를 탭하세요":"개요 지점 선택을 종료했습니다");}
    private void updateToolStates(){syncOtherTools();updateInkButton();memoButton.setColorFilter(memoMode?Color.WHITE:NAVY);memoButton.setBackground(memoMode?round(ACCENT,22):round(Color.TRANSPARENT,22));}
    private void stopInk(){pageView.setLassoMode(false);inkMode=0;pageView.setInkTool(0,inkColor,inkWidth);updateInkButton();}
    private void setInkMode(int mode){placementKind="";if(renderer==null)return;pageView.setLassoMode(false);inkMode=mode;pageView.setDirectTextSelection(false);highlightMode=memoMode=outlineMode=false;pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(false);pageView.setInkTool(mode,inkColor,inkWidth);updateToolStates();updateInkButton();toast(mode==3?"직선: 시작점에서 끝점까지 드래그하세요":mode==1?(fingerInk?"손가락 또는 S펜으로 필기하세요":"S펜으로 필기하세요. 손가락 필기는 필기도구에서 켤 수 있습니다"):mode==2?"지울 획을 터치하세요":"읽기 모드 · 빠르게 스와이프하면 페이지를 넘깁니다");}
    private void updateInkButton(){if(inkButton==null)return;inkButton.setColorFilter(inkMode==0&&!highlightMode&&!pageView.isLassoMode()?NAVY:Color.WHITE);inkButton.setBackground(inkMode==0&&!highlightMode&&!pageView.isLassoMode()?round(Color.TRANSPARENT,22):round(inkMode==2?0xFFDC2626:ACCENT,22));inkButton.setImageResource(pageView.isLassoMode()?R.drawable.ic_lasso:highlightMode?R.drawable.ic_highlight:inkMode==2?R.drawable.ic_eraser:R.drawable.ic_ink);inkButton.setContentDescription(pageView.isLassoMode()?"올가미 선택 사용 중":highlightMode?"하이라이트 사용 중":inkMode==3?"직선 사용 중":inkMode==1?"펜 사용 중":inkMode==2?"지우개 사용 중":"필기도구");}
    private void showInkTools(){
        if(renderer==null){toast("문서를 먼저 여세요");return;}
        String[] labels={"펜"+(inkMode==1?" ✓":""),"하이라이트"+(highlightMode?" ✓":""),"지우개"+(inkMode==2?" ✓":""),"읽기·페이지 넘김",
            "손가락 필기 · "+(fingerInk?"켜짐":"꺼짐"),"실행 취소","다시 실행","펜 색상","펜 굵기","하이라이트 색상","사용 안내","올가미 · 영역 캡처","직선"};
        int[] icons={R.drawable.ic_ink,R.drawable.ic_highlight,R.drawable.ic_eraser,R.drawable.ic_check,R.drawable.ic_ink,R.drawable.ic_undo,
            R.drawable.ic_chevron_right,R.drawable.ic_palette,R.drawable.ic_ink,R.drawable.ic_palette,R.drawable.ic_outline,R.drawable.ic_lasso,R.drawable.ic_ink};
        Runnable[] actions={()->setInkMode(1),()->{if(!highlightMode)toggleHighlight();},()->setInkMode(2),()->setInkMode(0),
            this::toggleFingerInk,this::undoInk,this::redoInk,this::chooseInkColor,this::chooseInkWidth,this::chooseColor,this::showInkHelp,this::startLasso,()->setInkMode(3)};
        showIconMenu("필기도구",labels,icons,actions);
    }
    private void toggleFingerInk(){fingerInk=!fingerInk;recentPrefs.edit().putBoolean("finger_ink",fingerInk).apply();pageView.setFingerInk(fingerInk);syncOtherTools();toast(fingerInk?"펜·지우개는 손가락으로도 사용합니다. 두 손가락으로 확대하세요":"손가락은 선택·이동, S펜은 필기에 사용합니다");}
    private void chooseInkColor(){String[] names={"차콜","블루","레드","그린","퍼플"};int[] colors={0xFF172033,0xFF2563EB,0xFFDC2626,0xFF16835B,0xFF7C3AED};new AlertDialog.Builder(this).setTitle("펜 색상").setSingleChoiceItems(names,indexOf(colors,inkColor),(d,w)->{inkColor=colors[w];pageView.setInkTool(inkMode,inkColor,inkWidth);syncOtherTools();d.dismiss();}).setNegativeButton("취소",null).show();}
    private int indexOf(int[] values,int value){for(int i=0;i<values.length;i++)if(values[i]==value)return i;return 0;}
    private void chooseInkWidth(){String[] names={"가늘게","보통","굵게","아주 굵게"};float[] widths={0.0022f,0.004f,0.0065f,0.009f};int checked=0;for(int i=0;i<widths.length;i++)if(Math.abs(widths[i]-inkWidth)<0.0001f)checked=i;final int selected=checked;new AlertDialog.Builder(this).setTitle("기본 펜 굵기").setSingleChoiceItems(names,selected,(d,w)->{inkWidth=widths[w];pageView.setInkTool(inkMode,inkColor,inkWidth);syncOtherTools();d.dismiss();}).setNegativeButton("취소",null).show();}
    private void undoInk(){if(store==null)return;for(int i=store.strokes.size()-1;i>=0;i--){AnnotationStore.InkStroke s=store.strokes.get(i);if(s.page==currentPage){store.strokes.remove(i);activeSession.redoStrokes.push(s);store.save();pageView.invalidate();toast("마지막 필기를 취소했습니다");return;}}toast("취소할 필기가 없습니다");}
    private void redoInk(){if(activeSession==null||activeSession.redoStrokes.isEmpty()){toast("다시 실행할 필기가 없습니다");return;}AnnotationStore.InkStroke s=activeSession.redoStrokes.pop();store.strokes.add(s);store.save();if(s.page!=currentPage)showPage(s.page);else pageView.invalidate();}
    private void showInkHelp(){new AlertDialog.Builder(this).setTitle("필기 안내").setMessage("• S펜: 필기 또는 지우개\n• 손가락 필기 켜기: 펜·지우개 사용\n• 손가락 필기 끄기: 글자 선택·화면 이동\n• 두 손가락: 확대·이동\n• 필압: 누르는 힘에 따라 선 굵기 변화\n• S펜 측면 버튼: 누르는 동안 임시 지우개\n• 펜 뒤쪽 지우개: 지원 기기에서 자동 인식\n\n일반 정전식 펜과 손가락은 필압을 지원하지 않습니다.").setPositiveButton("확인",null).show();}
    private void chooseColor(){String[] names={"선샤인","민트","로즈","스카이","라벤더"};int[] colors={0x66FFDE59,0x6654C27A,0x66FF6B9A,0x66549CF5,0x66B67CF2};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(6),dp(22),dp(18));TextView guide=new TextView(this);guide.setText("선택한 문장의 하이라이트 미리보기");guide.setTextColor(0xFF64748B);guide.setTextSize(14);guide.setPadding(dp(12),dp(14),dp(12),dp(14));guide.setBackground(round(selectedColor,12));panel.addView(guide);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);LinearLayout.LayoutParams paletteParams=new LinearLayout.LayoutParams(-1,-2);paletteParams.topMargin=dp(20);panel.addView(row,paletteParams);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("하이라이트 색상").setView(panel).setNegativeButton("취소",null).create();for(int i=0;i<colors.length;i++){final int c=colors[i];ImageButton s=new ImageButton(this);s.setContentDescription(names[i]);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(16));bg.setColor(c|0xFF000000);bg.setStroke(dp(c==selectedColor?4:1),c==selectedColor?NAVY:0xFFE2E8F0);s.setBackground(bg);if(c==selectedColor){s.setImageResource(R.drawable.ic_check);s.setColorFilter(NAVY);}s.setPadding(dp(12),dp(12),dp(12),dp(12));s.setOnClickListener(v->{selectedColor=c;pageView.setHighlightMode(highlightMode,selectedColor);updateInkButton();dialog.dismiss();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(4),0,dp(4),0);row.addView(s,p);}dialog.show();}
    private void toggleBookmark(){if(renderer==null)return;if(!store.bookmarks.add(currentPage))store.bookmarks.remove(currentPage);store.save();updateBookmarkButton();}
    private void updateBookmarkButton(){boolean marked=renderer!=null&&store.bookmarks.contains(currentPage);bookmarkButton.setImageResource(marked?R.drawable.ic_star:R.drawable.ic_star_outline);bookmarkButton.setColorFilter(marked?0xFFF59E0B:NAVY);bookmarkButton.setContentDescription(marked?"즐겨찾기 해제":"즐겨찾기 추가");}
    private void toggleFullscreen(){fullscreen=!fullscreen;header.setVisibility(fullscreen?View.GONE:View.VISIBLE);tabStrip.setVisibility(fullscreen?View.GONE:View.VISIBLE);bottomBar.setVisibility(fullscreen?View.GONE:View.VISIBLE);fullscreenExit.setVisibility(fullscreen?View.VISIBLE:View.GONE);if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(fullscreen)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}}else getWindow().getDecorView().setSystemUiVisibility(fullscreen?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:View.SYSTEM_UI_FLAG_VISIBLE);root.requestApplyInsets();}
    @Override public void onBackPressed(){if(fullscreen)toggleFullscreen();else super.onBackPressed();}
    @Override public void onHighlightCreated(AnnotationStore.Mark mark){store.marks.add(mark);store.save();pageView.invalidate();}
    @Override public void onMemoPointRequested(int page,float x,float y){if(!placementKind.isEmpty()){createPlacedElement(page,x,y);return;}EditText input=new EditText(this);input.setHint("메모를 입력하세요");input.setPadding(dp(24),dp(12),dp(24),dp(12));new AlertDialog.Builder(this).setTitle("새 메모 포스트잇").setView(input).setPositiveButton("저장",(d,w)->{String note=input.getText().toString().trim();if(note.isEmpty())return;AnnotationStore.Mark m=new AnnotationStore.Mark();m.page=page;m.left=Math.max(0f,x-0.025f);m.right=Math.min(1f,x+0.025f);m.top=Math.max(0f,y-0.025f);m.bottom=Math.min(1f,y+0.025f);m.color=selectedColor;m.note=note;m.noteOnly=true;store.marks.add(m);store.save();pageView.invalidate();toast("메모 포스트잇을 저장했습니다");}).setNegativeButton("취소",null).show();}
    @Override public void onMarkTapped(AnnotationStore.Mark mark){editMark(mark);}
    @Override public void onZoomGestureStarted(){if(highlightMode||memoMode||outlineMode){highlightMode=memoMode=outlineMode=false;pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(false);updateToolStates();}}
    @Override public void onPageSwipe(int direction){animatePage(direction);}
    private void animatePage(int direction){
        if(pageAnimating||renderer==null)return;int target=twoPage?(currentPage/2)*2+direction*2:currentPage+direction;if(target<0)return;if(target>=renderer.getPageCount()){if(direction>0&&isNotebook(activeSession))appendPage(activeSession,library.paper(new File(activeSession.uri.getPath())));return;}
        pageAnimating=true;
        float offset=dp(26)*direction;
        boolean vertical=verticalPageSwipe;
        pageView.animate().alpha(0.45f).translationX(vertical?0:-offset).translationY(vertical?-offset:0)
            .setDuration(110).withEndAction(()->{
                showPage(target);
                pageView.setTranslationX(vertical?0:offset);pageView.setTranslationY(vertical?offset:0);
                pageView.animate().alpha(1f).translationX(0).translationY(0).setDuration(170)
                    .withEndAction(()->pageAnimating=false).start();
            }).start();
    }
    @Override public void onOutlinePointRequested(int page,float x,float y){promptOutline(page,x,y,"");}
    private void promptOutline(int page,float x,float y,String suggested){EditText input=new EditText(this);input.setHint("예: 2. 세부 검토사항");if(suggested!=null&&!suggested.isEmpty())input.setText(suggested.length()>60?suggested.substring(0,60)+"…":suggested);input.setPadding(dp(24),dp(12),dp(24),dp(12));new AlertDialog.Builder(this).setTitle("개요 제목").setView(input).setPositiveButton("저장",(d,w)->{String title=input.getText().toString().trim();if(title.isEmpty())title="페이지 "+(page+1);AnnotationStore.OutlineItem item=new AnnotationStore.OutlineItem();item.page=page;item.x=x;item.y=y;item.title=title;store.outlines.add(item);store.save();outlineMode=false;pageView.setOutlineMode(false);toast("개요에 저장했습니다");}).setNegativeButton("취소",null).show();}
    @Override public void onInkChanged(){if(store!=null){store.save();if(activeSession!=null)activeSession.redoStrokes.clear();}}
    @Override public void onTextSelectionFinished(PdfPageView.TextSelection selection,float anchorX,float anchorY){showTextSelectionPopup(selection,anchorX,anchorY);}
    @Override public void onTranslationTapped(AnnotationStore.TranslationNote note){editTranslation(note);}
    private void startTextSelection(){if(renderer==null)return;setInkMode(0);pageView.setDirectTextSelection(true);syncOtherTools();recognizePageText(true);toast("텍스트 선택: 단어에서 드래그하세요");}
    private void recognizePageText(boolean announce){++ocrGeneration;recognizeViewText(pageView,announce);if(twoPage)recognizeViewText(pageView==firstPageView?secondPageView:firstPageView,false);}
    private void recognizeViewText(PdfPageView view,boolean announce){if(renderer==null||view.getVisibility()!=View.VISIBLE)return;final DocumentSession session=activeSession;final int page=view.getPageNumber();final int generation=ocrGeneration;Bitmap copy=view.copyPageBitmap();if(copy==null)return;if(announce)toast("글자를 다시 인식하는 중입니다…");if(latinRecognizer==null){latinRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);koreanRecognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());}final int width=copy.getWidth(),height=copy.getHeight();InputImage image=InputImage.fromBitmap(copy,0);latinRecognizer.process(image).addOnCompleteListener(latin->{koreanRecognizer.process(image).addOnCompleteListener(korean->{copy.recycle();if(generation!=ocrGeneration||session!=activeSession||page!=view.getPageNumber())return;Text result=null;if(latin.isSuccessful())result=latin.getResult();if(korean.isSuccessful()&&(result==null||korean.getResult().getText().length()>result.getText().length()))result=korean.getResult();if(result==null){if(announce)toast("글자를 인식하지 못했습니다");return;}List<PdfPageView.TextRegion> regions=makeTextRegions(result,width,height);session.textRegions.put(page,regions);view.setTextRegions(regions,announce);});});}
    private List<PdfPageView.TextRegion> makeTextRegions(Text text,int width,int height){List<PdfPageView.TextRegion> out=new ArrayList<>();for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){android.graphics.Rect lb=line.getBoundingBox();if(lb==null)continue;RectF lineBox=normalized(lb,width,height);for(Text.Element element:line.getElements()){android.graphics.Rect eb=element.getBoundingBox();if(eb!=null&&!element.getText().trim().isEmpty())out.add(new PdfPageView.TextRegion(element.getText().trim(),line.getText().trim(),normalized(eb,width,height),lineBox));}}return out;}
    private RectF normalized(android.graphics.Rect r,int w,int h){return new RectF(Math.max(0f,(float)r.left/w),Math.max(0f,(float)r.top/h),Math.min(1f,(float)r.right/w),Math.min(1f,(float)r.bottom/h));}
    private PopupWindow selectionPopup;
    @Override public void onSelectionAdjustStarted(){if(selectionPopup!=null){selectionPopup.dismiss();selectionPopup=null;}}
    private TextView menuTile(LinearLayout row,String label,int iconId,Runnable action){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
        tile.setBackground(round(0xFFF3F7FB,14));tile.setContentDescription(label);
        ImageView iconView=new ImageView(this);iconView.setImageResource(iconId);iconView.setColorFilter(NAVY);
        tile.addView(iconView,new LinearLayout.LayoutParams(dp(23),dp(23)));
        TextView title=new TextView(this);title.setText(label);title.setSingleLine();title.setTextColor(NAVY);
        title.setTextSize(12);title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,dp(22));titleParams.topMargin=dp(3);tile.addView(title,titleParams);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(65),1);params.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,params);
        tile.setOnClickListener(v->action.run());
        return title;
    }
    private void showTextSelectionPopup(PdfPageView.TextSelection selection,float anchorX,float anchorY){
        onSelectionAdjustStarted();
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(7),dp(7),dp(7),dp(7));panel.setBackground(round(Color.WHITE,20));
        String[] labels={"하이라이트","복사","번역","읽어주기","단어장","개요","메모","발췌"};
        int[] icons={R.drawable.ic_highlight,R.drawable.ic_copy,R.drawable.ic_translate,R.drawable.ic_speaker,
            R.drawable.ic_dictionary,R.drawable.ic_outline,R.drawable.ic_note_add,R.drawable.ic_copy};
        Runnable[] actions={()->addOcrHighlights(selection.bounds),()->copySelectedText(selection.text),()->translateText(selection.text,selection.unionBounds),
            ()->readAloud(selection.text),()->openDictionary(selection.text),()->promptOutline(currentPage,selection.unionBounds.left,selection.unionBounds.top,selection.text),
            ()->onMemoPointRequested(currentPage,selection.unionBounds.right,selection.unionBounds.top),
            ()->addStudyEntry(selection.text,selection.unionBounds.left,selection.unionBounds.top,true)};
        for(int row=0;row<2;row++){
            LinearLayout group=new LinearLayout(this);panel.addView(group);
            for(int col=0;col<4;col++){
                int index=row*4+col;
                if(index>=labels.length){View empty=new View(this);group.addView(empty,new LinearLayout.LayoutParams(0,dp(71),1));continue;}
                menuTile(group,labels[index],icons[index],()->{onSelectionAdjustStarted();actions[index].run();pageView.clearTextSelectionOverlay();});
            }
        }
        int width=Math.min(dp(376),root.getWidth()-dp(20));
        selectionPopup=new PopupWindow(panel,width,dp(156),false);selectionPopup.setBackgroundDrawable(round(Color.WHITE,20));selectionPopup.setElevation(dp(12));
        selectionPopup.setOutsideTouchable(true);
        if(android.os.Build.VERSION.SDK_INT>=29)selectionPopup.setTouchModal(false);
        int[] location=new int[2];pageView.getLocationOnScreen(location);
        int[] rootLocation=new int[2];root.getLocationOnScreen(rootLocation);
        int y=location[1]+Math.round(anchorY)+dp(20);
        if(y+dp(160)>rootLocation[1]+root.getHeight())y=location[1]+Math.round(anchorY)-dp(176);
        selectionPopup.showAtLocation(root,Gravity.TOP|Gravity.LEFT,rootLocation[0]+(root.getWidth()-width)/2,Math.max(rootLocation[1]+dp(8),y));
    }
    private void copySelectedText(String text){ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("PDF 선택 문장",text));toast("선택한 내용을 복사했습니다");}
    private void openDictionary(String word){try{Intent i=new Intent();i.setClassName("com.hdlee73.sajeonapp","com.hdlee73.sajeonapp.MainActivity");i.putExtra("query",word.replaceAll("^[^A-Za-z]+|[^A-Za-z'-]+$","").trim());i.putExtra("return_to_pdf",true);i.putExtra("source_title",documentTitle);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("단어장 앱이 필요합니다").setMessage("LEXI 단어장 앱을 설치하면 선택한 단어를 바로 검색할 수 있습니다.").setPositiveButton("확인",null).show();}}
    private void addOcrHighlights(List<RectF> bounds){for(RectF b:bounds){AnnotationStore.Mark m=new AnnotationStore.Mark();m.page=currentPage;m.left=b.left;m.top=b.top;m.right=b.right;m.bottom=b.bottom;m.color=selectedColor;store.marks.add(m);}store.save();pageView.invalidate();toast("선택한 범위를 하이라이트했습니다");}
    private void translateOffline(String source,RectF bounds){final DocumentSession target=activeSession;final int page=currentPage;boolean korean=source.matches(".*[가-힣].*");TranslatorOptions options=new TranslatorOptions.Builder().setSourceLanguage(korean?TranslateLanguage.KOREAN:TranslateLanguage.ENGLISH).setTargetLanguage(korean?TranslateLanguage.ENGLISH:TranslateLanguage.KOREAN).build();Translator translator=Translation.getClient(options);ProgressDialog progress=ProgressDialog.show(this,"번역","번역 모델을 준비하는 중입니다…",true,false);translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()).onSuccessTask(v->translator.translate(source)).addOnSuccessListener(result->{progress.dismiss();translator.close();if(isFinishing()||isDestroyed()||!sessions.contains(target))return;switchDocument(target);showPage(page);showTranslationResult(source,result,bounds);}).addOnFailureListener(e->{progress.dismiss();translator.close();toast("번역 실패: 인터넷 연결을 확인하세요");});}
    private void showTranslationResult(String source,String translated,RectF bounds){final AnnotationStore targetStore=store;final int targetPage=currentPage;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(4),dp(22),0);TextView original=new TextView(this);original.setText("원문\n"+source);original.setTextColor(0xFF64748B);original.setTextSize(14);panel.addView(original);EditText result=new EditText(this);result.setText(translated);result.setHint("번역 앱에서 결과를 복사한 뒤 붙여넣으세요");result.setMinLines(3);result.setMaxLines(7);result.setPadding(dp(16),dp(16),dp(16),dp(16));result.setBackground(round(0xFFFFF7D6,16));result.setGravity(Gravity.TOP);panel.addView(result,new LinearLayout.LayoutParams(-1,-2));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("번역 · 포스트잇").setView(panel).setPositiveButton("포스트잇 저장",(d,w)->{String value=result.getText().toString().trim();if(value.isEmpty())return;AnnotationStore.TranslationNote n=new AnnotationStore.TranslationNote();n.page=targetPage;n.left=bounds.left;n.top=bounds.top;n.right=bounds.right;n.bottom=bounds.bottom;n.source=source;n.translated=value;targetStore.translations.add(n);targetStore.save();pageView.invalidate();toast("번역 포스트잇을 저장했습니다");}).setNeutralButton("붙여넣기",null).setNegativeButton("닫기",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();if(clip!=null&&clip.getItemCount()>0)result.setText(clip.getItemAt(0).coerceToText(this));}));dialog.show();}
    private void editTranslation(AnnotationStore.TranslationNote note){EditText input=new EditText(this);input.setText(note.translated);input.setMinLines(3);new AlertDialog.Builder(this).setTitle("번역 포스트잇 · p."+(note.page+1)).setMessage("원문: "+note.source).setView(input).setPositiveButton("저장",(d,w)->{note.translated=input.getText().toString().trim();store.save();pageView.invalidate();}).setNegativeButton("삭제",(d,w)->{store.translations.remove(note);store.save();pageView.invalidate();toast("번역 포스트잇을 삭제했습니다");}).setNeutralButton("표시 설정",(d,w)->showTranslationDisplayOptions(note)).show();}
    private void showTranslationDisplayOptions(AnnotationStore.TranslationNote note){String[] choices={"펼쳐서 표시","최소화","숨기기"};int checked=!note.visible?2:(note.minimized?1:0);new AlertDialog.Builder(this).setTitle("번역 포스트잇 표시").setSingleChoiceItems(choices,checked,(d,w)->{note.visible=w!=2;note.minimized=w==1;store.save();pageView.invalidate();d.dismiss();}).setNegativeButton("취소",null).show();}
    private void showTranslations(){if(store==null||store.translations.isEmpty()){toast("저장된 번역 포스트잇이 없습니다");return;}List<AnnotationStore.TranslationNote> items=new ArrayList<>(store.translations);String[] labels=new String[items.size()];for(int i=0;i<items.size();i++){AnnotationStore.TranslationNote n=items.get(i);String state=!n.visible?"숨김":(n.minimized?"최소화":"펼침");labels[i]="p."+(n.page+1)+"  ["+state+"] "+(n.translated.length()>35?n.translated.substring(0,35)+"…":n.translated);}new AlertDialog.Builder(this).setTitle("번역 포스트잇").setItems(labels,(d,i)->{showPage(items.get(i).page);editTranslation(items.get(i));}).show();}
    private void editMark(AnnotationStore.Mark mark){EditText input=new EditText(this);input.setHint("메모를 입력하세요");input.setText(mark.note);input.setPadding(dp(24),dp(12),dp(24),dp(12));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("페이지 "+(mark.page+1)+(mark.noteOnly?" 메모 포스트잇":" 하이라이트")).setView(input).setPositiveButton("저장",(d,w)->{mark.note=input.getText().toString().trim();mark.visible=true;store.save();pageView.invalidate();}).setNegativeButton(mark.noteOnly?"메모 삭제":"하이라이트 삭제",(d,w)->{store.marks.remove(mark);store.save();pageView.invalidate();toast(mark.noteOnly?"메모를 삭제했습니다":"하이라이트를 삭제했습니다");}).setNeutralButton("표시 설정",(d,w)->showMemoDisplayOptions(mark)).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFD32F2F));dialog.show();}
    private void showMemoDisplayOptions(AnnotationStore.Mark mark){String[] choices={"펼쳐서 표시","최소화","숨기기"};int checked=!mark.visible?2:(mark.minimized?1:0);new AlertDialog.Builder(this).setTitle("메모 포스트잇 표시").setSingleChoiceItems(choices,checked,(d,w)->{mark.visible=w!=2;mark.minimized=w==1;store.save();pageView.invalidate();d.dismiss();}).setNegativeButton("취소",null).show();}
    private void showIconMenu(String title,String[] labels,int[] icons,Runnable[] actions){if(labels.length!=icons.length||labels.length!=actions.length)throw new IllegalArgumentException("메뉴 항목 불일치");
        ScrollView scroll=new ScrollView(this);LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);grid.setPadding(dp(12),dp(6),dp(12),dp(10));scroll.addView(grid);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("닫기",null).create();
        for(int i=0;i<labels.length;i+=2){LinearLayout row=new LinearLayout(this);grid.addView(row);
            for(int j=i;j<Math.min(i+2,labels.length);j++){final int index=j;menuTile(row,labels[j],icons[j],()->{dialog.dismiss();actions[index].run();});}
            if(i+1>=labels.length)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(71),1));
        }
        dialog.show();int height=Math.min(dp(145+((labels.length+1)/2)*71),getResources().getDisplayMetrics().heightPixels-dp(130));dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.92f),height);
    }
    private void showAddDocumentMenu(){new AlertDialog.Builder(this).setTitle("문서 추가").setItems(new String[]{"파일 가져오기","저장된 문서 열기","새 노트 만들기"},(d,index)->{if(index==0)choosePdf();else if(index==1)showLibrary();else newNotebook();}).setNegativeButton("취소",null).show();}
    private void showTools(){
        showIconMenu("메뉴",new String[]{"문서","보기·이동","필기·삽입","학습·주석","내보내기·백업","사용법"},new int[]{R.drawable.ic_folder_open,R.drawable.ic_thumbnails,R.drawable.ic_ink,R.drawable.ic_outline,R.drawable.ic_copy,R.drawable.ic_check},new Runnable[]{()->showToolCategory(0),()->showToolCategory(1),()->showToolCategory(2),()->showToolCategory(3),()->showToolCategory(4),this::showHelp});
    }
    private void showToolCategory(int category){
        if(category!=0&&renderer==null){toast("문서를 먼저 여세요");return;}
        switch(category){
            case 0:showIconMenu("문서",new String[]{"문서함·파일 관리","문서 추가","새 노트","현재 문서 이름 변경"},new int[]{R.drawable.ic_folder_open,R.drawable.ic_note_add,R.drawable.ic_note_add,R.drawable.ic_ink},new Runnable[]{this::showLibrary,this::showAddDocumentMenu,this::newNotebook,()->{if(activeSession!=null)renameDocument(activeSession);else toast("문서를 먼저 여세요");}});break;
            case 1:showIconMenu("보기·이동",new String[]{"페이지 미리보기","페이지로 이동","두 쪽 보기 · "+(twoPage?"켜짐":"꺼짐"),"전체 화면","페이지 넘김 설정","읽기·페이지 넘김"},new int[]{R.drawable.ic_thumbnails,R.drawable.ic_chevron_right,R.drawable.ic_thumbnails,R.drawable.ic_fullscreen,R.drawable.ic_chevron_right,R.drawable.ic_check},new Runnable[]{this::toggleSidebar,this::goToPage,this::toggleTwoPage,this::toggleFullscreen,this::choosePageSwipeDirection,()->setInkMode(0)});break;
            case 2:showIconMenu("필기·삽입",new String[]{"필기도구","올가미·영역 캡처","텍스트 선택","타이핑","이미지 붙여넣기","웹·유튜브 링크","메모 추가"},new int[]{R.drawable.ic_ink,R.drawable.ic_lasso,R.drawable.ic_scan,R.drawable.ic_note_add,R.drawable.ic_copy,R.drawable.ic_chevron_right,R.drawable.ic_note_add},new Runnable[]{this::showInkTools,this::startLasso,this::startTextSelection,()->placeElement("text",""),this::pasteImage,()->placeElement("link",""),this::toggleMemoMode});break;
            case 3:showIconMenu("학습·주석",new String[]{"문서·필기 검색","듀얼 뷰 노트","발췌 바구니","메모·하이라이트","번역 포스트잇","책갈피","개요 목록","개요 추가","글자 다시 인식"},new int[]{R.drawable.ic_search,R.drawable.ic_note_add,R.drawable.ic_copy,R.drawable.ic_highlight,R.drawable.ic_translate,R.drawable.ic_star,R.drawable.ic_outline,R.drawable.ic_note_add,R.drawable.ic_scan},new Runnable[]{this::searchDocument,()->showStudy(false),()->showStudy(true),this::showMarkList,this::showTranslations,this::showBookmarks,this::showOutlineList,this::toggleOutlineMode,()->recognizePageText(true)});break;
            case 4:showIconMenu("내보내기·백업",new String[]{"PDF 내보내기","노트·발췌 내보내기","주석 백업","주석 백업 복원","본문 미리보기 저장"},new int[]{R.drawable.ic_folder_open,R.drawable.ic_copy,R.drawable.ic_copy,R.drawable.ic_undo,R.drawable.ic_check},new Runnable[]{this::exportPdf,this::exportStudy,this::exportAnnotations,this::importSidecar,this::saveToLibrary});break;
        }
    }
    private void showOutlineList(){
        if(store==null){toast("PDF를 먼저 여세요");return;}
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(20),dp(8),dp(20),dp(16));
        ScrollView scroll=new ScrollView(this);scroll.addView(rows);scroll.setLayoutParams(new ViewGroup.LayoutParams(-1,dp(360)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("문서 개요").setView(scroll)
            .setPositiveButton("＋ 개요 추가",(d,w)->toggleOutlineMode()).setNegativeButton("닫기",null).create();
        List<AnnotationStore.OutlineItem> items=new ArrayList<>(store.outlines);
        items.sort(Comparator.comparingInt((AnnotationStore.OutlineItem item)->item.page).thenComparingDouble(item->item.y));
        if(items.isEmpty()){TextView empty=new TextView(this);empty.setText("기억할 위치를 제목과 함께 저장하세요");empty.setTextColor(0xFF64748B);rows.addView(empty);}
        for(AnnotationStore.OutlineItem item:items){
            LinearLayout row=new LinearLayout(this);row.setPadding(dp(14),dp(12),dp(4),dp(12));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(round(0xFFF1F5F9,16));
            TextView title=new TextView(this);title.setText(item.title+"\n페이지 "+(item.page+1));title.setTextSize(16);title.setTextColor(NAVY);
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));title.setOnClickListener(v->{dialog.dismiss();showPage(item.page);pageView.post(()->pageView.focusOnPoint(item.x,item.y));});
            row.addView(icon(R.drawable.ic_more_vert,"개요 관리",NAVY,v->{dialog.dismiss();showOutlineItem(item);}),new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(8);rows.addView(row,params);
        }
        dialog.show();
    }
    private void showOutlineItem(AnnotationStore.OutlineItem item){new AlertDialog.Builder(this).setTitle(item.title).setMessage("페이지 "+(item.page+1)).setPositiveButton("이동",(d,w)->{showPage(item.page);pageView.post(()->pageView.focusOnPoint(item.x,item.y));}).setNegativeButton("삭제",(d,w)->{store.outlines.remove(item);store.save();toast("개요 항목을 삭제했습니다");}).setNeutralButton("취소",null).show();}
    private void choosePageSwipeDirection(){String[] choices={"화살표만 · 드래그 넘김 끄기","수평 · 좌우로 넘기기","수직 · 위아래로 넘기기"};new AlertDialog.Builder(this).setTitle("페이지 넘김").setSingleChoiceItems(choices,!swipeEnabled?0:verticalPageSwipe?2:1,(dialog,which)->{swipeEnabled=which!=0;verticalPageSwipe=which==2;pageView.setVerticalPageSwipe(verticalPageSwipe);pageView.setPageSwipeEnabled(swipeEnabled);recentPrefs.edit().putBoolean("vertical_page_swipe",verticalPageSwipe).putBoolean("page_swipe_enabled_v2",swipeEnabled).apply();syncOtherTools();dialog.dismiss();toast(swipeEnabled?"스와이프로도 페이지를 넘깁니다":"본문의 반투명 화살표로 페이지를 넘기세요");}).setNegativeButton("취소",null).show();}
    private void showMarkList(){List<AnnotationStore.Mark> items=new ArrayList<>(store.marks);if(items.isEmpty()){toast("저장된 하이라이트나 메모가 없습니다");return;}String[] labels=new String[items.size()];for(int i=0;i<items.size();i++){AnnotationStore.Mark mark=items.get(i);String note=mark.note;String state=note==null||note.isEmpty()?"":(!mark.visible?"[숨김] ":(mark.minimized?"[최소화] ":"[펼침] "));labels[i]="p."+(mark.page+1)+"  "+state+(note==null||note.isEmpty()?"(메모 없음)":note);}new AlertDialog.Builder(this).setTitle("메모·하이라이트").setItems(labels,(d,i)->{showPage(items.get(i).page);editMark(items.get(i));}).show();}
    private void showBookmarks(){if(store.bookmarks.isEmpty()){toast("즐겨찾기한 페이지가 없습니다");return;}List<Integer> pages=new ArrayList<>(store.bookmarks);Collections.sort(pages);String[] labels=new String[pages.size()];for(int i=0;i<pages.size();i++)labels[i]="페이지 "+(pages.get(i)+1);new AlertDialog.Builder(this).setTitle("즐겨찾기").setItems(labels,(d,i)->showPage(pages.get(i))).show();}
    private void goToPage(){if(renderer==null)return;EditText input=new EditText(this);input.setInputType(2);input.setHint("1 ~ "+renderer.getPageCount());new AlertDialog.Builder(this).setTitle("페이지로 이동").setView(input).setPositiveButton("이동",(d,w)->{try{showPage(Integer.parseInt(input.getText().toString())-1);}catch(Exception ignored){toast("올바른 페이지를 입력하세요");}}).setNegativeButton("취소",null).show();}
    private void exportAnnotations(){if(documentUri==null)return;try{pendingJsonExport=store.exportJson(documentUri,documentTitle);}catch(JSONException error){toast("백업 실패");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,documentTitle.replaceAll("(?i)\\.pdf$","")+"_annotations.json");startActivityForResult(i,EXPORT_JSON);}
    private void startLasso(){
        if(renderer==null){toast("PDF를 먼저 여세요");return;}
        onSelectionAdjustStarted();highlightMode=memoMode=outlineMode=false;inkMode=0;
        pageView.setLassoMode(true);updateToolStates();toast("손가락 또는 S펜으로 원하는 영역을 둘러 그리세요. 두 손가락으로 확대할 수 있습니다.");
    }
    @Override public void onLassoSelectionFinished(){
        final Bitmap capture;
        try{capture=pageView.captureLasso();}catch(OutOfMemoryError|RuntimeException error){pageView.clearLassoSelection();toast("캡처할 영역을 조금 줄여 주세요");return;}
        if(capture==null)return;
        final String selectedText=pageView.lassoText();final int capturedPage=currentPage;final String capturedTitle=documentTitle;
        final boolean[] handedOff={false};
        ScrollView scroll=new ScrollView(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(8),dp(12),dp(8));scroll.addView(panel);
        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(round(0xFFF1F5F9,12));preview.setImageBitmap(capture);panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView hint=new TextView(this);hint.setText("선택 영역 · p."+(capturedPage+1));hint.setTextColor(NAVY);hint.setPadding(dp(8),dp(8),dp(8),dp(4));panel.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("올가미 캡처").setView(scroll).setNegativeButton("닫기",null).create();
        String[] labels={"이미지 복사","PNG 저장","이미지 공유","글자 복사","다시 선택","선택 종료"};
        int[] icons={R.drawable.ic_copy,R.drawable.ic_folder_open,R.drawable.ic_chevron_right,R.drawable.ic_scan,R.drawable.ic_lasso,R.drawable.ic_check};
        for(int row=0;row<3;row++){LinearLayout group=new LinearLayout(this);panel.addView(group);for(int col=0;col<2;col++){final int index=row*2+col;menuTile(group,labels[index],icons[index],()->{
            if(index<3){handedOff[0]=true;dialog.dismiss();writeCapture(capture,index,capturedTitle,capturedPage);}
            else if(index==3){if(selectedText.isEmpty()){toast("인식된 글자가 없습니다. 이미지 복사를 사용하거나 글자를 다시 인식하세요");return;}copySelectedText(selectedText);dialog.dismiss();}
            else{dialog.dismiss();if(index==5){setInkMode(0);updateToolStates();}}
        });}}
        dialog.setOnDismissListener(d->{preview.setImageDrawable(null);pageView.clearLassoSelection();if(!handedOff[0]&&!capture.isRecycled())capture.recycle();});
        dialog.show();dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.92f),Math.min(dp(600),getResources().getDisplayMetrics().heightPixels-dp(100)));
    }
    private void writeCapture(Bitmap image,int action,String title,int page){
        new Thread(()->{
            File file=null;
            try{
                File directory=new File(getCacheDir(),"captures");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("캡처 폴더를 만들 수 없습니다");
                File[] old=directory.listFiles();if(old!=null)for(File item:old)if(item.getName().matches("[a-f0-9-]{36}\\.png")&&System.currentTimeMillis()-item.lastModified()>7L*24*60*60*1000)item.delete();
                file=new File(directory,UUID.randomUUID()+".png");try(OutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG 저장 실패");}
                final File ready=file;
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;Uri uri=CaptureProvider.uri(this,ready);
                    if(action==0){ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(),"PDF Note 영역 캡처",uri));toast("이미지를 복사했습니다. 이미지 붙여넣기를 지원하는 앱에서 사용하세요");}
                    else if(action==1){pendingCaptureExport=ready;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,title.replaceAll("(?i)\\.pdf$","")+"_p"+(page+1)+"_capture.png");startActivityForResult(intent,EXPORT_CAPTURE);}
                    else{Intent intent=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newUri(getContentResolver(),"PDF Note 캡처",uri));try{startActivity(Intent.createChooser(intent,"캡처 이미지 공유"));}catch(ActivityNotFoundException error){toast("이미지를 받을 앱이 없습니다");}}
                });
            }catch(Exception error){if(file!=null)file.delete();runOnUiThread(()->toast("캡처 실패: "+error.getMessage()));}
            finally{image.recycle();}
        },"lasso-capture").start();
    }
    private void receiveCaptureExport(int result,Intent data){
        final File source=pendingCaptureExport;pendingCaptureExport=null;
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(source==null){toast("올가미로 다시 캡처해 주세요");return;}
        final Uri destination=data.getData();
        new Thread(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(destination,"wt")){
            if(out==null)throw new IOException("저장할 파일을 열 수 없습니다");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            runOnUiThread(()->toast("PNG 캡처를 저장했습니다"));
        }catch(Exception error){runOnUiThread(()->toast("캡처 저장 실패: "+error.getMessage()));}},"lasso-export").start();
    }
    private void buildStudyPanel(){
        studyPanel=new LinearLayout(this);studyPanel.setOrientation(LinearLayout.VERTICAL);studyPanel.setPadding(dp(8),dp(4),dp(8),dp(4));studyPanel.setBackgroundColor(0xFFFFFBF1);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        studyHeading=new TextView(this);studyHeading.setTextSize(14);studyHeading.setTextColor(NAVY);bar.addView(studyHeading,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(icon(R.drawable.ic_note_add,"현재 페이지에 노트 추가",NAVY,v->addStudyEntry("",0.5f,0.5f,false)),new LinearLayout.LayoutParams(dp(44),dp(44)));
        bar.addView(icon(R.drawable.ic_more_vert,"노트 메뉴",NAVY,v->new AlertDialog.Builder(this).setItems(new String[]{"전체 노트","발췌만 보기","내보내기","닫기"},(d,i)->{if(i<2){basketOnly=i==1;refreshStudyPanel();}else if(i==2)exportStudy();else{studyVisible=false;layoutStudyPanel();}}).show()),new LinearLayout.LayoutParams(dp(44),dp(44)));
        studyPanel.addView(bar);ScrollView scroll=new ScrollView(this);studyRows=new LinearLayout(this);studyRows.setOrientation(LinearLayout.VERTICAL);scroll.addView(studyRows);studyPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void layoutStudyPanel(){
        if(studySplit==null)return;boolean wide=getResources().getConfiguration().screenWidthDp>=600;
        studySplit.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);studyPanel.setVisibility(studyVisible?View.VISIBLE:View.GONE);
        pdfArea.setLayoutParams(new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,studyVisible?1.3f:1f));
        studyPanel.setLayoutParams(new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,1f));
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);layoutStudyPanel();}
    private void showStudy(boolean basket){if(store==null){toast("PDF를 먼저 여세요");return;}studyVisible=true;basketOnly=basket;layoutStudyPanel();refreshStudyPanel();}
    private void addStudyEntry(String text,float x,float y,boolean excerpt){
        if(store==null)return;AnnotationStore.StudyEntry entry=new AnnotationStore.StudyEntry();entry.page=currentPage;entry.x=x;entry.y=y;entry.text=text;entry.excerpt=excerpt;
        if(excerpt){store.studyEntries.add(entry);store.save();showStudy(true);toast("발췌 바구니에 저장했습니다");}else editStudyEntry(entry,true);
    }
    private void editStudyEntry(AnnotationStore.StudyEntry entry,boolean fresh){
        final AnnotationStore target=store;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),0,dp(18),0);
        EditText text=new EditText(this);text.setHint("노트 / 발췌 원문 · Markdown 입력");text.setText(entry.text);text.setMinLines(3);text.setMaxLines(6);panel.addView(text);
        EditText comment=new EditText(this);comment.setHint("설명 / Anki 뒷면");comment.setText(entry.comment);comment.setMaxLines(4);panel.addView(comment);
        new AlertDialog.Builder(this).setTitle("[p."+(entry.page+1)+"] "+(entry.excerpt?"발췌":"노트")).setView(panel)
            .setPositiveButton("저장",(d,w)->{if(text.getText().toString().trim().isEmpty()){toast("노트 내용을 입력하세요");return;}entry.text=text.getText().toString();entry.comment=comment.getText().toString();if(fresh)target.studyEntries.add(entry);target.save();showStudy(false);})
            .setNegativeButton("취소",null).setNeutralButton(fresh?"닫기":"삭제",(d,w)->{if(!fresh){target.studyEntries.remove(entry);target.save();refreshStudyPanel();}}).show();
    }
    private void refreshStudyPanel(){
        if(studyRows==null)return;studyRows.removeAllViews();studyHeading.setText((basketOnly?"발췌 바구니":"노트")+" · p."+(currentPage+1));if(store==null)return;
        int count=0;for(AnnotationStore.StudyEntry entry:store.studyEntries){if(basketOnly&&!entry.excerpt)continue;count++;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(6),dp(10),dp(8));card.setBackground(round(entry.page==currentPage?0xFFFFEDB6:Color.WHITE,12));
            TextView link=new TextView(this);link.setText("[p."+(entry.page+1)+"] ↗");link.setTextColor(ACCENT);link.setTextSize(14);link.setMinHeight(dp(40));link.setGravity(Gravity.CENTER_VERTICAL);
            link.setOnClickListener(v->{showPage(entry.page);pageView.post(()->pageView.focusOnPoint(entry.x,entry.y));});card.addView(link);
            TextView body=new TextView(this);body.setText(entry.text+(entry.comment.isEmpty()?"":"\n\n"+entry.comment));body.setTextSize(15);body.setTextColor(NAVY);body.setOnClickListener(v->editStudyEntry(entry,false));card.addView(body);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(8);studyRows.addView(card,params);
        }
        if(count==0){TextView empty=new TextView(this);empty.setText(basketOnly?"본문을 드래그한 뒤 ‘발췌’를 누르세요":"＋로 현재 페이지에 연결된 노트를 작성하세요.\n[p.] 링크를 누르면 원문 위치로 이동합니다.");empty.setPadding(dp(10),dp(12),dp(10),dp(12));studyRows.addView(empty);}
    }
    private void exportStudy(){
        if(store==null)return;new AlertDialog.Builder(this).setTitle("노트·발췌 내보내기").setItems(new String[]{"Markdown (.md)","CSV (.csv)","Excel (.xlsx)","Anki (.tsv · 앞면/뒷면)"},(d,format)->{
            List<AnnotationStore.StudyEntry> entries=new ArrayList<>();for(AnnotationStore.StudyEntry e:store.studyEntries)if(!basketOnly||e.excerpt)entries.add(e);
            if(entries.isEmpty()){toast("내보낼 항목이 없습니다");return;}try{pendingExport=StudyExporter.export(entries,documentTitle,format);}catch(IOException error){toast("내보내기 실패");return;}
            String[] extensions={"md","csv","xlsx","tsv"},types={"text/markdown","text/csv","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/tab-separated-values"};
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(types[format]).putExtra(Intent.EXTRA_TITLE,documentTitle.replaceAll("(?i)\\.pdf$","")+"_notes."+extensions[format]),EXPORT_STUDY);
        }).show();
    }
    private void importSidecar(){if(store==null)return;importTarget=store;importSession=activeSession;startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),IMPORT_SIDECAR);}
    private void receiveStudyResult(int request,int result,Intent data){
        if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT_STUDY)pendingExport=null;else{importTarget=null;importSession=null;}return;}
        if(request==EXPORT_STUDY){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null||pendingExport==null)throw new IOException("다시 내보내세요");out.write(pendingExport);toast("내보냈습니다");}catch(Exception error){toast("내보내기 실패: "+error.getMessage());}finally{pendingExport=null;}return;}
        final AnnotationStore target=importTarget;final DocumentSession session=importSession;importTarget=null;importSession=null;
        if(session==null||!sessions.contains(session))return;
        try(InputStream in=getContentResolver().openInputStream(data.getData())){
            if(in==null)throw new IOException("파일을 읽을 수 없습니다");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>16*1024*1024)throw new IOException("백업은 16MB 이하만 지원합니다");out.write(buffer,0,n);}
            String json=out.toString("UTF-8");JSONObject root=new JSONObject(json);
            new AlertDialog.Builder(this).setTitle("주석 백업 복원").setMessage("백업 문서: "+root.optString("document")+"\n현재 문서: "+session.title+"\n\n현재 문서의 주석·노트·발췌를 이 백업으로 교체합니다.")
                .setPositiveButton("복원",(d,w)->{if(!sessions.contains(session))return;try{target.importJson(json,session.renderer.getPageCount());session.redoStrokes.clear();switchDocument(session);toast("주석과 노트를 복원했습니다");}catch(JSONException error){toast("복원 실패: "+error.getMessage());}}).setNegativeButton("취소",null).show();
        }catch(Exception error){toast("백업 읽기 실패: "+error.getMessage());}
    }
    private void showLibrary(){
        onSelectionAdjustStarted();if(store!=null)store.save();if(libraryDialog!=null&&libraryDialog.isShowing())return;
        if(libraryFolder==null||!libraryFolder.isDirectory())libraryFolder=library.root;
        libraryDialog=new LibraryDialog(this,library,libraryFolder,new LibraryDialog.Actions(){
            public void open(File file){openPdf(Uri.fromFile(file));}
            public void importFiles(File folder){libraryFolder=folder;choosePdf();}
            public void newNote(File folder,Runnable refresh){createNotebook(folder,refresh);}
            public void changed(File source,File target,boolean moved){if(moved)libraryChanged(source,target);}
            public void selectedFolder(File folder){libraryFolder=folder;}
            public void removed(List<File> files){for(DocumentSession session:new ArrayList<>(sessions))if(files.contains(new File(session.uri.getPath())))closeDocument(session);}
        });libraryDialog.show();
    }
    private void newNotebook(){createNotebook(libraryFolder==null?library.root:libraryFolder,()->{});}
    private void createNotebook(File folder,Runnable refresh){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(this);name.setSingleLine();name.setHint("노트 이름");name.setText("새 노트");name.setPadding(dp(18),dp(12),dp(18),dp(12));panel.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));PaperChoiceView paper=new PaperChoiceView(this);panel.addView(paper);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("새 노트").setView(panel).setPositiveButton("만들기",null).setNegativeButton("취소",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{String title=NotebookFiles.name(name.getText().toString());NotebookFiles.Paper selected=paper.paper();dialog.getButton(-1).setEnabled(false);new Thread(()->{try{File file=library.createNote(folder,title,selected);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;dialog.dismiss();refresh.run();if(libraryDialog!=null)libraryDialog.dismiss();openPdf(Uri.fromFile(file));toast("마지막 장에서 넘기면 새 페이지가 추가됩니다");});}catch(Exception error){runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);name.setError(error.getMessage());});}},"new-notebook").start();}catch(Exception error){name.setError(error.getMessage());}}));dialog.show();
    }
    private boolean isNotebook(DocumentSession session){return session!=null&&library.managed(session.uri)&&library.paper(new File(session.uri.getPath()))!=null;}
    private void chooseAddedPage(){
        if(activeSession==null)return;final DocumentSession session=activeSession;
        if(!library.managed(session.uri)){toast("문서함에 저장한 뒤 페이지를 추가하세요");return;}
        NotebookFiles.Paper same=library.paper(new File(session.uri.getPath()));if(same!=null){appendPage(session,same);return;}
        PaperChoiceView paper=new PaperChoiceView(this);new AlertDialog.Builder(this).setTitle("추가할 페이지").setView(paper).setPositiveButton("추가",(d,w)->appendPage(session,paper.paper())).setNegativeButton("취소",null).show();
    }
    private void appendPage(DocumentSession session,NotebookFiles.Paper paper){
        if(!appending.add(session))return;onSelectionAdjustStarted();session.store.save();File file=new File(session.uri.getPath());ProgressDialog progress=ProgressDialog.show(this,"","새 페이지를 추가하는 중…",true,false);
        new Thread(()->{try{int count=library.append(file,paper);runOnUiThread(()->{appending.remove(session);if(isFinishing()||isDestroyed())return;progress.dismiss();if(!sessions.contains(session))return;try{
            ParcelFileDescriptor nextDescriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer nextRenderer;try{nextRenderer=new PdfRenderer(nextDescriptor);}catch(Exception error){nextDescriptor.close();throw error;}
            session.renderer.close();try{session.descriptor.close();}catch(IOException ignored){}session.renderer=nextRenderer;session.descriptor=nextDescriptor;session.page=count-1;
            if(session==activeSession){renderer=nextRenderer;descriptor=nextDescriptor;showPage(count-1);rebuildThumbnails();}saveSessionState();
        }catch(Exception error){toast("페이지를 다시 열 수 없습니다: "+error.getMessage());}});}catch(Exception error){runOnUiThread(()->{appending.remove(session);if(isFinishing()||isDestroyed())return;progress.dismiss();toast("페이지 추가 실패: "+error.getMessage());});}},"append-page").start();
    }
    private void libraryChanged(File before,File after){
        Uri oldUri=Uri.fromFile(before),newUri=Uri.fromFile(after);for(DocumentSession session:sessions)if(session.uri.equals(oldUri)){session.uri=newUri;session.title=after.getName();session.store.rebind(newUri);if(session==activeSession){documentUri=newUri;documentTitle=session.title;titleView.setText(documentTitle);}}
        if(recentPrefs.getString("last_uri","").equals(oldUri.toString()))recentPrefs.edit().putString("last_uri",newUri.toString()).putString("last_title",after.getName()).apply();updateTabs();saveSessionState();
    }
    private void renameDocument(DocumentSession session){
        if(!library.managed(session.uri)){toast("문서함에 저장한 뒤 이름을 변경하세요");return;}onSelectionAdjustStarted();session.store.save();File before=new File(session.uri.getPath());EditText input=new EditText(this);input.setSingleLine();input.setText(session.title.replaceFirst("(?i)\\.pdf$",""));input.selectAll();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("이름 변경").setView(input).setPositiveButton("저장",null).setNegativeButton("취소",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{String name=NotebookFiles.pdfName(input.getText().toString());dialog.getButton(-1).setEnabled(false);new Thread(()->{try{File after=library.transfer(before,before.getParentFile(),name,true);runOnUiThread(()->{dialog.dismiss();libraryChanged(before,after);});}catch(Exception error){runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);input.setError(error.getMessage());});}},"rename-pdf").start();}catch(Exception error){input.setError(error.getMessage());}}));dialog.show();
    }
    private void saveToLibrary(){if(activeSession==null)return;if(library.managed(activeSession.uri)){showLibrary();return;}final DocumentSession session=activeSession;final Uri source=session.officePreview==null?session.uri:Uri.fromFile(session.officePreview);if(session.officePreview!=null)try{AnnotationStore copy=new AnnotationStore(this);copy.open(source);copy.importJson(session.store.exportJson(session.uri,session.title),session.renderer.getPageCount());}catch(JSONException error){toast("주석 저장 실패");return;}importPdfToLibrary(source,session.title.replaceFirst("(?i)\\.[^.]+$","")+".pdf",currentPage,true);}
    private void exportPdf(){if(activeSession==null)return;try{exportSource=activeSession.officePreview==null?documentUri:Uri.fromFile(activeSession.officePreview);exportSnapshot=store.exportJson(documentUri,documentTitle);exportPageCount=renderer.getPageCount();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,documentTitle.replaceAll("(?i)\\.pdf$","")+"_notes.pdf"),EXPORT_PDF);}catch(JSONException error){toast("PDF 준비 실패");}}
    private void receivePdfExport(int result,Intent data){if(result!=RESULT_OK||data==null||data.getData()==null)return;final Uri source=exportSource,target=data.getData();final String snapshot=exportSnapshot;final int count=exportPageCount;if(source==null||snapshot==null){toast("다시 내보내세요");return;}ProgressDialog progress=ProgressDialog.show(this,"PDF 내보내기","필기·타이핑·이미지를 PDF에 담는 중입니다…",true,false);new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(target,"wt")){if(out==null)throw new IOException("출력 파일을 열 수 없습니다");AnnotationStore annotations=new AnnotationStore(this);annotations.importJson(snapshot,count);DocumentExporter.export(this,source,annotations,out);runOnUiThread(()->{progress.dismiss();toast("PDF를 내보냈습니다");});}catch(Exception error){runOnUiThread(()->{progress.dismiss();toast("PDF 내보내기 실패: "+error.getMessage());});}},"pdf-export").start();}
    private void placeElement(String kind,String asset){if(renderer==null){toast("문서를 먼저 여세요");return;}stopInk();highlightMode=outlineMode=false;memoMode=true;placementKind=kind;placementAsset=asset;pageView.setHighlightMode(false,selectedColor);pageView.setOutlineMode(false);pageView.setMemoMode(true);updateToolStates();toast("본문에서 넣을 위치를 터치하세요");}
    private void createPlacedElement(int page,float x,float y){AnnotationStore.PageElement element=new AnnotationStore.PageElement();element.page=page;element.kind=placementKind;element.asset=placementAsset;element.left=Math.min(.75f,x);element.top=Math.min(.8f,y);element.right=Math.min(.97f,element.left+.6f);element.bottom=Math.min(.98f,element.top+(element.kind.equals("link")?.07f:.18f));placementKind="";memoMode=false;pageView.setMemoMode(false);updateToolStates();if(element.kind.equals("image")){element.bottom=Math.min(.98f,element.top+.35f);store.elements.add(element);store.save();pageView.invalidate();}else editPageElement(element,true);}
    private void editPageElement(AnnotationStore.PageElement element,boolean fresh){final AnnotationStore target=store;EditText input=new EditText(this);input.setText(element.text);input.setHint(element.kind.equals("link")?"https://… 또는 YouTube 주소":"타이핑할 내용");input.setMinLines(3);new AlertDialog.Builder(this).setTitle(element.kind.equals("link")?"웹·유튜브 링크":"타이핑").setView(input).setPositiveButton("저장",(d,w)->{String text=input.getText().toString().trim();if(text.isEmpty())return;if(element.kind.equals("link")&&!validWebUrl(text)){toast("http 또는 https 주소를 입력하세요");return;}element.text=text;if(fresh)target.elements.add(element);target.save();pageView.invalidate();}).setNegativeButton("취소",null).setNeutralButton(fresh?"닫기":"삭제",(d,w)->{if(!fresh){target.elements.remove(element);target.save();pageView.invalidate();}}).show();}
    private boolean validWebUrl(String value){Uri uri=Uri.parse(value);return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))&&uri.getHost()!=null;}
    @Override public void onElementTapped(AnnotationStore.PageElement element){String[] labels=element.kind.equals("image")?new String[]{"위치·크기","삭제"}:element.kind.equals("link")?new String[]{"링크 열기","수정","위치·크기","삭제"}:new String[]{"수정","위치·크기","삭제"};new AlertDialog.Builder(this).setTitle("페이지 "+(element.page+1)).setItems(labels,(d,index)->{String action=labels[index];if(action.equals("링크 열기")){if(validWebUrl(element.text))try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(element.text)));}catch(ActivityNotFoundException error){toast("링크를 열 앱이 없습니다");}}else if(action.equals("수정"))editPageElement(element,false);else if(action.equals("위치·크기"))editElementGeometry(element);else{store.elements.remove(element);store.save();pageView.invalidate();}}).show();}
    private void editElementGeometry(AnnotationStore.PageElement element){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);String[] labels={"왼쪽 (%)","위쪽 (%)","너비 (%)","높이 (%)"};float[] values={element.left*100,element.top*100,(element.right-element.left)*100,(element.bottom-element.top)*100};EditText[] inputs=new EditText[4];for(int i=0;i<4;i++){inputs[i]=new EditText(this);inputs[i].setHint(labels[i]);inputs[i].setInputType(8194);inputs[i].setText(String.format(Locale.US,"%.1f",values[i]));panel.addView(inputs[i]);}new AlertDialog.Builder(this).setTitle("위치·크기 · %").setView(panel).setPositiveButton("적용",(d,w)->{try{float x=Float.parseFloat(inputs[0].getText().toString())/100,y=Float.parseFloat(inputs[1].getText().toString())/100,width=Float.parseFloat(inputs[2].getText().toString())/100,height=Float.parseFloat(inputs[3].getText().toString())/100;if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)||x<0||y<0||width<=0||height<=0||x+width>1||y+height>1)throw new IllegalArgumentException();element.left=x;element.top=y;element.right=x+width;element.bottom=y+height;store.save();pageView.invalidate();}catch(Exception error){toast("페이지 안에 들어가는 위치와 크기를 입력하세요");}}).setNegativeButton("취소",null).show();}
    private void pasteImage(){if(renderer==null)return;ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData data=clipboard.getPrimaryClip();Uri image=data!=null&&data.getItemCount()>0?data.getItemAt(0).getUri():null;if(image!=null&&"content".equals(image.getScheme()))importImage(image);else new AlertDialog.Builder(this).setTitle("이미지 붙여넣기").setMessage("클립보드에 이미지가 없습니다. 브라우저의 ‘이미지 복사’를 사용하거나 저장된 이미지를 선택하세요.").setPositiveButton("이미지 선택",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),IMPORT_IMAGE)).setNegativeButton("닫기",null).show();}
    private void importImage(Uri source){final DocumentSession session=activeSession;new Thread(()->{try{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(source)){BitmapFactory.decodeStream(in,null,options);}if(options.outWidth<=0||options.outHeight<=0)throw new IOException("이미지를 읽을 수 없습니다");options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;Bitmap image;try(InputStream in=getContentResolver().openInputStream(source)){image=BitmapFactory.decodeStream(in,null,options);}if(image==null)throw new IOException("이미지 형식이 지원되지 않습니다");File folder=new File(getFilesDir(),"images");folder.mkdirs();String name=UUID.randomUUID()+".png";try(OutputStream out=new FileOutputStream(new File(folder,name))){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("이미지 저장 실패");}finally{image.recycle();}runOnUiThread(()->{if(session!=null&&sessions.contains(session)){switchDocument(session);placeElement("image",name);}});}catch(Exception error){runOnUiThread(()->toast("이미지 가져오기 실패: "+error.getMessage()));}},"image-paste").start();}
    private void searchDocument(){if(activeSession==null)return;EditText input=new EditText(this);input.setHint("PDF 내용 / 필기 / 노트 검색");new AlertDialog.Builder(this).setTitle("문서·필기 검색").setView(input).setPositiveButton("검색",(d,w)->{String query=input.getText().toString().trim();if(!query.isEmpty())runDocumentSearch(query);}).setNegativeButton("취소",null).show();}
    private void runDocumentSearch(String query){final DocumentSession session=activeSession;final Uri source=session.officePreview==null?session.uri:Uri.fromFile(session.officePreview);final String json;try{json=session.store.exportJson(session.uri,session.title);}catch(JSONException error){return;}final int count=session.renderer.getPageCount();if(searchCanceled!=null)searchCanceled.set(true);final java.util.concurrent.atomic.AtomicBoolean cancel=new java.util.concurrent.atomic.AtomicBoolean();searchCanceled=cancel;ProgressDialog progress=new ProgressDialog(this);progress.setTitle("검색 · "+query);progress.setMessage("본문과 필기를 인식하는 중입니다…");progress.setCancelable(true);progress.setOnCancelListener(d->cancel.set(true));progress.setButton(DialogInterface.BUTTON_NEGATIVE,"중지",(d,w)->cancel.set(true));progress.show();new Thread(()->{try{AnnotationStore snapshot=new AnnotationStore(this);snapshot.importJson(json,count);List<SearchScanner.Hit> hits=SearchScanner.scan(this,source,snapshot,query,cancel,(page,total)->runOnUiThread(()->progress.setMessage(page+" / "+total+" 페이지 인식 중")));runOnUiThread(()->{progress.dismiss();if(cancel.get()||isFinishing()||isDestroyed()||!sessions.contains(session))return;if(hits.isEmpty()){toast("검색 결과가 없습니다. 손글씨는 OCR 인식 상태에 따라 달라집니다");return;}String[] labels=new String[hits.size()];for(int i=0;i<hits.size();i++){SearchScanner.Hit h=hits.get(i);labels[i]="p."+(h.page+1)+" · "+h.text;}new AlertDialog.Builder(this).setTitle("검색 결과 · "+hits.size()).setItems(labels,(d,index)->{SearchScanner.Hit hit=hits.get(index);switchDocument(session);showPage(hit.page);pageView.post(()->pageView.focusOnPoint(hit.x,hit.y));}).setNegativeButton("닫기",null).show();});}catch(Exception error){runOnUiThread(()->{progress.dismiss();if(!cancel.get())toast("검색 실패: "+error.getMessage());});}},"document-search").start();}
    private void showHelp(){new AlertDialog.Builder(this).setTitle("PDF Note 사용법").setMessage("• 읽기: 본문을 빠르게 스와이프해 페이지 넘기기, 확대 상태에서는 드래그로 이동\n• 텍스트 선택: 단어를 길게 누른 뒤 드래그, 또는 필기·삽입의 텍스트 선택 모드\n• 선택 팝업: 하이라이트·복사·번역·읽어주기·단어장 찾기·개요 추가·메모\n• 단어장 연결: ‘단어장 찾기’ 후 사전앱의 ‘PDF로 돌아가기’ 버튼\n• 포스트잇: 메모와 번역을 펼치기·최소화·숨기기로 관리\n• 문서 추가: 상단 폴더 또는 탭의 + 버튼\n• HWP·HWPX·DOC·DOCX·PPT·PPTX·XLS·XLSX: PDF로 변환해 저장하고 엽니다. 서식은 변환 엔진과 글꼴에 따라 달라질 수 있습니다\n• HWP·DOC 본문 미리보기는 글자만 표시합니다\n• 문서 전환·닫기: 상단 문서 탭과 × 버튼\n• 앱 재실행: 열었던 탭과 마지막 페이지 자동 복원\n• 필기·하이라이트: 아래쪽 필기도구 아이콘, 손가락 필기 켜기 지원\n• 페이지 썸네일: 상단 페이지 목록 아이콘\n• 개요 저장: 선택 팝업 또는 메뉴의 ‘개요 지점 추가’\n• 페이지 넘김: 본문 양옆의 반투명 화살표\n• 스와이프 넘김: 읽기 모드에서 본문을 빠르게 좌우로 밀기. 확대 시 가장자리에서 넘기기, 보기·이동에서 방향 변경\n• 두 쪽 보기: 도구에서 전환, 각 페이지 터치 후 필기\n• 문서함: 정렬·표지/목록 보기·이름 검색, 폴더의 ⋮에서 색상 변경\n• 문서 선택: 여러 문서 복사·이동·삭제, 휴지통에서 복원\n• 메뉴: 문서 / 보기·이동 / 필기·삽입 / 학습·주석 / 내보내기·백업\n• PDF 가져오기: 선택한 폴더에 자동 저장, 탭 이름을 길게 눌러 이름 변경\n• 새 노트: 백지·줄노트·모눈종이와 배경색 선택, 마지막 장에서 넘기면 자동 추가\n• 페이지 미리보기: ＋ 페이지로 새 장 추가\n• 타이핑·이미지 붙여넣기·웹/YouTube 링크: 도구에서 선택 후 페이지에 배치\n• PDF 내보내기: 필기·타이핑·이미지를 포함해 저장\n• 검색: 상단 돋보기로 본문·필기·메모 검색, 손글씨 인식률에 따라 결과가 달라집니다\n• 확대: 두 손가락으로 핀치\n• 확대 화면 이동: 한 손가락으로 상하좌우 드래그\n• 전체 화면: 위쪽 확장 아이콘\n• 즐겨찾기: 별 아이콘\n\n필기·번역·개요·하이라이트·메모·즐겨찾기는 문서별로 저장되며 원본 PDF는 변경하지 않습니다. Translate 앱을 사용하면 선택한 문장이 해당 앱으로 전달됩니다. 기기 내 번역은 최초 모델 다운로드가 필요합니다.").setPositiveButton("확인",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void closeAllDocuments(){for(DocumentSession s:new ArrayList<>(sessions)){if(s.renderer!=null)s.renderer.close();if(s.descriptor!=null)try{s.descriptor.close();}catch(IOException ignored){}if(s.officePreview!=null)s.officePreview.delete();}sessions.clear();renderer=null;descriptor=null;}
    @Override protected void onStop(){onSelectionAdjustStarted();saveSessionState();super.onStop();}
    @Override protected void onDestroy(){if(libraryDialog!=null)libraryDialog.dismiss();if(searchCanceled!=null)searchCanceled.set(true);if(hwpConversion!=null)hwpConversion.cancel();saveSessionState();++ocrGeneration;if(speech!=null){speech.stop();speech.shutdown();speech=null;}if(latinRecognizer!=null)latinRecognizer.close();if(koreanRecognizer!=null)koreanRecognizer.close();closeAllDocuments();super.onDestroy();}
}
