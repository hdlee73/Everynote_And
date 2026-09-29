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
    private static final class DocumentSession {
        Uri uri; String title; ParcelFileDescriptor descriptor; PdfRenderer renderer;
        AnnotationStore store; int page; File officePreview;
        final Deque<AnnotationStore.InkStroke> redoStrokes=new ArrayDeque<>();
        final Map<Integer,List<PdfPageView.TextRegion>> textRegions=new HashMap<>();
    }
    private static final int OPEN_PDF=10, EXPORT_JSON=11, NAVY=0xFF173B63, ACCENT=0xFF2563EB;
    private PdfRenderer renderer; private ParcelFileDescriptor descriptor; private Uri documentUri;
    private String documentTitle="PDF"; private int currentPage, selectedColor=0x66FFDE59;
    private PdfPageView pageView; private TextView titleView,pageLabel; private Button highlightButton;
    private ImageButton bookmarkButton,colorButton,memoButton,inkButton,fullscreenExit; private LinearLayout header,bottomBar;
    private FrameLayout root; private AnnotationStore store; private boolean highlightMode,memoMode,outlineMode,fullscreen;
    private boolean verticalPageSwipe;
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

    @Override protected void onCreate(Bundle state){super.onCreate(state);recentPrefs=getSharedPreferences("recent_documents",MODE_PRIVATE);verticalPageSwipe=recentPrefs.getBoolean("vertical_page_swipe",false);buildUi();pageView.setVerticalPageSwipe(verticalPageSwipe);Uri u=getIntent().getData();if(u!=null)openPdf(u);else if(!restoreSession())showWelcome();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private ImageButton icon(int image,String label,int tint,View.OnClickListener click){ImageButton b=new ImageButton(this);b.setImageResource(image);b.setColorFilter(tint);b.setContentDescription(label);b.setBackgroundColor(Color.TRANSPARENT);b.setScaleType(ImageView.ScaleType.CENTER);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setOnClickListener(click);return b;}

    private void buildUi(){
        root=new FrameLayout(this);root.setBackgroundColor(0xFFF1F3F5);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(4),0,dp(4),0);header.setBackgroundColor(NAVY);
        header.addView(icon(R.drawable.ic_folder_open,"PDF 열기",Color.WHITE,v->choosePdf()),new LinearLayout.LayoutParams(dp(52),dp(56)));
        titleView=new TextView(this);titleView.setTextColor(Color.WHITE);titleView.setTextSize(19);titleView.setSingleLine();titleView.setGravity(Gravity.CENTER_VERTICAL);titleView.setPadding(dp(8),0,dp(8),0);header.addView(titleView,new LinearLayout.LayoutParams(0,dp(56),1));
        header.addView(icon(R.drawable.ic_thumbnails,"페이지 목록",Color.WHITE,v->toggleSidebar()),new LinearLayout.LayoutParams(dp(48),dp(56)));
        header.addView(icon(R.drawable.ic_outline,"개요",Color.WHITE,v->showOutlineList()),new LinearLayout.LayoutParams(dp(48),dp(56)));
        header.addView(icon(R.drawable.ic_fullscreen,"전체 화면",Color.WHITE,v->toggleFullscreen()),new LinearLayout.LayoutParams(dp(48),dp(56)));
        header.addView(icon(R.drawable.ic_more_vert,"도구",Color.WHITE,v->showTools()),new LinearLayout.LayoutParams(dp(48),dp(56)));content.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        tabStrip=new HorizontalScrollView(this);tabStrip.setHorizontalScrollBarEnabled(false);tabStrip.setBackgroundColor(0xFFF8FAFC);tabRow=new LinearLayout(this);tabRow.setGravity(Gravity.CENTER_VERTICAL);tabRow.setPadding(dp(6),dp(4),dp(6),dp(4));tabStrip.addView(tabRow,new HorizontalScrollView.LayoutParams(-2,-1));content.addView(tabStrip,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout viewerRow=new LinearLayout(this);viewerRow.setOrientation(LinearLayout.HORIZONTAL);
        thumbnailPanel=new ScrollView(this);thumbnailPanel.setBackgroundColor(0xFFF1F5F9);thumbnailPanel.setVisibility(View.GONE);thumbnailList=new LinearLayout(this);thumbnailList.setOrientation(LinearLayout.VERTICAL);thumbnailList.setPadding(dp(7),dp(8),dp(7),dp(8));thumbnailPanel.addView(thumbnailList,new ScrollView.LayoutParams(-1,-2));viewerRow.addView(thumbnailPanel,new LinearLayout.LayoutParams(dp(116),-1));
        pageView=new PdfPageView(this,this);viewerRow.addView(pageView,new LinearLayout.LayoutParams(0,-1,1));content.addView(viewerRow,new LinearLayout.LayoutParams(-1,0,1));
        bottomBar=new LinearLayout(this);bottomBar.setGravity(Gravity.CENTER_VERTICAL);bottomBar.setPadding(dp(4),dp(4),dp(4),dp(4));bottomBar.setBackgroundColor(Color.WHITE);bottomBar.setElevation(dp(10));
        bottomBar.addView(icon(R.drawable.ic_chevron_left,"이전 페이지",NAVY,v->animatePage(-1)),new LinearLayout.LayoutParams(dp(44),dp(56)));
        pageLabel=new TextView(this);pageLabel.setGravity(Gravity.CENTER);pageLabel.setTextColor(0xFF475569);pageLabel.setTextSize(14);pageLabel.setSingleLine();pageLabel.setBackground(round(0xFFF1F5F9,18));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(38),1);pp.setMargins(dp(2),0,dp(2),0);bottomBar.addView(pageLabel,pp);
        bottomBar.addView(icon(R.drawable.ic_chevron_right,"다음 페이지",NAVY,v->animatePage(1)),new LinearLayout.LayoutParams(dp(44),dp(56)));
        highlightButton=new Button(this);highlightButton.setText("하이라이트");highlightButton.setTextSize(13);highlightButton.setTextColor(NAVY);highlightButton.setAllCaps(false);highlightButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_highlight,0,0,0);highlightButton.setCompoundDrawablePadding(dp(4));highlightButton.setBackground(round(0xFFEFF6FF,18));highlightButton.setOnClickListener(v->toggleHighlight());LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,dp(42));hp.setMargins(dp(4),0,dp(4),0);bottomBar.addView(highlightButton,hp);
        colorButton=icon(R.drawable.ic_palette,"하이라이트 색상",NAVY,v->chooseColor());bottomBar.addView(colorButton,new LinearLayout.LayoutParams(dp(46),dp(46)));updateColorButton();
        memoButton=icon(R.drawable.ic_note_add,"메모 추가",NAVY,v->toggleMemoMode());bottomBar.addView(memoButton,new LinearLayout.LayoutParams(dp(44),dp(56)));
        inkButton=icon(R.drawable.ic_ink,"필기도구",NAVY,v->showInkTools());bottomBar.addView(inkButton,new LinearLayout.LayoutParams(dp(44),dp(56)));
        bookmarkButton=icon(R.drawable.ic_star_outline,"즐겨찾기",NAVY,v->toggleBookmark());bottomBar.addView(bookmarkButton,new LinearLayout.LayoutParams(dp(48),dp(56)));content.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(64)));
        fullscreenExit=icon(R.drawable.ic_fullscreen_exit,"전체 화면 종료",Color.WHITE,v->toggleFullscreen());fullscreenExit.setBackground(round(0xAA0F172A,24));fullscreenExit.setVisibility(View.GONE);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);ep.setMargins(0,dp(12),dp(12),0);root.addView(fullscreenExit,ep);
        root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());top=b.top;bottom=b.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}if(!fullscreen){header.setPadding(dp(4),top,dp(4),0);header.getLayoutParams().height=dp(56)+top;bottomBar.setPadding(dp(4),dp(4),dp(4),dp(4)+bottom);bottomBar.getLayoutParams().height=dp(64)+bottom;}return insets;});setContentView(root);
    }
    private void showWelcome(){titleView.setText("PDF Note");pageLabel.setText("PDF를 열어 시작하세요");}
    private void saveSessionState(){try{JSONArray a=new JSONArray();for(DocumentSession s:sessions)a.put(new JSONObject().put("uri",s.uri.toString()).put("page",s==activeSession?currentPage:s.page));recentPrefs.edit().putString("open_sessions",a.toString()).putString("active_uri",activeSession==null?"":activeSession.uri.toString()).apply();}catch(JSONException ignored){}}
    private boolean restoreSession(){String raw=recentPrefs.getString("open_sessions",null);if(raw==null)return false;try{JSONArray a=new JSONArray(raw);String active=recentPrefs.getString("active_uri","");DocumentSession target=null;for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Uri u=Uri.parse(o.getString("uri"));openPdf(u);if(activeSession!=null&&activeSession.uri.equals(u)){activeSession.page=Math.max(0,o.optInt("page",0));if(u.toString().equals(active))target=activeSession;}}if(target==null&&!sessions.isEmpty())target=sessions.get(sessions.size()-1);if(target!=null){switchDocument(target);return true;}}catch(Exception ignored){}return !sessions.isEmpty();}
    private void choosePdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","application/msword","application/x-hwp","application/vnd.hancom.hwp","application/octet-stream"});startActivityForResult(i,OPEN_PDF);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==TRANSLATE_EXTERNAL){receiveExternalTranslation(result,data);return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(req==OPEN_PDF){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}openPdf(u);}else if(req==EXPORT_JSON){try(OutputStream out=getContentResolver().openOutputStream(u)){if(out!=null)out.write(store.exportJson(documentUri,documentTitle).getBytes());toast("주석을 내보냈습니다");}catch(Exception e){toast("내보내기 실패: "+e.getMessage());}}}
    private void openPdf(Uri u){for(DocumentSession s:sessions)if(s.uri.equals(u)){switchDocument(s);return;}DocumentSession s=new DocumentSession();try{
        s.title=queryName(u);
        if(OfficeImporter.isOffice(s.title)){
            s.officePreview=OfficeImporter.createPreview(this,u,s.title);
            s.descriptor=ParcelFileDescriptor.open(s.officePreview,ParcelFileDescriptor.MODE_READ_ONLY);
            toast("본문 글자 미리보기로 열었습니다. 표·그림·원본 서식은 반영되지 않습니다");
        }else if(s.title.toLowerCase(Locale.ROOT).endsWith(".pdf")||"application/pdf".equals(getContentResolver().getType(u))){
            s.descriptor=getContentResolver().openFileDescriptor(u,"r");
        }else throw new IOException("PDF, HWP 또는 DOC 파일을 선택하세요");
        if(s.descriptor==null)throw new IOException("파일을 읽을 수 없습니다");s.renderer=new PdfRenderer(s.descriptor);s.uri=u;s.store=new AnnotationStore(this);s.store.open(u);sessions.add(s);recentPrefs.edit().putString("last_uri",u.toString()).putString("last_title",s.title).apply();switchDocument(s);
    }catch(Exception e){if(s.renderer!=null)s.renderer.close();if(s.descriptor!=null)try{s.descriptor.close();}catch(IOException ignored){}if(s.officePreview!=null)s.officePreview.delete();toast("문서 열기 실패: "+e.getMessage());if(sessions.isEmpty())showWelcome();}}
    private void switchDocument(DocumentSession s){if(activeSession!=null)activeSession.page=currentPage;activeSession=s;renderer=s.renderer;descriptor=s.descriptor;documentUri=s.uri;documentTitle=s.title;store=s.store;titleView.setText(documentTitle);highlightMode=memoMode=outlineMode=false;inkMode=0;pageView.stopTextSelection();pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(false);pageView.setInkTool(0,inkColor,inkWidth);updateToolStates();updateInkButton();updateTabs();showPage(Math.min(s.page,renderer.getPageCount()-1));rebuildThumbnails();saveSessionState();}
    private void closeDocument(DocumentSession s){int oldIndex=sessions.indexOf(s);sessions.remove(s);if(s.renderer!=null)s.renderer.close();if(s.descriptor!=null)try{s.descriptor.close();}catch(IOException ignored){}if(s.officePreview!=null)s.officePreview.delete();if(s==activeSession){activeSession=null;if(sessions.isEmpty()){renderer=null;descriptor=null;documentUri=null;store=null;pageView.clearPage();thumbnailList.removeAllViews();updateTabs();showWelcome();}else switchDocument(sessions.get(Math.max(0,Math.min(oldIndex,sessions.size()-1))));}else updateTabs();saveSessionState();}
    private void updateTabs(){tabRow.removeAllViews();for(DocumentSession s:sessions){LinearLayout chip=new LinearLayout(this);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setPadding(dp(10),0,dp(2),0);chip.setBackground(round(s==activeSession?0xFFDCEAFE:0xFFEFF2F6,14));TextView name=new TextView(this);name.setText(s.title);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setTextColor(s==activeSession?ACCENT:0xFF475569);name.setTextSize(13);name.setOnClickListener(v->switchDocument(s));chip.addView(name,new LinearLayout.LayoutParams(dp(132),dp(34)));TextView close=new TextView(this);close.setText("×");close.setGravity(Gravity.CENTER);close.setTextSize(21);close.setTextColor(0xFF64748B);close.setContentDescription(s.title+" 닫기");close.setOnClickListener(v->closeDocument(s));chip.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(34));cp.setMargins(dp(3),0,dp(3),0);tabRow.addView(chip,cp);}TextView add=new TextView(this);add.setText("＋");add.setGravity(Gravity.CENTER);add.setTextSize(24);add.setTextColor(ACCENT);add.setContentDescription("PDF 추가");add.setOnClickListener(v->choosePdf());tabRow.addView(add,new LinearLayout.LayoutParams(dp(44),dp(34)));}
    private String queryName(Uri u){try(android.database.Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}return u.getLastPathSegment()==null?"PDF":u.getLastPathSegment();}
    private void toggleSidebar(){sidebarVisible=!sidebarVisible;thumbnailPanel.setVisibility(sidebarVisible?View.VISIBLE:View.GONE);if(sidebarVisible)rebuildThumbnails();}
    private void rebuildThumbnails(){final int generation=++thumbnailGeneration;thumbnailList.removeAllViews();if(!sidebarVisible||renderer==null)return;for(int i=0;i<renderer.getPageCount();i++){final int page=i;LinearLayout item=new LinearLayout(this);item.setTag(page);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(4),dp(5),dp(4),dp(7));ImageView preview=new ImageView(this);preview.setTag("image");preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.WHITE);item.addView(preview,new LinearLayout.LayoutParams(dp(94),dp(126)));TextView number=new TextView(this);number.setText(String.valueOf(i+1));number.setGravity(Gravity.CENTER);number.setTextSize(12);number.setTextColor(0xFF475569);item.addView(number,new LinearLayout.LayoutParams(-1,dp(24)));item.setOnClickListener(v->showPage(page));thumbnailList.addView(item,new LinearLayout.LayoutParams(-1,dp(160)));}updateThumbnailSelection();renderThumbnail(0,generation,activeSession);}
    private void renderThumbnail(int index,int generation,DocumentSession session){if(generation!=thumbnailGeneration||session!=activeSession||renderer==null||index>=renderer.getPageCount())return;thumbnailList.post(()->{if(generation!=thumbnailGeneration||session!=activeSession)return;try(PdfRenderer.Page page=renderer.openPage(index)){int width=188;float ratio=(float)width/page.getWidth();Bitmap image=Bitmap.createBitmap(width,Math.max(1,(int)(page.getHeight()*ratio)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.postScale(ratio,ratio);page.render(image,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);View item=thumbnailList.findViewWithTag(index);if(item instanceof LinearLayout){View child=((LinearLayout)item).getChildAt(0);if(child instanceof ImageView)((ImageView)child).setImageBitmap(image);}}catch(Exception ignored){}renderThumbnail(index+1,generation,session);});}
    private void updateThumbnailSelection(){for(int i=0;i<thumbnailList.getChildCount();i++){View v=thumbnailList.getChildAt(i);Object tag=v.getTag();boolean selected=tag instanceof Integer&&((Integer)tag)==currentPage;GradientDrawable bg=round(selected?0xFFDCEAFE:Color.TRANSPARENT,8);if(selected)bg.setStroke(dp(2),ACCENT);v.setBackground(bg);}if(sidebarVisible&&currentPage<thumbnailList.getChildCount())thumbnailList.getChildAt(currentPage).post(()->thumbnailPanel.smoothScrollTo(0,Math.max(0,thumbnailList.getChildAt(currentPage).getTop()-dp(16))));}
    private void showPage(int index){onSelectionAdjustStarted();if(renderer==null||index<0||index>=renderer.getPageCount())return;currentPage=index;if(activeSession!=null)activeSession.page=index;try(PdfRenderer.Page page=renderer.openPage(index)){int width=Math.max(1080,getResources().getDisplayMetrics().widthPixels*2);float ratio=Math.min(2.5f,(float)width/page.getWidth());Bitmap image=Bitmap.createBitmap((int)(page.getWidth()*ratio),(int)(page.getHeight()*ratio),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);Matrix m=new Matrix();m.postScale(ratio,ratio);page.render(image,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);pageView.showPage(image,index,store.marks,store.strokes,store.translations);pageLabel.setText((index+1)+" / "+renderer.getPageCount());updateBookmarkButton();updateThumbnailSelection();saveSessionState();List<PdfPageView.TextRegion> cached=activeSession.textRegions.get(index);if(cached!=null)pageView.setTextRegions(cached,false);else pageView.post(()->recognizePageText(false));}}
    private void toggleHighlight(){if(renderer==null)return;highlightMode=!highlightMode;memoMode=outlineMode=false;stopInk();updateToolStates();pageView.setMemoMode(false);pageView.setOutlineMode(false);pageView.setHighlightMode(highlightMode,selectedColor);toast(highlightMode?"문장을 따라 좌우로 드래그하세요":"하이라이트를 종료했습니다");}
    private void toggleMemoMode(){if(renderer==null)return;memoMode=!memoMode;highlightMode=outlineMode=false;stopInk();updateToolStates();pageView.setHighlightMode(false,selectedColor);pageView.setOutlineMode(false);pageView.setMemoMode(memoMode);toast(memoMode?"메모를 놓을 위치를 탭하세요":"메모 추가를 종료했습니다");}
    private void toggleOutlineMode(){if(renderer==null)return;outlineMode=!outlineMode;highlightMode=memoMode=false;stopInk();pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(outlineMode);updateToolStates();toast(outlineMode?"개요로 저장할 정확한 위치를 탭하세요":"개요 지점 선택을 종료했습니다");}
    private void updateToolStates(){highlightButton.setText(highlightMode?"하이라이트 ✓":"하이라이트");highlightButton.setTextColor(highlightMode?Color.WHITE:NAVY);highlightButton.setBackground(round(highlightMode?ACCENT:0xFFEFF6FF,18));memoButton.setColorFilter(memoMode?Color.WHITE:NAVY);memoButton.setBackground(memoMode?round(ACCENT,22):round(Color.TRANSPARENT,22));}
    private void stopInk(){inkMode=0;pageView.setInkTool(0,inkColor,inkWidth);updateInkButton();}
    private void setInkMode(int mode){if(renderer==null)return;inkMode=mode;highlightMode=memoMode=outlineMode=false;pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(false);pageView.setInkTool(mode,inkColor,inkWidth);updateToolStates();updateInkButton();toast(mode==1?"S펜으로 필기하세요. 손가락은 화면 이동에 사용됩니다":mode==2?"S펜으로 지울 획을 터치하세요":"필기 모드를 종료했습니다");}
    private void updateInkButton(){if(inkButton==null)return;inkButton.setColorFilter(inkMode==0?NAVY:Color.WHITE);inkButton.setBackground(inkMode==0?round(Color.TRANSPARENT,22):round(inkMode==2?0xFFDC2626:ACCENT,22));inkButton.setContentDescription(inkMode==1?"펜 사용 중":inkMode==2?"지우개 사용 중":"필기도구");}
    private void showInkTools(){
        if(renderer==null){toast("문서를 먼저 여세요");return;}
        String[] labels={"펜"+(inkMode==1?" ✓":""),"지우개"+(inkMode==2?" ✓":""),"필기 종료","실행 취소",
            "다시 실행","펜 색상","펜 굵기","사용 안내"};
        int[] icons={R.drawable.ic_ink,R.drawable.ic_eraser,R.drawable.ic_check,R.drawable.ic_undo,
            R.drawable.ic_chevron_right,R.drawable.ic_palette,R.drawable.ic_ink,R.drawable.ic_outline};
        Runnable[] actions={()->setInkMode(1),()->setInkMode(2),()->setInkMode(0),this::undoInk,
            this::redoInk,this::chooseInkColor,this::chooseInkWidth,this::showInkHelp};
        showIconMenu("S펜 필기도구",labels,icons,actions);
    }
    private void chooseInkColor(){String[] names={"차콜","블루","레드","그린","퍼플"};int[] colors={0xFF172033,0xFF2563EB,0xFFDC2626,0xFF16835B,0xFF7C3AED};new AlertDialog.Builder(this).setTitle("펜 색상").setSingleChoiceItems(names,indexOf(colors,inkColor),(d,w)->{inkColor=colors[w];pageView.setInkTool(inkMode,inkColor,inkWidth);d.dismiss();}).setNegativeButton("취소",null).show();}
    private int indexOf(int[] values,int value){for(int i=0;i<values.length;i++)if(values[i]==value)return i;return 0;}
    private void chooseInkWidth(){String[] names={"가늘게","보통","굵게","아주 굵게"};float[] widths={0.0022f,0.004f,0.0065f,0.009f};int checked=0;for(int i=0;i<widths.length;i++)if(Math.abs(widths[i]-inkWidth)<0.0001f)checked=i;final int selected=checked;new AlertDialog.Builder(this).setTitle("기본 펜 굵기").setSingleChoiceItems(names,selected,(d,w)->{inkWidth=widths[w];pageView.setInkTool(inkMode,inkColor,inkWidth);d.dismiss();}).setNegativeButton("취소",null).show();}
    private void undoInk(){if(store==null)return;for(int i=store.strokes.size()-1;i>=0;i--){AnnotationStore.InkStroke s=store.strokes.get(i);if(s.page==currentPage){store.strokes.remove(i);activeSession.redoStrokes.push(s);store.save();pageView.invalidate();toast("마지막 필기를 취소했습니다");return;}}toast("취소할 필기가 없습니다");}
    private void redoInk(){if(activeSession==null||activeSession.redoStrokes.isEmpty()){toast("다시 실행할 필기가 없습니다");return;}AnnotationStore.InkStroke s=activeSession.redoStrokes.pop();store.strokes.add(s);store.save();if(s.page!=currentPage)showPage(s.page);else pageView.invalidate();}
    private void showInkHelp(){new AlertDialog.Builder(this).setTitle("S펜 필기 안내").setMessage("• S펜: 필기 또는 지우개\n• 손가락: 확대·이동·페이지 넘김\n• 필압: 누르는 힘에 따라 선 굵기 변화\n• S펜 측면 버튼: 누르는 동안 임시 지우개\n• 펜 뒤쪽 지우개: 지원 기기에서 자동 인식\n\n일반 정전식 펜과 손가락은 필압을 지원하지 않습니다.").setPositiveButton("확인",null).show();}
    private void updateColorButton(){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(selectedColor|0xFF000000);d.setStroke(dp(3),Color.WHITE);colorButton.setBackground(d);colorButton.setColorFilter(Color.WHITE);}
    private void chooseColor(){String[] names={"선샤인","민트","로즈","스카이","라벤더"};int[] colors={0x66FFDE59,0x6654C27A,0x66FF6B9A,0x66549CF5,0x66B67CF2};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(6),dp(22),dp(18));TextView guide=new TextView(this);guide.setText("선택한 문장의 하이라이트 미리보기");guide.setTextColor(0xFF64748B);guide.setTextSize(14);guide.setPadding(dp(12),dp(14),dp(12),dp(14));guide.setBackground(round(selectedColor,12));panel.addView(guide);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);LinearLayout.LayoutParams paletteParams=new LinearLayout.LayoutParams(-1,-2);paletteParams.topMargin=dp(20);panel.addView(row,paletteParams);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("하이라이트 색상").setView(panel).setNegativeButton("취소",null).create();for(int i=0;i<colors.length;i++){final int c=colors[i];ImageButton s=new ImageButton(this);s.setContentDescription(names[i]);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(16));bg.setColor(c|0xFF000000);bg.setStroke(dp(c==selectedColor?4:1),c==selectedColor?NAVY:0xFFE2E8F0);s.setBackground(bg);if(c==selectedColor){s.setImageResource(R.drawable.ic_check);s.setColorFilter(NAVY);}s.setPadding(dp(12),dp(12),dp(12),dp(12));s.setOnClickListener(v->{selectedColor=c;pageView.setHighlightMode(highlightMode,selectedColor);updateColorButton();dialog.dismiss();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(4),0,dp(4),0);row.addView(s,p);}dialog.show();}
    private void toggleBookmark(){if(renderer==null)return;if(!store.bookmarks.add(currentPage))store.bookmarks.remove(currentPage);store.save();updateBookmarkButton();}
    private void updateBookmarkButton(){boolean marked=renderer!=null&&store.bookmarks.contains(currentPage);bookmarkButton.setImageResource(marked?R.drawable.ic_star:R.drawable.ic_star_outline);bookmarkButton.setColorFilter(marked?0xFFF59E0B:NAVY);bookmarkButton.setContentDescription(marked?"즐겨찾기 해제":"즐겨찾기 추가");}
    private void toggleFullscreen(){fullscreen=!fullscreen;header.setVisibility(fullscreen?View.GONE:View.VISIBLE);tabStrip.setVisibility(fullscreen?View.GONE:View.VISIBLE);bottomBar.setVisibility(fullscreen?View.GONE:View.VISIBLE);fullscreenExit.setVisibility(fullscreen?View.VISIBLE:View.GONE);if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(fullscreen)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}}else getWindow().getDecorView().setSystemUiVisibility(fullscreen?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:View.SYSTEM_UI_FLAG_VISIBLE);root.requestApplyInsets();}
    @Override public void onBackPressed(){if(fullscreen)toggleFullscreen();else super.onBackPressed();}
    @Override public void onHighlightCreated(AnnotationStore.Mark mark){store.marks.add(mark);store.save();pageView.invalidate();}
    @Override public void onMemoPointRequested(int page,float x,float y){EditText input=new EditText(this);input.setHint("메모를 입력하세요");input.setPadding(dp(24),dp(12),dp(24),dp(12));new AlertDialog.Builder(this).setTitle("새 메모 포스트잇").setView(input).setPositiveButton("저장",(d,w)->{String note=input.getText().toString().trim();if(note.isEmpty())return;AnnotationStore.Mark m=new AnnotationStore.Mark();m.page=page;m.left=Math.max(0f,x-0.025f);m.right=Math.min(1f,x+0.025f);m.top=Math.max(0f,y-0.025f);m.bottom=Math.min(1f,y+0.025f);m.color=selectedColor;m.note=note;m.noteOnly=true;store.marks.add(m);store.save();pageView.invalidate();toast("메모 포스트잇을 저장했습니다");}).setNegativeButton("취소",null).show();}
    @Override public void onMarkTapped(AnnotationStore.Mark mark){editMark(mark);}
    @Override public void onZoomGestureStarted(){if(highlightMode||memoMode||outlineMode){highlightMode=memoMode=outlineMode=false;pageView.setHighlightMode(false,selectedColor);pageView.setMemoMode(false);pageView.setOutlineMode(false);updateToolStates();}}
    @Override public void onPageSwipe(int direction){animatePage(direction);}
    private void animatePage(int direction){
        if(pageAnimating||renderer==null||currentPage+direction<0||currentPage+direction>=renderer.getPageCount())return;
        pageAnimating=true;
        float offset=dp(26)*direction;
        boolean vertical=verticalPageSwipe;
        pageView.animate().alpha(0.45f).translationX(vertical?0:-offset).translationY(vertical?-offset:0)
            .setDuration(110).withEndAction(()->{
                showPage(currentPage+direction);
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
    private void startTextSelection(){recognizePageText(true);}
    private void recognizePageText(boolean announce){if(renderer==null)return;final DocumentSession session=activeSession;final int page=currentPage;final int generation=++ocrGeneration;Bitmap copy=pageView.copyPageBitmap();if(copy==null)return;if(announce)toast("페이지의 글자를 다시 찾는 중입니다…");if(latinRecognizer==null){latinRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);koreanRecognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());}final int width=copy.getWidth(),height=copy.getHeight();InputImage image=InputImage.fromBitmap(copy,0);latinRecognizer.process(image).addOnCompleteListener(latin->{koreanRecognizer.process(image).addOnCompleteListener(korean->{if(!copy.isRecycled())copy.recycle();if(generation!=ocrGeneration||session!=activeSession||page!=currentPage)return;Text result=null;if(latin.isSuccessful())result=latin.getResult();if(korean.isSuccessful()&&(result==null||korean.getResult().getText().length()>result.getText().length()))result=korean.getResult();if(result==null){if(announce)toast("글자를 인식하지 못했습니다");return;}List<PdfPageView.TextRegion> regions=makeTextRegions(result,width,height);if(regions.isEmpty()){if(announce)toast("선택할 글자를 찾지 못했습니다");return;}session.textRegions.put(page,regions);pageView.setTextRegions(regions,announce);if(announce)toast("첫 단어부터 원하는 문장 끝까지 계속 드래그하세요");});});}
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
        String[] labels={"하이라이트","복사","번역","읽어주기","단어장","개요","메모"};
        int[] icons={R.drawable.ic_highlight,R.drawable.ic_copy,R.drawable.ic_translate,R.drawable.ic_speaker,
            R.drawable.ic_dictionary,R.drawable.ic_outline,R.drawable.ic_note_add};
        Runnable[] actions={()->addOcrHighlights(selection.bounds),()->copySelectedText(selection.text),()->translateText(selection.text,selection.unionBounds),
            ()->readAloud(selection.text),()->openDictionary(selection.text),()->promptOutline(currentPage,selection.unionBounds.left,selection.unionBounds.top,selection.text),
            ()->onMemoPointRequested(currentPage,selection.unionBounds.right,selection.unionBounds.top)};
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
    private void showIconMenu(String title,String[] labels,int[] icons,Runnable[] actions){
        ScrollView scroll=new ScrollView(this);LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);grid.setPadding(dp(12),dp(6),dp(12),dp(10));scroll.addView(grid);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("닫기",null).create();
        for(int i=0;i<labels.length;i+=2){LinearLayout row=new LinearLayout(this);grid.addView(row);
            for(int j=i;j<Math.min(i+2,labels.length);j++){final int index=j;menuTile(row,labels[j],icons[j],()->{dialog.dismiss();actions[index].run();});}
            if(i+1>=labels.length)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(71),1));
        }
        dialog.show();int height=Math.min(dp(660),getResources().getDisplayMetrics().heightPixels-dp(130));dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.92f),height);
    }
    private void showTools(){
        String[] labels={"글자 다시 인식","번역 포스트잇","전체 화면","S펜 도구","페이지 목록","개요 추가","개요 목록",
            "넘김 방향 · "+(verticalPageSwipe?"수직":"수평"),"메모·하이라이트","즐겨찾기","페이지로 이동","주석 백업","사용법"};
        int[] icons={R.drawable.ic_scan,R.drawable.ic_translate,R.drawable.ic_fullscreen,R.drawable.ic_ink,R.drawable.ic_thumbnails,
            R.drawable.ic_note_add,R.drawable.ic_outline,R.drawable.ic_chevron_right,R.drawable.ic_highlight,R.drawable.ic_star,
            R.drawable.ic_thumbnails,R.drawable.ic_copy,R.drawable.ic_outline};
        Runnable[] actions={this::startTextSelection,this::showTranslations,this::toggleFullscreen,this::showInkTools,this::toggleSidebar,
            this::toggleOutlineMode,this::showOutlineList,this::choosePageSwipeDirection,this::showMarkList,this::showBookmarks,
            this::goToPage,this::exportAnnotations,this::showHelp};
        showIconMenu("도구",labels,icons,actions);
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
    private void choosePageSwipeDirection(){String[] choices={"수평 · 좌우로 넘기기","수직 · 위아래로 넘기기"};new AlertDialog.Builder(this).setTitle("페이지 넘김 방향").setSingleChoiceItems(choices,verticalPageSwipe?1:0,(dialog,which)->{verticalPageSwipe=which==1;pageView.setVerticalPageSwipe(verticalPageSwipe);recentPrefs.edit().putBoolean("vertical_page_swipe",verticalPageSwipe).apply();dialog.dismiss();toast(verticalPageSwipe?"위로 밀면 다음 페이지로 이동합니다":"왼쪽으로 밀면 다음 페이지로 이동합니다");}).setNegativeButton("취소",null).show();}
    private void showMarkList(){List<AnnotationStore.Mark> items=new ArrayList<>(store.marks);if(items.isEmpty()){toast("저장된 하이라이트나 메모가 없습니다");return;}String[] labels=new String[items.size()];for(int i=0;i<items.size();i++){AnnotationStore.Mark mark=items.get(i);String note=mark.note;String state=note==null||note.isEmpty()?"":(!mark.visible?"[숨김] ":(mark.minimized?"[최소화] ":"[펼침] "));labels[i]="p."+(mark.page+1)+"  "+state+(note==null||note.isEmpty()?"(메모 없음)":note);}new AlertDialog.Builder(this).setTitle("메모·하이라이트").setItems(labels,(d,i)->{showPage(items.get(i).page);editMark(items.get(i));}).show();}
    private void showBookmarks(){if(store.bookmarks.isEmpty()){toast("즐겨찾기한 페이지가 없습니다");return;}List<Integer> pages=new ArrayList<>(store.bookmarks);Collections.sort(pages);String[] labels=new String[pages.size()];for(int i=0;i<pages.size();i++)labels[i]="페이지 "+(pages.get(i)+1);new AlertDialog.Builder(this).setTitle("즐겨찾기").setItems(labels,(d,i)->showPage(pages.get(i))).show();}
    private void goToPage(){if(renderer==null)return;EditText input=new EditText(this);input.setInputType(2);input.setHint("1 ~ "+renderer.getPageCount());new AlertDialog.Builder(this).setTitle("페이지로 이동").setView(input).setPositiveButton("이동",(d,w)->{try{showPage(Integer.parseInt(input.getText().toString())-1);}catch(Exception ignored){toast("올바른 페이지를 입력하세요");}}).setNegativeButton("취소",null).show();}
    private void exportAnnotations(){if(documentUri==null)return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,documentTitle.replaceAll("(?i)\\.pdf$","")+"_annotations.json");startActivityForResult(i,EXPORT_JSON);}
    private void showHelp(){new AlertDialog.Builder(this).setTitle("PDF Note 사용법").setMessage("• 텍스트 선택: 단어에서 바로 드래그하거나 잠시 누른 뒤 문장 끝까지 계속 드래그\n• 선택 팝업: 하이라이트·복사·번역·읽어주기·단어장 찾기·개요 추가·메모\n• 단어장 연결: ‘단어장 찾기’ 후 사전앱의 ‘PDF로 돌아가기’ 버튼\n• 포스트잇: 메모와 번역을 펼치기·최소화·숨기기로 관리\n• 문서 추가: 상단 폴더 또는 탭의 + 버튼\n• HWP·DOC: 본문 글자만 추출하며 표·그림·원본 배치는 보존되지 않습니다\n• 문서 전환·닫기: 상단 문서 탭과 × 버튼\n• 앱 재실행: 열었던 탭과 마지막 페이지 자동 복원\n• S펜 필기: 아래쪽 펜 아이콘\n• 페이지 썸네일: 상단 페이지 목록 아이콘\n• 개요 저장: 선택 팝업 또는 메뉴의 ‘개요 지점 추가’\n• 페이지 넘김: 좌우 또는 위아래로 밀면 짧은 전환 효과가 표시됩니다\n• 확대: 두 손가락으로 핀치\n• 확대 화면 이동: 한 손가락으로 상하좌우 드래그\n• 전체 화면: 위쪽 확장 아이콘\n• 즐겨찾기: 별 아이콘\n\n필기·번역·개요·하이라이트·메모·즐겨찾기는 문서별로 저장되며 원본 PDF는 변경하지 않습니다. Translate 앱을 사용하면 선택한 문장이 해당 앱으로 전달됩니다. 기기 내 번역은 최초 모델 다운로드가 필요합니다.").setPositiveButton("확인",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void closeAllDocuments(){for(DocumentSession s:new ArrayList<>(sessions)){if(s.renderer!=null)s.renderer.close();if(s.descriptor!=null)try{s.descriptor.close();}catch(IOException ignored){}if(s.officePreview!=null)s.officePreview.delete();}sessions.clear();renderer=null;descriptor=null;}
    @Override protected void onStop(){onSelectionAdjustStarted();saveSessionState();super.onStop();}
    @Override protected void onDestroy(){saveSessionState();++ocrGeneration;if(speech!=null){speech.stop();speech.shutdown();speech=null;}if(latinRecognizer!=null)latinRecognizer.close();if(koreanRecognizer!=null)koreanRecognizer.close();closeAllDocuments();super.onDestroy();}
}
