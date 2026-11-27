package com.eagleport.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

final class Glass {
    static int blend(int a,int b,float t) {
        return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),(int)(Color.green(a)*(1-t)+Color.green(b)*t),(int)(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    static int alpha(int c,int a) { return (c&0xffffff)|(a<<24); }
    static GradientDrawable panel(int base,int accent,float radius,boolean lite) {
        boolean ocean=base==0xff041021;
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{alpha(ocean?0xff12679c:blend(base,accent,.25f),lite?255:188),alpha(ocean?0xff07406e:blend(base,accent,.12f),lite?255:200)});
        d.setCornerRadius(radius); d.setStroke(1,alpha(ocean?0xff82c6ff:accent,153)); return d;
    }
    static final class Wall extends Drawable {
        final int base,accent; final Bitmap image; final boolean fit; final int tint,opacity; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Wall(int b,int a,Bitmap i,boolean f,int t,int o){base=b;accent=a;image=i;fit=f;tint=t;opacity=Math.max(0,Math.min(100,o));}
        @Override public void draw(Canvas c) {
            Rect r=getBounds(); float w=r.width(),h=r.height(); if(w<=0||h<=0)return;
            c.save(); c.translate(r.left,r.top); c.clipRect(0,0,w,h);
            p.setShader(null); p.setColor(base); c.drawRect(0,0,w,h,p);

                boolean ocean=base==0xff041021;
                p.setShader(new LinearGradient(0,0,w,h,ocean?new int[]{0xff041021,0xff07274e,0xff0d5ab0}:new int[]{base,blend(base,accent,.18f),blend(base,accent,.38f)},new float[]{0f,.58f,1f},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
                Path wave=new Path();wave.moveTo(-w*.2f,h*.66f);wave.cubicTo(w*.1f,h*.08f,w*.5f,h*.64f,w*1.15f,h*.24f);wave.lineTo(w*1.15f,h);wave.lineTo(0,h);wave.close();
                p.setShader(new LinearGradient(0,h*.3f,w,h,alpha(ocean?0xff004b92:blend(base,accent,.48f),130),alpha(base,45),Shader.TileMode.CLAMP));c.drawPath(wave,p);p.setShader(null);
                p.setColor(alpha(accent,90));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);Path line=new Path();line.moveTo(-w*.2f,h*.66f);line.cubicTo(w*.1f,h*.08f,w*.5f,h*.64f,w*1.15f,h*.24f);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
            
            if(image!=null&&!image.isRecycled()) {
                float s=fit?Math.min(w/image.getWidth(),h/image.getHeight()):Math.max(w/image.getWidth(),h/image.getHeight());
                float x=(w-image.getWidth()*s)/2,y=(h-image.getHeight()*s)/2;
                p.setColor(Color.WHITE);p.setAlpha(Math.round(opacity*2.55f)); c.drawBitmap(image,null,new RectF(x,y,x+image.getWidth()*s,y+image.getHeight()*s),p);p.setAlpha(255);
                if(tint>0){p.setColor(alpha(accent,tint));c.drawRect(0,0,w,h,p);}
            }
            c.restore();
        }
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
