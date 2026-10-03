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
        Spinner papers=new Spinner(context);papers.setContentDescription("종이 형식");papers.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,NotebookFiles.PAPER_NAMES));addView(papers,new LayoutParams(-1,dp(48)));
        templateButton=new TextView(context);templateButton.setText("PDF·이미지 서식 고르기");templateButton.setTextSize(14);templateButton.setGravity(Gravity.CENTER);templateButton.setTextColor(0xFF007AFF);templateButton.setBackground(chipBackground());templateButton.setVisibility(GONE);templateButton.setOnClickListener(v->{if(templateRequest!=null)templateRequest.run();});LayoutParams tp=new LayoutParams(-1,dp(44));tp.topMargin=dp(6);addView(templateButton,tp);
        preview=new Preview(context);addView(preview,new LayoutParams(-1,dp(158)));papers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View view,int position,long id){kind=position;templateButton.setVisibility(kind==NotebookFiles.CUSTOM?VISIBLE:GONE);preview.invalidate();}public void onNothingSelected(AdapterView<?> p){}});
        HorizontalScrollView scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(false);colors=new LinearLayout(context);colors.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(colors,new HorizontalScrollView.LayoutParams(-2,dp(58)));addView(scroll,new LayoutParams(-1,dp(58)));refreshColors();
    }
    NotebookFiles.Paper paper(){return new NotebookFiles.Paper(kind,color,template);}
    /** Called when the user taps "PDF·이미지 서식 고르기"; the host opens a file picker and answers with {@link #setTemplate}. */
    void onTemplateRequest(Runnable request){templateRequest=request;}
    void setTemplate(java.io.File file){template=file;templateButton.setText(file==null?"PDF·이미지 서식 고르기":"서식: "+file.getName()+" · 다시 고르기");preview.invalidate();}
    private GradientDrawable chipBackground(){GradientDrawable g=new GradientDrawable();g.setColor(0xFFEAF2FF);g.setCornerRadius(dp(12));return g;}
    private void refreshColors(){colors.removeAllViews();for(int i=0;i<NotebookFiles.COLORS.length;i++){final int selected=NotebookFiles.COLORS[i];TextView chip=new TextView(getContext());chip.setText(color==selected?Glyph.check(getContext(),0xFF007AFF,""):"");chip.setTextColor(0xFF007AFF);chip.setTextSize(20);chip.setGravity(Gravity.CENTER);chip.setContentDescription("배경색 "+NotebookFiles.COLOR_NAMES[i]);chip.setTag("paper_color:"+i);GradientDrawable bg=new GradientDrawable();bg.setColor(selected);bg.setCornerRadius(dp(20));bg.setStroke(dp(color==selected?2:1),color==selected?0xFF007AFF:0xFFCBD5E1);chip.setBackground(bg);LayoutParams lp=new LayoutParams(dp(40),dp(40));lp.setMargins(dp(3),0,dp(6),0);colors.addView(chip,lp);chip.setOnClickListener(v->{color=selected;refreshColors();preview.invalidate();});}}
    private final class Preview extends View {
        Preview(Context c){super(c);setContentDescription("선택한 종이 미리보기");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float h=getHeight()-dp(12),w=h*595/842;RectF rect=new RectF((getWidth()-w)/2,dp(6),(getWidth()+w)/2,dp(6)+h);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);canvas.drawRect(rect,p);p.setColor(0xFFBBC4CE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);canvas.drawRect(rect,p);if(kind==NotebookFiles.CUSTOM){p.setStyle(Paint.Style.FILL);p.setColor(0xFF8E8E93);p.setTextSize(dp(11));p.setTextAlign(Paint.Align.CENTER);canvas.drawText(template==null?"서식을 고르세요":template.getName(),rect.centerX(),rect.centerY(),p);return;}
            boolean dark=Color.red(color)+Color.green(color)+Color.blue(color)<300;
            for(float[] seg:NotebookFiles.layout(kind,595,842)){int[] c=NotebookFiles.ruleColor((int)seg[4]);p.setColor(dark?Color.rgb(Math.min(255,c[0]/2+90),Math.min(255,c[1]/2+90),Math.min(255,c[2]/2+90)):Color.rgb(c[0],c[1],c[2]));
                float x1=rect.left+seg[0]*w/595,y1=rect.bottom-seg[1]*h/842,x2=rect.left+seg[2]*w/595,y2=rect.bottom-seg[3]*h/842;
                if(seg[4]==3){p.setStyle(Paint.Style.FILL);canvas.drawCircle(x1,y1,Math.max(.6f,w/595*.9f),p);p.setStyle(Paint.Style.STROKE);}else canvas.drawLine(x1,y1,x2,y2,p);}}
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
