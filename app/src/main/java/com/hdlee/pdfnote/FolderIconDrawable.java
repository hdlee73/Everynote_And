package com.hdlee.pdfnote;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Colored folder with a rounded tab, paper insert and a softly shaded front. */
final class FolderIconDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color,size;
    private int opacity=255;
    FolderIconDrawable(int color,int size){this.color=color;this.size=size;}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();if(bounds.isEmpty())return;
        canvas.save();float scale=Math.min(bounds.width()/64f,bounds.height()/64f);canvas.translate(bounds.centerX()-32*scale,bounds.centerY()-32*scale);canvas.scale(scale,scale);
        paint.setAlpha(opacity);paint.setShader(null);paint.setColor(0x140F172A);paint.setAlpha(opacity*20/255);canvas.drawRoundRect(new RectF(5,46,59,56),6,6,paint);
        paint.setColor(shade(color,.84f));paint.setAlpha(opacity);
        Path back=new Path();back.moveTo(8,14);back.quadTo(8,10,12,10);back.lineTo(25,10);back.quadTo(28,10,30,14);back.lineTo(32,17);back.lineTo(52,17);back.quadTo(57,17,57,22);back.lineTo(57,49);back.quadTo(57,53,53,53);back.lineTo(12,53);back.quadTo(8,53,8,49);back.close();canvas.drawPath(back,paint);
        paint.setColor(0xFFEFF5FC);paint.setAlpha(opacity);canvas.drawRoundRect(new RectF(13,20,52,42),3,3,paint);
        paint.setShader(new LinearGradient(0,27,0,54,light(color,.16f),color,Shader.TileMode.CLAMP));paint.setAlpha(opacity);
        Path front=new Path();front.moveTo(9,27);front.lineTo(55,27);front.quadTo(60,27,59,32);front.lineTo(56,50);front.quadTo(55,54,51,54);front.lineTo(12,54);front.quadTo(8,54,7,50);front.lineTo(4,32);front.quadTo(3,27,9,27);front.close();canvas.drawPath(front,paint);
        paint.setShader(null);paint.setColor(Color.WHITE);paint.setAlpha(opacity*65/255);canvas.drawRoundRect(new RectF(10,29,54,31),1,1,paint);
        canvas.restore();
    }
    private static int shade(int c,float factor){return Color.rgb(Math.round(Color.red(c)*factor),Math.round(Color.green(c)*factor),Math.round(Color.blue(c)*factor));}
    private static int light(int c,float fraction){return Color.rgb(Math.round(Color.red(c)+(255-Color.red(c))*fraction),Math.round(Color.green(c)+(255-Color.green(c))*fraction),Math.round(Color.blue(c)+(255-Color.blue(c))*fraction));}
    @Override public void setAlpha(int alpha){opacity=Math.max(0,Math.min(255,alpha));invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
