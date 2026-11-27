package com.eagleport.launcher;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.media.AudioManager;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int WALL_PICK=100, HOME_ROLE=101;
    private static final int WHITE=Color.WHITE, MUTED=0xffb8d4e8;
    private static final String RELEASES="https://github.com/Jeran24/Releases";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private Prefs prefs; private AppCatalog catalog; private WallpaperStore wallpaper;
    private Bitmap wallImage;
    private List<AppCatalog.Entry> entries=new ArrayList<>();
    private LinearLayout root, body, nav, searchResults;
    private TextView clock, date;
    private ScrollView pageScroll;
    private EditText search;
    private String page="home";
    private int base,accent,homePage=0;
    private boolean lite, alive=true, resumed=false, scanning=false, loaded=false, editing=false;
    private float startX,startY; private String startPage; private int navZone=-1;
    private long downAt;
    private boolean handledSwipe=false;
    private boolean sliderTouch=false;
    private final Runnable tick=new Runnable(){public void run(){
        if(!resumed)return;
        if(clock!=null)clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        if(date!=null)date.setText(DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
        ui.postDelayed(this,15000);
    }};

    @Override public void onCreate(Bundle b){
        super.onCreate(b); prefs=new Prefs(this); catalog=new AppCatalog(this); wallpaper=new WallpaperStore(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // A failed launch should never trap the user in a restart loop.
        if(prefs.bool("startup_pending",false)) prefs.safeMode();
        prefs.put("startup_pending",true);
        render(prefs.bool("onboarding_done",false)?"home":"welcome");
        worker.execute(()->{
            Bitmap img=null;
            if(prefs.bool("custom_wall",false)) img=wallpaper.load();
            final Bitmap result=img;
            ui.post(()->{if(!alive){if(result!=null)result.recycle();return;} wallImage=result; if(result==null&&prefs.bool("custom_wall",false))prefs.put("custom_wall",false); applyWall();});
        });
    }
    @Override protected void onResume(){super.onResume();resumed=true;applyNav();ui.removeCallbacks(tick);tick.run();scanApps();}
    @Override protected void onPause(){resumed=false;ui.removeCallbacks(tick);super.onPause();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);render("home");}
    @Override protected void onDestroy(){alive=false;ui.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&root!=null)applyNav();}
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(catalog!=null)catalog.clear();
        if(level==TRIM_MEMORY_RUNNING_LOW||level==TRIM_MEMORY_RUNNING_CRITICAL||level==TRIM_MEMORY_RUNNING_MODERATE){
            if(prefs!=null&&prefs.bool("adaptive",true)) lite=true;
        }
    }
    private void scanApps(){
        if(scanning)return;scanning=true;
        worker.execute(()->{
            List<AppCatalog.Entry> result;
            try{result=catalog.scan();}catch(RuntimeException ex){result=null;}
            final List<AppCatalog.Entry> found=result;
            ui.post(()->{
                scanning=false;if(!alive)return;
                if(found==null){if(!loaded)toast("Unable to read apps. Open recovery to try again.");return;}
                boolean changed=!loaded||!ids(entries).equals(ids(found));entries=found;loaded=true;
                seed();
                if(changed&&(page.equals("home")||page.equals("library")||page.equals("edit")))render(page);
            });
        });
    }
    private List<String> ids(List<AppCatalog.Entry> list){List<String> out=new ArrayList<>();for(AppCatalog.Entry e:list)out.add(e.id);return out;}
    private void seed(){
        if(!prefs.has("home_apps")){
            List<String> picked=new ArrayList<>();
            String[] names={"phone","messages","gallery","camera","music","play store","files","settings","clock","calendar","notes","calculator"};
            for(String n:names)for(AppCatalog.Entry e:entries)if(e.label.toLowerCase(Locale.ROOT).equals(n)&&!picked.contains(e.id)){picked.add(e.id);break;}
            for(AppCatalog.Entry e:entries){if(picked.size()>=12)break;if(!picked.contains(e.id))picked.add(e.id);}
            prefs.list("home_apps",picked);
        }
        if(!prefs.has("dock_apps")){
            List<String> picked=new ArrayList<>();
            for(String n:new String[]{"whatsapp","telegram","chrome","phone"})for(AppCatalog.Entry e:entries)if(e.label.equalsIgnoreCase(n)){picked.add(e.id);break;}
            for(AppCatalog.Entry e:entries){if(picked.size()>=4)break;if(!picked.contains(e.id))picked.add(e.id);}
            prefs.list("dock_apps",picked);
        }
    }
    private AppCatalog.Entry entry(String id){for(AppCatalog.Entry e:entries)if(e.id.equals(id))return e;return null;}
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void colors(){
        String theme=prefs.str("theme","ocean"); int a;
        switch(theme){
            case "emerald":base=0xff06231e;a=0xff57e7b5;break;
            case "violet":base=0xff190f32;a=0xffc49aff;break;
            case "sunset":base=0xff301424;a=0xffffab72;break;
            case "midnight":base=0xff101820;a=0xff94b4cf;break;
            default:base=0xff041021;a=0xff55d5ff;
        }
        accent=prefs.num("accent",0);if(accent==0)accent=a;
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        lite=prefs.str("performance","balanced").equals("lite")||!prefs.bool("glass_effects",true);
        if(prefs.bool("adaptive",true)&&am!=null){ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);lite=lite||mi.lowMemory||am.isLowRamDevice();}
    }
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size<16?size+2:size);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView title(String s,float size){TextView t=text(s,size,WHITE);t.setTypeface(null,Typeface.BOLD);return t;}
    private TextView button(String s,Runnable r){TextView t=text(s,14,WHITE);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(44));t.setPadding(dp(10),dp(7),dp(10),dp(7));t.setBackground(Glass.panel(base,accent,dp(14),lite));t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->r.run());return t;}
    private void add(LinearLayout parent,View v,int h){parent.addView(v,lp(-1,h));}
    private void space(LinearLayout l,int h){add(l,new View(this),dp(h));}
    private void section(LinearLayout l,String s){space(l,10);TextView t=text(s.toUpperCase(Locale.ROOT),11,accent);t.setLetterSpacing(.1f);add(l,t,-2);space(l,6);}
    private LinearLayout card(LinearLayout l){LinearLayout c=vertical();c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(Glass.panel(base,accent,dp(17),lite));add(l,c,-2);space(l,8);return c;}
    private void action(LinearLayout l,String name,String desc,Runnable r){LinearLayout c=card(l);add(c,title(name,15),-2);if(!desc.isEmpty())add(c,text(desc,12,MUTED),-2);c.setMinimumHeight(dp(48));c.setOnClickListener(v->r.run());c.setFocusable(true);c.setContentDescription(name+". "+desc);}
    private void heading(LinearLayout l,String kicker,String label){add(l,text(kicker,11,accent),-2);add(l,title(label,23),-2);space(l,10);}
    private LinearLayout scroll(){ScrollView s=new ScrollView(this);pageScroll=s;s.setFillViewport(false);s.setClipToPadding(false);s.setVerticalScrollBarEnabled(false);body.addView(s,lp(-1,-1));LinearLayout l=vertical();l.setPadding(dp(14),dp(6),dp(14),dp(12));s.addView(l,new ScrollView.LayoutParams(-1,-2));return l;}
    private void applyWall(){if(root!=null)root.setBackground(new Glass.Wall(base,accent,prefs.bool("custom_wall",false)?wallImage:null,prefs.bool("wall_fit",false),prefs.bool("wall_tint",false)?35:0,prefs.num("wall_opacity",100)));}
    private void applyNav(){
        if(prefs==null)return;
        // Only the launcher window uses immersive navigation. Android's bars remain
        // available by edge swipe and are unaffected inside other applications.
        int flags=0;
        if(prefs.bool("gesture",true)&&!page.equals("recovery")){
            flags=View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private void render(String next){
        if(!alive)return;
        if(search!=null&&search.hasFocus()){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
        final int previousScroll=next.equals(page)&&pageScroll!=null?pageScroll.getScrollY():0;
        pageScroll=null;page=next;search=null;searchResults=null;clock=null;date=null;
        try{
            colors();getWindow().setStatusBarColor(base);getWindow().setNavigationBarColor(base);
            root=vertical();root.setClipToPadding(false);applyWall();
            if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
            body=vertical();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            setContentView(root);
            switch(page){
                case "welcome": welcome();break;
                case "library": library();break;
                case "settings": settingsPage();break;
                case "health": health();break;
                case "control": control();break;
                case "recents": recents();break;
                case "edit": editHome();break;
                case "recovery": recovery();break;
                default:page="home";home();
            }
            bottom();applyNav();
            if(!next.equals("settings")&&!lite&&prefs.bool("animations",true)){body.setAlpha(.65f);body.setTranslationY(dp(6));body.animate().alpha(1f).translationY(0f).setDuration(190).start();}
            if(pageScroll!=null&&previousScroll>0){final ScrollView target=pageScroll;target.post(()->target.scrollTo(0,previousScroll));}
            if(resumed){ui.removeCallbacks(tick);tick.run();}
            prefs.put("startup_pending",false);
        }catch(RuntimeException e){android.util.Log.e("EaglePort","Render failed",e);fallback();}
    }
    private void fallback(){
        page="recovery";prefs.safeMode();getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout v=vertical();v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setBackgroundColor(0xff101820);
        add(v,title("EaglePort recovery",24),-2);add(v,text("A screen could not load. Your saved app shortcuts are preserved.",15,WHITE),-2);
        add(v,button("Try safe Home",()->render("home")),-2);
        add(v,button("Choose Android Home app",this::chooseHome),-2);
        setContentView(v);prefs.put("startup_pending",false);
    }
    private void bottom(){
        // No surrounding rectangle on any screen. In gesture mode only the
        // central white indicator is drawn; left/right still handle swipes.
        nav=horizontal();nav.setPadding(dp(10),0,dp(10),0);
        boolean gesture=prefs.bool("gesture",true);
        String[] labels={"Back","Home","Recents"};
        for(int i=0;i<3;i++){
            final int zone=i;FrameLayout cell=new FrameLayout(this);
            cell.setContentDescription(labels[i]+(gesture?" swipe up zone":""));
            cell.setFocusable(true);
            if(gesture){
                if(i==1){View line=new View(this);GradientDrawable bg=new GradientDrawable();bg.setColor(WHITE);bg.setCornerRadius(dp(4));line.setBackground(bg);
                    FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(94),dp(4),Gravity.CENTER);cell.addView(line,p);}
                if(i==1)cell.setOnClickListener(v->navAction(1));
            }else{
                TextView symbol=text(new String[]{"‹","⌂","▢"}[i],23,WHITE);symbol.setGravity(Gravity.CENTER);
                cell.addView(symbol,new FrameLayout.LayoutParams(-1,-1));cell.setOnClickListener(v->navAction(zone));
            }
            nav.addView(cell,new LinearLayout.LayoutParams(0,dp(34),1));
        }
        add(root,nav,dp(34));
        if(Build.VERSION.SDK_INT>=30){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                nav.setVisibility(insets.isVisible(WindowInsets.Type.ime())?View.GONE:View.VISIBLE);
                if(Build.VERSION.SDK_INT>=35){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
                return insets;
            });
            root.requestApplyInsets();
        }
    }
    private void navAction(int zone){if(zone==0)onBackPressed();else if(zone==1)render("home");else render("recents");}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        if(root==null)return super.dispatchTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            startX=event.getRawX();startY=event.getRawY();downAt=SystemClock.uptimeMillis();startPage=page;navZone=-1;handledSwipe=false;
            if(nav!=null&&nav.isShown()&&prefs.bool("gesture",true)){
                int[] xy=new int[2];nav.getLocationOnScreen(xy);
                if(startY>=xy[1]&&startY<=xy[1]+nav.getHeight())navZone=Math.max(0,Math.min(2,(int)((startX-xy[0])*3/Math.max(1,nav.getWidth()))));
            }
        }
        if(action==MotionEvent.ACTION_UP&&!handledSwipe&&!sliderTouch&&Objects.equals(startPage,page)){
            float dx=event.getRawX()-startX,dy=event.getRawY()-startY;float threshold=dp(44);
            Runnable go=null;
            if(navZone>=0&&dy<-dp(26)&&Math.abs(dy)>Math.abs(dx)){final int z=navZone;go=()->navAction(z);}
            else if(dy<-threshold&&Math.abs(dy)>Math.abs(dx)&&startX>getResources().getDisplayMetrics().widthPixels*.67f&&startY>getResources().getDisplayMetrics().heightPixels*.80f&&prefs.bool("gesture",true)){go=()->render("recents");}
            else if(page.equals("home")&&SystemClock.uptimeMillis()-downAt<1100){
                if(Math.abs(dx)>Math.abs(dy)&&dx<-threshold)go=()->render("settings");
                else if(Math.abs(dx)>Math.abs(dy)&&dx>threshold)go=()->render("control");
                else if(Math.abs(dy)>Math.abs(dx)&&dy<-threshold)go=()->render("library");
                else if(Math.abs(dy)>Math.abs(dx)&&dy>threshold)go=()->render("control");
            }
            if(go==null&&Math.abs(dx)>Math.abs(dy)&&Math.abs(dx)>threshold&&navZone<0){
                if(page.equals("library")&&dx<0)go=this::launchLast;
                else if(!page.equals("home")&&dx>0)go=this::onBackPressed;
            }
            if(go!=null){
                handledSwipe=true;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();go.run();return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public void onBackPressed(){
        if(page.equals("home"))return;
        if(page.equals("edit")||page.equals("recovery"))render("settings");else render("home");
    }
    private void home(){
        LinearLayout content=vertical();content.setPadding(dp(13),dp(4),dp(13),dp(5));body.addView(content,lp(-1,-1));
        LinearLayout brand=horizontal();ImageView mark=new ImageView(this);mark.setImageResource(com.eagleport.launcher.R.drawable.eagle);mark.setContentDescription("EaglePort logo");mark.setVisibility(prefs.bool("show_logo",true)?View.VISIBLE:View.INVISIBLE);brand.addView(mark,lp(dp(52),dp(52)));
        brand.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        brand.addView(button("◉",()->render("control")),lp(dp(48),dp(48)));spaceHorizontal(brand,6);brand.addView(button("⚙",()->open(new Intent(Settings.ACTION_SETTINGS))),lp(dp(48),dp(48)));add(content,brand,-2);space(content,7);
        // A non-clickable hint: the app library opens by swipe, never by drawer icon.
        TextView hint=text("⌕  Swipe up to search apps",12,MUTED);hint.setPadding(dp(12),0,dp(12),0);hint.setBackground(Glass.panel(base,accent,dp(16),lite));add(content,hint,dp(36));space(content,8);
        if(prefs.bool("home_widgets",true)){
        LinearLayout time=card(content);clock=text(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()),39,WHITE);add(time,clock,-2);date=text(DateFormat.getDateInstance(DateFormat.FULL).format(new Date()),12,MUTED);add(time,date,-2);
        Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);int percent=scale>0&&level>=0?level*100/scale:-1;
        LinearLayout widget=card(content);add(widget,title(percent>=0?"Battery  "+percent+"%":"Battery",17),-2);add(widget,text("Offline device widget · live weather not enabled",11,MUTED),-2);widget.setOnClickListener(v->render("health"));}
        else {clock=null;date=null;}
        ScrollView gridScroll=new ScrollView(this);gridScroll.setVerticalScrollBarEnabled(false);content.addView(gridScroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout list=vertical();gridScroll.addView(list,new ScrollView.LayoutParams(-1,-2));
        List<AppCatalog.Entry> selected=new ArrayList<>();for(String id:prefs.list("home_apps")){AppCatalog.Entry e=entry(id);if(e!=null)selected.add(e);}
        if(selected.isEmpty())add(list,text(loaded?"No shortcuts. Hold an empty area or open Settings to add apps.":"Loading installed apps…",13,MUTED),dp(54));
        int count=Math.max(1,(selected.size()+11)/12);homePage=Math.min(homePage,count-1);
        int from=homePage*12,to=Math.min(from+12,selected.size());if(from<to)grid(list,selected.subList(from,to),false,true);
        list.setOnLongClickListener(v->{editing=false;render("edit");return true;});
        if(count>1){LinearLayout pages=horizontal();pages.setGravity(Gravity.CENTER);for(int i=0;i<count;i++){final int p=i;TextView dot=text(i==homePage?"●":"○",18,WHITE);dot.setGravity(Gravity.CENTER);pages.addView(dot,lp(dp(42),dp(30)));dot.setOnClickListener(v->{homePage=p;render("home");});}add(content,pages,-2);}
        LinearLayout dock=horizontal();dock.setPadding(dp(5),dp(6),dp(5),dp(6));dock.setBackground(Glass.panel(base,accent,dp(18),lite));
        for(String id:prefs.list("dock_apps")){AppCatalog.Entry e=entry(id);if(e!=null)dock.addView(appIcon(e,false,true),new LinearLayout.LayoutParams(0,dp(Math.max(56,64*Math.max(70,Math.min(125,prefs.num("icon_scale",100)))/100f)),1));}
        if(dock.getChildCount()==0){TextView empty=text("Hold here to customize dock",12,MUTED);empty.setGravity(Gravity.CENTER);dock.addView(empty,lp(-1,dp(40)));}
        dock.setOnLongClickListener(v->{editing=true;render("edit");return true;});add(content,dock,-2);
    }
    private void spaceHorizontal(LinearLayout l,int d){l.addView(new View(this),lp(dp(d),1));}
    private View appIcon(AppCatalog.Entry e,boolean honey,boolean compact){
        LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(2),dp(3),dp(2),dp(3));cell.setContentDescription(e.label);
        ImageView image=new ImageView(this);image.setImageBitmap(catalog.icon(e));image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize=dp((compact?39:honey?50:58)*Math.max(70,Math.min(125,prefs.num("icon_scale",100)))/100f);
        if(!honey){image.setBackground(Glass.panel(base,accent,dp(17),lite));image.setPadding(dp(5),dp(5),dp(5),dp(5));}
        if(honey){
            GradientDrawable round=new GradientDrawable();round.setShape(GradientDrawable.OVAL);round.setColor(Glass.alpha(accent,35));round.setStroke(1,Glass.alpha(accent,100));image.setBackground(round);image.setClipToOutline(true);image.setPadding(dp(3),dp(3),dp(3),dp(3));
        }
        cell.addView(image,lp(iconSize,iconSize));
        if(!compact){TextView label=text(e.label,10,WHITE);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);cell.addView(label,lp(-1,dp(19)));}
        cell.setFocusable(true);cell.setClickable(true);cell.setOnClickListener(v->launch(e));
        cell.setOnLongClickListener(v->{appMenu(e);return true;});return cell;
    }
    private void grid(LinearLayout parent,List<AppCatalog.Entry> apps,boolean honey,boolean isHome){
        int pos=0,rowIndex=0;
        while(pos<apps.size()){
            int columns=honey?(rowIndex%2==0?4:3):prefs.bool("adaptive_grid",true)&&apps.size()<=6?Math.min(3,apps.size()):4;int take=Math.min(columns,apps.size()-pos);
            LinearLayout row=horizontal();row.setGravity(Gravity.CENTER);
            if(honey&&columns==3)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,.5f));
            for(int i=0;i<take;i++)row.addView(appIcon(apps.get(pos++),honey,false),new LinearLayout.LayoutParams(0,dp(Math.max(76,(honey?90:95)*Math.max(70,Math.min(125,prefs.num("icon_scale",100)))/100f)),1));
            for(int i=take;i<columns;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
            if(honey&&columns==3)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,.5f));
            add(parent,row,-2);rowIndex++;
        }
    }
    private void library(){
        LinearLayout l=vertical();l.setPadding(dp(13),dp(5),dp(13),dp(7));body.addView(l,lp(-1,-1));
        heading(l,"YOUR APPLICATIONS","App Library");
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("Search apps…");search.setTextSize(15);search.setPadding(dp(12),0,dp(12),0);search.setBackground(Glass.panel(base,accent,dp(14),lite));add(l,search,dp(46));space(l,7);
        add(l,text("Hold an app to add it to Home or Dock.",11,MUTED),-2);
        ScrollView sc=new ScrollView(this);sc.setVerticalScrollBarEnabled(false);l.addView(sc,new LinearLayout.LayoutParams(-1,0,1));searchResults=vertical();sc.addView(searchResults,new ScrollView.LayoutParams(-1,-2));
        l.setFocusableInTouchMode(true);l.requestFocus();renderSearch("");
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){renderSearch(s.toString());}public void afterTextChanged(Editable e){}});
    }
    private void renderSearch(String q){if(searchResults==null)return;searchResults.removeAllViews();List<AppCatalog.Entry> match=new ArrayList<>();for(AppCatalog.Entry e:entries)if(e.label.toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT)))match.add(e);if(match.isEmpty())add(searchResults,text(loaded?"No matching apps":"Loading apps…",14,MUTED),dp(50));else grid(searchResults,match,prefs.str("layout","grid").equals("honey")&&q.trim().isEmpty(),false);}
    private void launchLast(){
        for(String id:prefs.list("history")){AppCatalog.Entry e=entry(id);if(e!=null){launch(e);return;}}
        toast("No recent app available");
    }
    private void launch(AppCatalog.Entry e){
        ComponentName component=ComponentName.unflattenFromString(e.id);if(component==null)return;
        try{
            Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);List<String> history=prefs.list("history");history.remove(e.id);history.add(0,e.id);if(history.size()>12)history=new ArrayList<>(history.subList(0,12));prefs.list("history",history);
        }catch(RuntimeException ex){toast("This app is not available");scanApps();}
    }
    private void appMenu(AppCatalog.Entry e){
        List<String> h=prefs.list("home_apps"),d=prefs.list("dock_apps");
        new AlertDialog.Builder(this).setTitle(e.label).setItems(new String[]{h.contains(e.id)?"Remove from Home":"Add to Home",d.contains(e.id)?"Remove from Dock":"Add to Dock","Android app info"},(dlg,which)->{
            if(which==0)toggleShortcut("home_apps",e.id,36);
            else if(which==1)toggleShortcut("dock_apps",e.id,4);
            else{ComponentName cn=ComponentName.unflattenFromString(e.id);if(cn!=null)open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+cn.getPackageName())));}
        }).show();
    }
    private void toggleShortcut(String key,String id,int limit){
        List<String> a=prefs.list(key);if(a.contains(id))a.remove(id);else{if(a.size()>=limit){toast("Remove a shortcut first");return;}a.add(id);}
        prefs.list(key,a);toast("Shortcuts saved");if(!page.equals("library"))render(page);
    }
    private void editHome(){
        LinearLayout l=scroll();heading(l,"CUSTOMIZE",editing?"Dock apps":"Home apps");
        add(l,button(editing?"Edit Home instead":"Edit Dock instead",()->{editing=!editing;render("edit");}),-2);space(l,6);
        String key=editing?"dock_apps":"home_apps";List<String> selected=prefs.list(key);int limit=editing?4:36;
        add(l,text(selected.size()+" / "+limit+" shortcuts · tap to add or remove",12,MUTED),-2);
        for(AppCatalog.Entry e:entries){boolean on=selected.contains(e.id);action(l,(on?"✓  ":"＋  ")+e.label,on?"Remove shortcut":"Add shortcut",()->toggleShortcut(key,e.id,limit));}
    }
    private void toggleSetting(LinearLayout l,String label,String description,String key,boolean fallback,Runnable changed){
        LinearLayout c=card(l);LinearLayout row=horizontal();LinearLayout words=vertical();
        add(words,title(label,15),-2);add(words,text(description,12,MUTED),-2);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);sw.setContentDescription(label);sw.setChecked(prefs.bool(key,fallback));
        sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,0xffd0dbe5}));
        row.addView(sw,lp(-2,dp(48)));add(c,row,-2);
        sw.setOnCheckedChangeListener((v,on)->{prefs.put(key,on);changed.run();});
        words.setOnClickListener(v->sw.setChecked(!sw.isChecked()));
    }
    private void welcome(){
        LinearLayout l=scroll();ImageView logo=new ImageView(this);logo.setImageResource(com.eagleport.launcher.R.drawable.eagle);add(l,logo,dp(112));heading(l,"WELCOME TO EAGLEPORT","Make your phone yours");add(l,text("A warm welcome from Ntirenganya Janvier and Mugisha Is\'haq.",18,WHITE),-2);space(l,12);
        action(l,"↑  Swipe up","Open the app library and search installed apps",()->render("library"));
        action(l,"↓  Swipe down","Open EaglePort Control Center from Home",()->render("control"));
        action(l,"←  Swipe left","Open launcher settings",()->render("settings"));
        action(l,"→  Swipe right","Open Control Center",()->render("control"));
        action(l,"↗  Bottom-right swipe","Open recent applications",()->render("recents"));
        action(l,"Optional screen lock","Set a PIN or password using Android security",()->open(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
        action(l,"Choose default launcher","Select EaglePort as your Home app",this::chooseHome);
        add(l,button("Get started",()->{prefs.put("onboarding_done",true);render("home");}),-2);
    }
    private void settingsPage(){
        LinearLayout l=scroll();heading(l,"MAKE IT YOURS","Launcher Settings");
        section(l,"Appearance");
        toggleSetting(l,"Show EaglePort logo","Show only the logo on Home","show_logo",true,()->{});
        toggleSetting(l,"Adaptive app layout","Use fewer columns when there are few apps","adaptive_grid",true,()->{});
        LinearLayout iconCard=card(l);TextView iconLabel=title("App icon size: "+prefs.num("icon_scale",100)+"%",15);add(iconCard,iconLabel,-2);SeekBar iconScale=new SeekBar(this);iconScale.setMax(55);iconScale.setProgress(Math.max(0,Math.min(55,prefs.num("icon_scale",100)-70)));add(iconCard,iconScale,-2);iconScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){if(user){prefs.put("icon_scale",70+v);iconLabel.setText("App icon size: "+(70+v)+"%");}}public void onStartTrackingTouch(SeekBar b){sliderTouch=true;}public void onStopTrackingTouch(SeekBar b){sliderTouch=false;}});
        String[] themes={"ocean","emerald","violet","midnight","sunset"};
        String[] labels={"Ocean Glass","Emerald Glass","Aurora Violet","Midnight Lite","Sunset Glow"};
        for(int i=0;i<themes.length;i++){final String t=themes[i];action(l,labels[i],prefs.str("theme","ocean").equals(t)?"Selected":"Tap to apply",()->{prefs.put("theme",t);prefs.put("accent",0);render("settings");});}
        action(l,"Custom accent","Choose a color",this::accentPicker);
        section(l,"Wallpaper");
        action(l,"Choose a photo","Keeps the image as the background, without artificial waves",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,WALL_PICK);}catch(ActivityNotFoundException ex){toast("No image picker installed");}
        });
        toggleSetting(l,"Fit wallpaper","Show full photo instead of center crop","wall_fit",false,this::applyWall);
        toggleSetting(l,"Wallpaper color tint","Subtle accent overlay","wall_tint",false,this::applyWall);
        LinearLayout opacityCard=card(l);TextView opacityLabel=title("Wallpaper opacity: "+prefs.num("wall_opacity",100)+"%",15);add(opacityCard,opacityLabel,-2);
        SeekBar opacity=new SeekBar(this);opacity.setMax(100);opacity.setProgress(prefs.num("wall_opacity",100));add(opacityCard,opacity,-2);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){prefs.put("wall_opacity",value);opacityLabel.setText("Wallpaper opacity: "+value+"%");applyWall();}}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        action(l,"Restore theme wallpaper","Uses the selected theme's background",()->{prefs.put("custom_wall",false);render("settings");});
        section(l,"Navigation & apps");
        action(l,"App library layout",prefs.str("layout","grid").equals("grid")?"Grid":"Honeycomb · staggered circular icons",()->{prefs.put("layout",prefs.str("layout","grid").equals("grid")?"honey":"grid");render("settings");});
        toggleSetting(l,"Gesture navigation","Single Home line when enabled","gesture",true,()->{root.removeView(nav);bottom();applyNav();});
        action(l,"Home screen apps","Choose your own app shortcuts",()->{editing=false;render("edit");});
        action(l,"Dock apps","Choose up to four shortcuts",()->{editing=true;render("edit");});
        section(l,"Performance & system");
        toggleSetting(l,"Screen animations","Lightweight transitions","animations",true,()->{});
        toggleSetting(l,"Home widgets","Show clock and battery cards","home_widgets",true,()->{});
        toggleSetting(l,"Glass effects","Disable transparent glass for weaker phones","glass_effects",true,()->{});
        action(l,"Device security","Manage Android screen lock",()->open(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
        action(l,"Launcher guide","Review gestures and setup",()->render("welcome"));
        action(l,"Performance profile",prefs.str("performance","balanced"),()->new AlertDialog.Builder(this).setTitle("Performance profile").setItems(new String[]{"Lite","Balanced","Rich"},(d,w)->{String[] profiles={"lite","balanced","rich"};prefs.put("performance",profiles[w]);prefs.put("glass_effects",w!=0);prefs.put("animations",w!=0);prefs.put("home_widgets",w!=0);prefs.put("adaptive",w!=2);render("settings");}).show());
        toggleSetting(l,"Adaptive memory mode","Reduce graphics when memory is low","adaptive",true,this::colors);
        action(l,"Device Center","Live battery, temperature, RAM and storage",()->render("health"));
        action(l,"Default launcher","Select EaglePort or your existing Android launcher",this::chooseHome);
        action(l,"Android keyboard","Use your current Android input method",()->open(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        action(l,"Check for launcher updates","Open official repository releases · settings are retained on signed in-place updates",()->open(new Intent(Intent.ACTION_VIEW,Uri.parse(RELEASES))));
        action(l,"Recovery","Safe appearance reset and Android fallback",()->render("recovery"));
        TextView credit=text("EaglePort OS v1.1 · By Ntirenganya Janvier Coder",12,MUTED);credit.setGravity(Gravity.CENTER);add(l,credit,-2);
    }
    private void accentPicker(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("#55D5FF");input.setText(String.format(Locale.US,"#%06X",accent&0xffffff));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Accent color (hex)").setView(input).setNegativeButton("Cancel",null).setNeutralButton("Theme color",(d,w)->{prefs.put("accent",0);render("settings");}).setPositiveButton("Apply",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String s=input.getText().toString().trim();if(!s.matches("#[0-9a-fA-F]{6}")){input.setError("Use #RRGGBB");return;}prefs.put("accent",Color.parseColor(s));dialog.dismiss();render("settings");}));dialog.show();
    }
    private void chooseHome(){
        if(Build.VERSION.SDK_INT>=29){
            RoleManager roles=(RoleManager)getSystemService(ROLE_SERVICE);
            if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)&&!roles.isRoleHeld(RoleManager.ROLE_HOME)){
                try{startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),HOME_ROLE);return;}catch(RuntimeException ignored){}
            }
        }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==WALL_PICK&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();worker.execute(()->{
                try{wallpaper.importImage(uri);Bitmap img=wallpaper.load();if(img==null)throw new java.io.IOException("Unable to read saved photo");
                    ui.post(()->{if(!alive){img.recycle();return;}wallImage=img;prefs.put("custom_wall",true);render("settings");toast("Wallpaper saved");});
                }catch(Exception|OutOfMemoryError e){ui.post(()->{if(alive)toast("Wallpaper could not be loaded. Previous settings kept.");});}
            });
        }
    }
    private void health(){
        LinearLayout l=scroll();heading(l,"EAGLEPORT / DEVICE CENTER","Phone status");
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(b!=null){
            int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1),temp=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            LinearLayout c=card(l);add(c,title(scale>0&&level>=0?(100*level/scale)+"% battery":"Battery unavailable",28),-2);add(c,text(temp!=Integer.MIN_VALUE?String.format(Locale.US,"Battery temperature %.1f°C",temp/10f):"Battery temperature unavailable",14,MUTED),-2);add(c,text("Remaining time: Android estimate unavailable",12,MUTED),-2);
        }
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(am!=null){ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();am.getMemoryInfo(m);metric(l,"Available RAM",String.format(Locale.US,"%d / %d MB",m.availMem/1048576,m.totalMem/1048576),m.lowMemory?"Android reports low memory":"System-wide memory; not launcher PSS");}
        StatFs disk=new StatFs(getFilesDir().getAbsolutePath());metric(l,"Internal storage",String.format(Locale.US,"%.1f GB free",disk.getAvailableBytes()/1e9),"Data partition");
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        metric(l,"Performance",lite?"Ultra Lite graphics":"Balanced graphics",pm!=null&&pm.isPowerSaveMode()?"Android battery saver on":"No constant background monitoring");
        metric(l,"Android","Version "+Build.VERSION.RELEASE,Build.MANUFACTURER+" "+Build.MODEL);
        action(l,"Recent launcher activity",prefs.list("history").size()+" recorded launches · not running processes",()->render("recents"));
        action(l,"Refresh readings","Read current values again",()->render("health"));
        add(l,button("Launcher Settings",()->render("settings")),-2);
    }
    private void metric(LinearLayout l,String label,String value,String desc){LinearLayout c=card(l);add(c,text(label,12,accent),-2);add(c,title(value,20),-2);add(c,text(desc,12,MUTED),-2);}
    private void control(){
        LinearLayout l=scroll();heading(l,"EAGLEPORT","Control Center");
        add(l,text("Quick controls open Android's own settings where required.",12,MUTED),-2);
        String[] names={"Wi-Fi","Bluetooth","Mobile data","Airplane","Do Not Disturb","Display","Hotspot","Notifications"};
        String[] actions={Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_BLUETOOTH_SETTINGS,Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_INTERNET_CONNECTIVITY:Settings.ACTION_WIRELESS_SETTINGS,Settings.ACTION_AIRPLANE_MODE_SETTINGS,Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,Settings.ACTION_DISPLAY_SETTINGS,Settings.ACTION_WIRELESS_SETTINGS,Settings.ACTION_APP_NOTIFICATION_SETTINGS};
        for(int i=0;i<names.length;i+=2){LinearLayout row=horizontal();for(int j=i;j<i+2;j++){final String a=actions[j];TextView tile=button(names[j],()->open(Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(a)?new Intent(a).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()):new Intent(a)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(62),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,p);}add(l,row,-2);}
        LinearLayout c=card(l);add(c,title("Launcher brightness",14),-2);SeekBar brightness=new SeekBar(this);brightness.setMax(100);float current=getWindow().getAttributes().screenBrightness;brightness.setProgress(current<0?60:(int)(current*100));add(c,brightness,-2);brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user){WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=Math.max(.08f,p/100f);getWindow().setAttributes(a);}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(audio!=null){LinearLayout vol=card(l);add(vol,title("Media volume",14),-2);SeekBar slider=new SeekBar(this);slider.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));slider.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));add(vol,slider,-2);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user)try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,p,0);}catch(SecurityException ex){toast("Volume change not permitted");}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}
        action(l,"System notifications","Android controls the real notification shade",()->open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        add(l,button("⌃",this::onBackPressed),dp(55));
    }
    private void recents(){
        LinearLayout l=scroll();heading(l,"RECENT LAUNCHES","Recent Apps");
        add(l,text("EaglePort history only. Android controls actual tasks and process closing.",12,MUTED),-2);space(l,8);
        for(String id:prefs.list("history")){AppCatalog.Entry e=entry(id);if(e==null)continue;LinearLayout c=card(l);LinearLayout r=horizontal();r.addView(appIcon(e,false,true),lp(dp(56),dp(50)));r.addView(title(e.label,16),new LinearLayout.LayoutParams(0,-2,1));r.addView(button("Remove",()->{List<String> h=prefs.list("history");h.remove(id);prefs.list("history",h);render("recents");}),lp(dp(85),dp(44)));add(c,r,-2);}
        action(l,"Clear launcher history","Does not force-stop applications",()->{prefs.list("history",new ArrayList<>());render("recents");});
        action(l,"Android navigation settings","Use system Recents for real tasks",()->open(new Intent(Settings.ACTION_SETTINGS)));
    }
    private void recovery(){
        LinearLayout l=scroll();heading(l,"EAGLEGUARD","Recovery");
        action(l,"Safe appearance mode","Preserve app shortcuts and photo file; turn off custom effects",()->{prefs.safeMode();render("home");});
        action(l,"Reload installed apps","Rescan without deleting settings",()->{scanApps();toast("Refreshing app list");});
        action(l,"Choose Android Home","Return to the original launcher whenever needed",this::chooseHome);
    }
    private void open(Intent i){try{startActivity(i);}catch(RuntimeException e){toast("Setting is not available on this device");}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
