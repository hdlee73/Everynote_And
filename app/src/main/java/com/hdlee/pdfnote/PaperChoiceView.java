package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class PaperChoiceView extends LinearLayout {
    private int kind,color=Color.WHITE;
    private java.io.File template;private Runnable templateRequest;private final TextView templateButton;
    private final Preview preview;
    private final LinearLayout colors;
    PaperChoiceView(Context context){
        super(context);setOrientation(VERTICAL);setPadding(dp(18),dp(8),dp(18),dp(8));
        picker=new TextView(context);picker.setTag("paper_picker");picker.setContentDescription("종이 형식");picker.setTextSize(15);picker.setTextColor(0xFF1C1C1E);picker.setGravity(Gravity.CENTER_VERTICAL);picker.setPadding(dp(16),0,dp(14),0);
        GradientDrawable pickerBg=new GradientDrawable();pickerBg.setColor(0xFFF2F2F7);pickerBg.setCornerRadius(dp(14));picker.setBackground(pickerBg);picker.setCompoundDrawablePadding(dp(10));
        picker.setOnClickListener(v->{java.util.List<AnchoredMenu.Row> rows=new java.util.ArrayList<>();for(int i=0;i<NotebookFiles.PAPER_ORDER.length;i++){final int position=i;int k=NotebookFiles.PAPER_ORDER[i];rows.add(new AnchoredMenu.Row(NotebookFiles.PAPER_NAMES[k],k>=10?R.drawable.ic_page:k==NotebookFiles.CUSTOM?R.drawable.ic_import:R.drawable.ic_note_add,()->selectPosition(position)).tint(k>=10?0xFF007AFF:k==NotebookFiles.CUSTOM?0xFF34C759:0xFF8E8E93).selected(position==selected));}AnchoredMenu.show(getContext(),picker,false,rows,null);});
        addView(picker,new LayoutParams(-1,dp(48)));
        templateButton=new TextView(context);templateButton.setText("PDF·이미지 서식 고르기");templateButton.setTextSize(14);templateButton.setGravity(Gravity.CENTER);templateButton.setTextColor(0xFF007AFF);templateButton.setBackground(chipBackground());templateButton.setVisibility(GONE);templateButton.setOnClickListener(v->{if(templateRequest!=null)templateRequest.run();});LayoutParams tp=new LayoutParams(-1,dp(44));tp.topMargin=dp(6);addView(templateButton,tp);
        preview=new Preview(context);addView(preview,new LayoutParams(-1,dp(158)));selectPosition(0);
        HorizontalScrollView scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(false);colors=new LinearLayout(context);colors.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(colors,new HorizontalScrollView.LayoutParams(-2,dp(58)));addView(scroll,new LayoutParams(-1,dp(58)));refreshColors();
    }
    private final TextView picker;private int selected=-1;
    /** Chooses a paper by its row in the list (the bundled form templates come first). */
    void selectPosition(int position){selected=position;kind=NotebookFiles.PAPER_ORDER[position];builtin=kind>=10?builtinFile(kind):null;picker.setText(NotebookFiles.PAPER_NAMES[kind]+"  ▾");templateButton.setVisibility(kind==NotebookFiles.CUSTOM?VISIBLE:GONE);if(preview!=null)preview.invalidate();}
    /** Chooses a paper by its kind (see {@link NotebookFiles#PAPER_NAMES}). */
    void selectKind(int paperKind){for(int i=0;i<NotebookFiles.PAPER_ORDER.length;i++)if(NotebookFiles.PAPER_ORDER[i]==paperKind){selectPosition(i);return;}}
    private java.io.File builtin;private final java.util.Map<String,Bitmap> previews=new java.util.HashMap<>();
    /** Copies a bundled form PDF out of the assets once so it can be used like a user-chosen template. */
    private java.io.File builtinFile(int k){
        try{java.io.File dir=new java.io.File(getContext().getFilesDir(),"templates");dir.mkdirs();String name=NotebookFiles.BUILTIN_TEMPLATES[k-10];java.io.File out=new java.io.File(dir,"builtin-"+name);
            if(!out.isFile()||out.length()==0){try(java.io.InputStream in=getContext().getAssets().open("templates/"+name);java.io.OutputStream o=new java.io.FileOutputStream(out)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}}
            return out;}catch(java.io.IOException e){return null;}
    }
    private Bitmap pdfPreview(java.io.File file){
        if(file==null||!file.isFile())return null;Bitmap cached=previews.get(file.getPath());if(cached!=null)return cached;
        try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer r=new android.graphics.pdf.PdfRenderer(fd)){
            if(r.getPageCount()<1)return null;try(android.graphics.pdf.PdfRenderer.Page page=r.openPage(0)){Bitmap bmp=Bitmap.createBitmap(300,Math.max(1,300*page.getHeight()/page.getWidth()),Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);page.render(bmp,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);previews.put(file.getPath(),bmp);return bmp;}
        }catch(Exception|Error e){return null;}
    }
    NotebookFiles.Paper paper(){if(kind>=10){if(builtin==null)builtin=builtinFile(kind);return new NotebookFiles.Paper(NotebookFiles.CUSTOM,Color.WHITE,builtin);}return new NotebookFiles.Paper(kind,color,template);}
    /** Called when the user taps "PDF·이미지 서식 고르기"; the host opens a file picker and answers with {@link #setTemplate}. */
    void onTemplateRequest(Runnable request){templateRequest=request;}
    void setTemplate(java.io.File file){template=file;templateButton.setText(file==null?"PDF·이미지 서식 고르기":"서식: "+file.getName()+" · 다시 고르기");preview.invalidate();}
    private GradientDrawable chipBackground(){GradientDrawable g=new GradientDrawable();g.setColor(0xFFEAF2FF);g.setCornerRadius(dp(12));return g;}
    private void refreshColors(){colors.removeAllViews();for(int i=0;i<NotebookFiles.COLORS.length;i++){final int selected=NotebookFiles.COLORS[i];TextView chip=new TextView(getContext());chip.setText(color==selected?Glyph.check(getContext(),0xFF007AFF,""):"");chip.setTextColor(0xFF007AFF);chip.setTextSize(20);chip.setGravity(Gravity.CENTER);chip.setContentDescription("배경색 "+NotebookFiles.COLOR_NAMES[i]);chip.setTag("paper_color:"+i);GradientDrawable bg=new GradientDrawable();bg.setColor(selected);bg.setCornerRadius(dp(20));bg.setStroke(dp(color==selected?2:1),color==selected?0xFF007AFF:0xFFCBD5E1);chip.setBackground(bg);LayoutParams lp=new LayoutParams(dp(40),dp(40));lp.setMargins(dp(3),0,dp(6),0);colors.addView(chip,lp);chip.setOnClickListener(v->{color=selected;refreshColors();preview.invalidate();});}}
    private final class Preview extends View {
        Preview(Context c){super(c);setContentDescription("선택한 종이 미리보기");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float h=getHeight()-dp(12),w=h*595/842;RectF rect=new RectF((getWidth()-w)/2,dp(6),(getWidth()+w)/2,dp(6)+h);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);canvas.drawRect(rect,p);
            Bitmap form=kind>=10?pdfPreview(builtin):kind==NotebookFiles.CUSTOM?pdfPreview(template):null;if(form!=null){canvas.drawBitmap(form,null,rect,new Paint(Paint.FILTER_BITMAP_FLAG));p.setColor(0xFFBBC4CE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));canvas.drawRect(rect,p);return;}p.setColor(0xFFBBC4CE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);canvas.drawRect(rect,p);if(kind==NotebookFiles.CUSTOM){p.setStyle(Paint.Style.FILL);p.setColor(0xFF8E8E93);p.setTextSize(dp(11));p.setTextAlign(Paint.Align.CENTER);canvas.drawText(template==null?"서식을 고르세요":template.getName(),rect.centerX(),rect.centerY(),p);return;}
            boolean dark=Color.red(color)+Color.green(color)+Color.blue(color)<300;
            for(float[] seg:NotebookFiles.layout(kind,595,842)){int[] c=NotebookFiles.ruleColor((int)seg[4]);p.setColor(dark?Color.rgb(Math.min(255,c[0]/2+90),Math.min(255,c[1]/2+90),Math.min(255,c[2]/2+90)):Color.rgb(c[0],c[1],c[2]));
                float x1=rect.left+seg[0]*w/595,y1=rect.bottom-seg[1]*h/842,x2=rect.left+seg[2]*w/595,y2=rect.bottom-seg[3]*h/842;
                if(seg[4]==3){p.setStyle(Paint.Style.FILL);canvas.drawCircle(x1,y1,Math.max(.6f,w/595*.9f),p);p.setStyle(Paint.Style.STROKE);}else canvas.drawLine(x1,y1,x2,y2,p);}}
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
