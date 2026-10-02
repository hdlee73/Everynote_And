package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class PaperChoiceView extends LinearLayout {
    private int kind,color=Color.WHITE;
    private final Preview preview;
    private final LinearLayout colors;
    PaperChoiceView(Context context){
        super(context);setOrientation(VERTICAL);setPadding(dp(18),dp(8),dp(18),dp(8));
        Spinner papers=new Spinner(context);papers.setContentDescription("종이 형식");papers.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,NotebookFiles.PAPER_NAMES));addView(papers,new LayoutParams(-1,dp(48)));
        preview=new Preview(context);addView(preview,new LayoutParams(-1,dp(158)));papers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View view,int position,long id){kind=position;preview.invalidate();}public void onNothingSelected(AdapterView<?> p){}});
        HorizontalScrollView scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(false);colors=new LinearLayout(context);colors.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(colors,new HorizontalScrollView.LayoutParams(-2,dp(58)));addView(scroll,new LayoutParams(-1,dp(58)));refreshColors();
    }
    NotebookFiles.Paper paper(){return new NotebookFiles.Paper(kind,color);}
    private void refreshColors(){colors.removeAllViews();for(int i=0;i<NotebookFiles.COLORS.length;i++){final int selected=NotebookFiles.COLORS[i];TextView chip=new TextView(getContext());chip.setText(color==selected?"✓":"");chip.setTextColor(0xFF007AFF);chip.setTextSize(20);chip.setGravity(Gravity.CENTER);chip.setContentDescription("배경색 "+NotebookFiles.COLOR_NAMES[i]);chip.setTag("paper_color:"+i);GradientDrawable bg=new GradientDrawable();bg.setColor(selected);bg.setCornerRadius(dp(20));bg.setStroke(dp(color==selected?2:1),color==selected?0xFF007AFF:0xFFCBD5E1);chip.setBackground(bg);LayoutParams lp=new LayoutParams(dp(40),dp(40));lp.setMargins(dp(3),0,dp(6),0);colors.addView(chip,lp);chip.setOnClickListener(v->{color=selected;refreshColors();preview.invalidate();});}}
    private final class Preview extends View {
        Preview(Context c){super(c);setContentDescription("선택한 종이 미리보기");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float h=getHeight()-dp(12),w=h*595/842;RectF rect=new RectF((getWidth()-w)/2,dp(6),(getWidth()+w)/2,dp(6)+h);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);canvas.drawRect(rect,p);p.setColor(0xFFBBC4CE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);canvas.drawRect(rect,p);float step=(kind==1?25:18)*h/842;if(kind!=0){for(float y=rect.top+54*h/842;y<rect.bottom-42*h/842;y+=step)canvas.drawLine(rect.left+36*w/595,y,rect.right-36*w/595,y,p);if(kind==2)for(float x=rect.left+36*w/595;x<rect.right-36*w/595;x+=step)canvas.drawLine(x,rect.top+54*h/842,x,rect.bottom-42*h/842,p);}}
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
