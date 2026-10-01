package com.hdlee.pdfnote;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Document shelf: folder tree, breadcrumbs and covers with a compact action menu. */
final class LibraryDialog extends Dialog {
    interface Actions {
        void open(File file);
        void importFiles(File folder);
        void newNote(File folder,Runnable refresh);
        void changed(File source,File target,boolean moved);
        void selectedFolder(File folder);
    }
    private final Activity activity;
    private final LibraryRepository repository;
    private final Actions actions;
    private File folder;
    private FolderTreeView tree;
    private LinearLayout content,body,grid,breadcrumb;
    private ScrollView shelf;
    private TextView heading;
    private final ExecutorService previews=Executors.newSingleThreadExecutor();
    private int generation;
    private boolean busy;
    LibraryDialog(Activity activity,LibraryRepository repository,File folder,Actions actions){
        super(activity);this.activity=activity;this.repository=repository;this.folder=folder;this.actions=actions;requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=new LinearLayout(activity);content.setTag("library_root");content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(0xFFF8FAFC);content.setFitsSystemWindows(true);
        LinearLayout toolbar=new LinearLayout(activity);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(12),dp(6),dp(8),dp(6));toolbar.setBackgroundColor(Color.WHITE);
        TextView close=button("‹","문서함 닫기",v->dismiss());close.setTextSize(32);toolbar.addView(close,new LinearLayout.LayoutParams(dp(40),dp(48)));
        TextView title=label("문서함",22,0xFF173B63);title.setTypeface(null,android.graphics.Typeface.BOLD);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView add=button("＋ 새로 만들기","새로 만들기",this::newMenu);add.setTextSize(14);toolbar.addView(add,new LinearLayout.LayoutParams(dp(126),dp(48)));content.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(64)));
        LinearLayout navigation=new LinearLayout(activity);navigation.setGravity(Gravity.CENTER_VERTICAL);navigation.setPadding(dp(8),0,dp(8),0);navigation.setBackgroundColor(Color.WHITE);
        TextView folders=button("☰ 폴더","폴더 트리 보기",v->{tree.setVisibility(tree.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);shelf.post(this::refreshGrid);});navigation.addView(folders,new LinearLayout.LayoutParams(dp(78),dp(44)));
        HorizontalScrollView trail=new HorizontalScrollView(activity);trail.setHorizontalScrollBarEnabled(false);breadcrumb=new LinearLayout(activity);breadcrumb.setGravity(Gravity.CENTER_VERTICAL);trail.addView(breadcrumb,new HorizontalScrollView.LayoutParams(-2,-1));navigation.addView(trail,new LinearLayout.LayoutParams(0,dp(44),1));content.addView(navigation,new LinearLayout.LayoutParams(-1,dp(44)));
        body=new LinearLayout(activity);tree=new FolderTreeView(activity,repository,folder,this::selectFolder);tree.setVisibility(activity.getResources().getConfiguration().screenWidthDp>=600?View.VISIBLE:View.GONE);body.addView(tree,new LinearLayout.LayoutParams(dp(178),-1));
        shelf=new ScrollView(activity);grid=new LinearLayout(activity);grid.setOrientation(LinearLayout.VERTICAL);grid.setPadding(dp(12),dp(10),dp(12),dp(24));shelf.addView(grid,new ScrollView.LayoutParams(-1,-2));body.addView(shelf,new LinearLayout.LayoutParams(0,-1,1));content.addView(body,new LinearLayout.LayoutParams(-1,0,1));setContentView(content);
        setOnDismissListener(d->{generation++;previews.shutdownNow();});shelf.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)shelf.post(this::refreshGrid);});refresh();
    }
    @Override public void show(){super.show();getWindow().setBackgroundDrawableResource(android.R.color.transparent);getWindow().setLayout(-1,-1);getWindow().setStatusBarColor(0xFF173B63);getWindow().setNavigationBarColor(Color.WHITE);content.post(this::refreshGrid);}
    private void selectFolder(File selected){folder=selected;actions.selectedFolder(folder);refresh();}
    private void refresh(){tree.select(folder);breadcrumb.removeAllViews();List<File> path=new ArrayList<>();for(File p=folder;p!=null;p=p.getParentFile()){path.add(p);if(p.equals(repository.root))break;}Collections.reverse(path);for(File p:path){if(breadcrumb.getChildCount()>0)breadcrumb.addView(label(" › ",14,0xFF94A3B8));TextView item=button(p.equals(repository.root)?"문서함":p.getName(),"폴더로 이동 "+p.getName(),v->selectFolder(p));item.setTextSize(13);item.setPadding(dp(5),0,dp(5),0);breadcrumb.addView(item,new LinearLayout.LayoutParams(-2,dp(44)));}refreshGrid();}
    private void newMenu(View anchor){PopupMenu menu=new PopupMenu(activity,anchor);menu.getMenu().add("새 노트");menu.getMenu().add("폴더 만들기");menu.getMenu().add("파일 가져오기");menu.setOnMenuItemClickListener(item->{String title=item.getTitle().toString();if(title.equals("새 노트"))actions.newNote(folder,this::refresh);else if(title.equals("폴더 만들기"))createFolder(folder,()->{tree.reload();refresh();});else{actions.selectedFolder(folder);actions.importFiles(folder);dismiss();}return true;});menu.show();}
    private void refreshGrid(){
        if(previews.isShutdown())return;final int version=++generation;grid.removeAllViews();List<File> items=repository.list(folder);heading=label(folder.equals(repository.root)?"내 문서":folder.getName(),18,0xFF334155);heading.setTypeface(null,android.graphics.Typeface.BOLD);heading.setPadding(dp(3),dp(6),0,dp(14));grid.addView(heading);
        int columns=Math.max(1,Math.min(4,(Math.max(shelf.getWidth(),dp(160))-dp(24))/dp(150)));
        if(items.isEmpty()){TextView empty=label("아직 문서가 없습니다",15,0xFF64748B);empty.setGravity(Gravity.CENTER);grid.addView(empty,new LinearLayout.LayoutParams(-1,dp(100)));TextView add=button("＋ 새 노트","빈 폴더에 새 노트 만들기",v->actions.newNote(folder,this::refresh));grid.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));return;}
        for(int i=0;i<items.size();i+=columns){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.TOP);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));for(int j=0;j<columns;j++){int index=i+j;LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-2,1);layout.setMargins(dp(5),dp(4),dp(5),dp(14));if(index<items.size())row.addView(card(items.get(index),version),layout);else row.addView(new View(activity),layout);}}
    }
    private View card(File file,int version){
        LinearLayout card=new LinearLayout(activity);card.setTag("document:"+file.getName());card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(9),dp(10),dp(9),dp(7));card.setBackground(round(Color.WHITE,12));card.setElevation(dp(2));
        ImageView cover=new ImageView(activity);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setBackground(round(file.isDirectory()?0xFFE4EDFA:0xFFF0F2F5,6));card.addView(cover,new LinearLayout.LayoutParams(-1,dp(132)));cover.setContentDescription(file.getName()+" 미리보기");
        if(file.isDirectory()){cover.setImageResource(R.drawable.ic_folder_open);cover.setPadding(dp(32),dp(32),dp(32),dp(32));}else{cover.setImageResource(R.drawable.ic_note_add);preview(file,cover,version);}
        LinearLayout nameRow=new LinearLayout(activity);nameRow.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(file.getName().replaceFirst("(?i)\\.pdf$",""),14,0xFF1E293B);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(0,dp(6),0,0);nameRow.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));
        if(!file.isDirectory()){TextView more=button("⋮",file.getName()+" 메뉴",v->documentMenu(file,v));more.setTextSize(25);nameRow.addView(more,new LinearLayout.LayoutParams(dp(32),dp(46)));}card.addView(nameRow);
        TextView type=label(file.isDirectory()?"폴더":repository.paper(file)!=null?"노트":"PDF",11,0xFF94A3B8);card.addView(type,new LinearLayout.LayoutParams(-1,dp(22)));
        View.OnClickListener open=v->{if(file.isDirectory())selectFolder(file);else{dismiss();actions.open(file);}};cover.setOnClickListener(open);name.setOnClickListener(open);card.setOnClickListener(open);if(!file.isDirectory())card.setOnLongClickListener(v->{documentMenu(file,v);return true;});return card;
    }
    private void preview(File file,ImageView cover,int version){
        previews.execute(()->{if(version!=generation)return;try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor);PdfRenderer.Page page=renderer.openPage(0)){
            float scale=240f/page.getWidth();Bitmap bitmap=Bitmap.createBitmap(240,Math.max(1,Math.round(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.postScale(scale,scale);page.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);AnnotationStore store=new AnnotationStore(activity);store.open(Uri.fromFile(file));AnnotationPainter.all(activity,new Canvas(bitmap),new RectF(0,0,bitmap.getWidth(),bitmap.getHeight()),store,0);
            activity.runOnUiThread(()->{if(isShowing()&&version==generation)cover.setImageBitmap(bitmap);});
        }catch(Exception ignored){}});
    }
    private void documentMenu(File file,View anchor){PopupMenu menu=new PopupMenu(activity,anchor);for(String name:new String[]{"이름 변경","복사","이동"})menu.getMenu().add(name);menu.setOnMenuItemClickListener(item->{if(item.getTitle().equals("이름 변경"))rename(file);else chooseDestination(file,item.getTitle().equals("이동"));return true;});menu.show();}
    private void rename(File file){EditText input=new EditText(activity);input.setSingleLine();input.setText(file.getName().replaceFirst("(?i)\\.pdf$",""));input.selectAll();AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("이름 변경").setView(input).setPositiveButton("저장",null).setNegativeButton("취소",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{String name=NotebookFiles.pdfName(input.getText().toString());if(!name.equals(file.getName())&&new File(file.getParentFile(),name).exists())throw new java.io.IOException("같은 이름의 PDF가 있습니다");dialog.dismiss();transfer(file,file.getParentFile(),name,true);}catch(Exception error){input.setError(error.getMessage());}}));dialog.show();}
    private void chooseDestination(File file,boolean move){
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);FolderTreeView picker=new FolderTreeView(activity,repository,folder,f->{});panel.addView(picker,new LinearLayout.LayoutParams(-1,dp(340)));TextView add=button("＋ 폴더 만들기","대상 폴더 만들기",v->createFolder(picker.selected(),picker::reload));panel.addView(add,new LinearLayout.LayoutParams(-1,dp(44)));
        new AlertDialog.Builder(activity).setTitle(move?"이동할 폴더":"복사할 폴더").setView(panel).setPositiveButton(move?"이곳으로 이동":"이곳에 복사",(d,w)->transfer(file,picker.selected(),file.getName(),move)).setNegativeButton("취소",null).show();
    }
    private void transfer(File file,File target,String name,boolean move){if(busy)return;busy=true;ProgressDialog progress=ProgressDialog.show(activity,"",move?"문서를 이동하는 중…":"문서를 복사하는 중…",true,false);new Thread(()->{try{File saved=repository.transfer(file,target,name,move);activity.runOnUiThread(()->{busy=false;progress.dismiss();actions.changed(file,saved,move);refresh();Toast.makeText(activity,move?"저장했습니다":"복사했습니다",Toast.LENGTH_SHORT).show();});}catch(Exception error){activity.runOnUiThread(()->{busy=false;progress.dismiss();Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();});}},"library-transfer").start();}
    private void createFolder(File parent,Runnable done){EditText input=new EditText(activity);input.setHint("폴더 이름");input.setSingleLine();AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("폴더 만들기").setView(input).setPositiveButton("만들기",null).setNegativeButton("취소",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{repository.createFolder(parent,input.getText().toString());dialog.dismiss();done.run();}catch(Exception error){input.setError(error.getMessage());}}));dialog.show();}
    private TextView button(String text,String description,View.OnClickListener click){TextView button=label(text,15,0xFF2563EB);button.setGravity(Gravity.CENTER);button.setContentDescription(description);button.setOnClickListener(click);button.setBackgroundResource(android.R.drawable.list_selector_background);return button;}
    private TextView label(String text,int size,int color){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private GradientDrawable round(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
