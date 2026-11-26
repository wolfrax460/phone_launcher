package com.eagleport.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

final class Prefs {
    // Keep this preference file and application ID stable across releases.
    private final SharedPreferences p;
    Prefs(Context c) {
        p = c.getSharedPreferences("eagleport_settings", Context.MODE_PRIVATE);
        if (p.getInt("schema", 0) < 1) p.edit().putInt("schema", 1).apply();
    }
    String str(String k, String d) { return p.getString(k,d); }
    boolean bool(String k, boolean d) { return p.getBoolean(k,d); }
    int num(String k, int d) { return p.getInt(k,d); }
    void put(String k, String v) { p.edit().putString(k,v).apply(); }
    void put(String k, boolean v) { p.edit().putBoolean(k,v).apply(); }
    void put(String k, int v) { p.edit().putInt(k,v).apply(); }
    boolean has(String k) { return p.contains(k); }
    List<String> list(String k) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(str(k,"[]"));
            for (int i=0;i<a.length();i++) { String v=a.optString(i); if(!v.isEmpty()&&!result.contains(v)) result.add(v); }
        } catch(Exception ignored) {}
        return result;
    }
    void list(String k,List<String> v) { p.edit().putString(k,new JSONArray(v).toString()).apply(); }
    void safeMode() { p.edit().putString("performance","lite").putBoolean("custom_wall",false).putString("theme","ocean").putInt("accent",0).apply(); }
}
