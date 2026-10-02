package com.hdlee.pdfnote;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Large pastel folder (tab, peeking pages, translucent front) used for folder cards on the shelf. */
final class FolderShapeDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    FolderShapeDrawable(int color){this.color=color;}
    private static int mix(int c,float toWhite){return Color.rgb(Math.round(Color.red(c)+(255-Color.red(c))*toWhite),Math.round(Color.green(c)+(255-Color.green(c))*toWhite),Math.round(Color.blue(c)+(255-Color.blue(c))*toWhite));}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();if(b.isEmpty())return;float w=b.width(),h=b.height(),r=w*.09f;canvas.save();canvas.translate(b.left,b.top);
        paint.setShader(null);paint.setColor(mix(color,.5f));
        canvas.drawRoundRect(new RectF(0,h*.1f,w,h),r,r,paint);canvas.drawRoundRect(new RectF(0,0,w*.42f,h*.22f),r*.8f,r*.8f,paint);
        paint.setColor(0xFFFFFFFF);canvas.drawRoundRect(new RectF(w*.12f,h*.2f,w*.88f,h*.6f),r*.6f,r*.6f,paint);
        paint.setColor(0xFFF2F2F7);canvas.drawRoundRect(new RectF(w*.18f,h*.15f,w*.82f,h*.5f),r*.6f,r*.6f,paint);
        paint.setColor(0xFFFFFFFF);canvas.drawRoundRect(new RectF(w*.12f,h*.22f,w*.88f,h*.6f),r*.6f,r*.6f,paint);
        paint.setShader(new LinearGradient(0,h*.3f,0,h,mix(color,.8f),mix(color,.6f),Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0,h*.3f,w,h),r,r,paint);paint.setShader(null);
        canvas.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
