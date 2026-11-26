package com.eagleport.launcher;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.util.*;

final class AppCatalog {
    static final class Entry {
        final String id,label; final ResolveInfo info;
        Entry(String i,String l,ResolveInfo r){id=i;label=l;info=r;}
    }
    private final PackageManager pm;
    private final String ownPackage;
    private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(2048){
        @Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}
    };
    AppCatalog(Context c){pm=c.getPackageManager();ownPackage=c.getPackageName();}
    List<Entry> scan(){
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<Entry> result=new ArrayList<>();
        for(ResolveInfo r:pm.queryIntentActivities(query,0)){
            if(r.activityInfo==null||ownPackage.equals(r.activityInfo.packageName))continue;
            ComponentName cn=new ComponentName(r.activityInfo.packageName,r.activityInfo.name);
            result.add(new Entry(cn.flattenToString(),r.loadLabel(pm).toString(),r));
        }
        result.sort(Comparator.comparing(x->x.label,String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    Bitmap icon(Entry e){
        Bitmap existing=icons.get(e.id); if(existing!=null)return existing;
        Bitmap b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);
        try{Drawable d=e.info.loadIcon(pm);d.setBounds(0,0,80,80);d.draw(new Canvas(b));}
        catch(RuntimeException ignored){}
        icons.put(e.id,b);return b;
    }
    void clear(){icons.evictAll();}
}
