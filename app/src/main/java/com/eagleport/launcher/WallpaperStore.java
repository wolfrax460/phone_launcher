package com.eagleport.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class WallpaperStore {
    private final Context context;
    private final AtomicFile file;
    WallpaperStore(Context c) { context=c; file=new AtomicFile(new File(c.getFilesDir(),"wallpaper-v1.webp")); }
    Bitmap load() {
        try(InputStream s=file.openRead()) { return BitmapFactory.decodeStream(s); }
        catch(IOException|RuntimeException e) { return null; }
    }
    void importImage(Uri uri) throws IOException {
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
        try(InputStream s=context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(s,null,o); }
        if(o.outWidth<=0||o.outHeight<=0) throw new IOException("Not a supported image");
        int sample=1;
        while(Math.max(o.outWidth/sample,o.outHeight/sample)>1920) sample*=2;
        o.inSampleSize=sample; o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap original;
        try(InputStream s=context.getContentResolver().openInputStream(uri)) { original=BitmapFactory.decodeStream(s,null,o); }
        if(original==null) throw new IOException("Image could not be decoded");
        float scale=Math.min(1f,Math.min(960f/original.getWidth(),960f/original.getHeight()));
        Bitmap resized=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),Math.max(1,Math.round(original.getHeight()*scale)),true);
        FileOutputStream out=null;
        try {
            out=file.startWrite();
            if(!resized.compress(Bitmap.CompressFormat.WEBP,85,out)) throw new IOException("Image could not be saved");
            file.finishWrite(out);
        } catch(IOException|RuntimeException e) { if(out!=null) file.failWrite(out); throw e; }
        finally { if(resized!=original) resized.recycle(); original.recycle(); }
    }
}
