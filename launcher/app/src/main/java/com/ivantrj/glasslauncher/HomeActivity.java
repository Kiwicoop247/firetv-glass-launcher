package com.ivantrj.glasslauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Apple TV style home screen.
 *
 * Focus is handled here, not by Android's focus search, so D-pad movement is
 * deterministic: three zones (top bar, dock, grid), Up/Down move exactly one
 * row, and the ends of a row never fall through to another zone.
 */
public class HomeActivity extends Activity {

    private static final int TOP = 0, DOCK = 1, GRID = 2, NOW_PLAYING = 3, WEATHER = 4, RADAR = 5, SPORTS = 6;
    private static final int COLUMNS = 1;
    private static final int PILL_INPUTS = 0, PILL_WIFI = 1, PILL_SETTINGS = 2;
    private static final int NEUTRAL_GLOW = 0xFF8E8E9E;

    private View root, page, band, glow, pillRow;
    private View recentPanel, drawerPanel;
    private int panelMode = PanelNavigation.HOME;
    private View[] pills;
    private GlassHighlight highlight;
    private SpotifyPlayback spotify;
    private ScrollingSongView playingSong;
    private View playingCard;
    private ImageView albumArt;
    private TextView playingArtist, playingAlbum;
    private PlaybackProgressView playingProgress;
    private FlowingBackground flowingBackground;
    private boolean musicActive;
    private int[] musicColors = ArtworkColors.dock(0xFF1DB954);
    private Bitmap paletteArtwork;
    private String paletteTrack;
    private int paletteGeneration;
    private DashboardData dashboard;
    private SportsData sports;
    private View sportsPanel;
    private List<SportsData.Event> sportsEvents = new ArrayList<>();
    private String sportsStatus = "Loading today's schedule…";
    private int sportsPage;
    private static final int SPORTS_PAGE_SIZE = 4;
    private final Runnable sportsRotation = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            if (zone!=SPORTS && panelMode==PanelNavigation.HOME && sportsEvents.size()>SPORTS_PAGE_SIZE) {
                sportsPage=(sportsPage+1)%sportsPageCount();
                renderSports();
            }
            main.postDelayed(this,10000);
        }
    };
    private DashboardClock analogClock;
    private FlightRadarView radar;
    private View weatherPanel, radarPanel, musicIdle;
    private List<DashboardData.Flight> nearbyFlights = new ArrayList<>();
    private DashboardData.Weather latestWeather;
    private String weatherStatus = "Connecting…", flightStatus = "Connecting…";
    private PendingIntent openSpotify;
    private final BroadcastReceiver spotifyAccessReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { spotify.refresh(); }
    };
    private TextView dockTitle, wifiName;
    private LinearLayout dockRow, inputsMenu;
    private RecyclerView grid;
    private ImageView wallpaper;
    private GridAdapter adapter;

    private final List<AppInfo> dockApps = new ArrayList<>();
    private List<AppInfo> gridApps = new ArrayList<>();

    private int zone = WEATHER, topIdx = 0, dockIdx = 0, gridIdx = 0;
    private String restoreGridPkg;
    private int gridScrollTarget, gridScrollActual;
    private SpringValue pageSpring;

    private GradientDrawable glowDrawable;
    private int glowColor = NEUTRAL_GLOW;
    private ValueAnimator glowAnimator;

    private boolean menuOpen;
    private int menuIdx;
    private List<InputsMenu.Entry> menuEntries = new ArrayList<>();

    private boolean stopped = true;
    private long wallpaperStamp = -1;
    private boolean staticWallpaper;
    private long loadedConfigStamp;
    private float density;
    private SharedPreferences prefs;
    private ConnectivityManager connectivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("focus", MODE_PRIVATE);
        connectivity = getSystemService(ConnectivityManager.class);

        root = findViewById(R.id.root);
        page = findViewById(R.id.page);
        band = findViewById(R.id.band);
        glow = findViewById(R.id.glow);
        highlight = findViewById(R.id.highlight);
        sportsPanel = findViewById(R.id.sportsPanel);
        dockTitle = findViewById(R.id.dockTitle);
        wifiName = findViewById(R.id.wifiName);
        dockRow = findViewById(R.id.dockRow);
        grid = findViewById(R.id.grid);
        inputsMenu = findViewById(R.id.inputsMenu);
        wallpaper = findViewById(R.id.wallpaper);
        pillRow = findViewById(R.id.pills);
        recentPanel = findViewById(R.id.recentPanel);
        drawerPanel = findViewById(R.id.drawerPanel);
        pills = new View[] {findViewById(R.id.pillInputs), findViewById(R.id.pillWifi), findViewById(R.id.pillSettings)};
        playingCard = findViewById(R.id.pillNowPlaying);
        albumArt = findViewById(R.id.albumArt);
        playingArtist = findViewById(R.id.playingArtist);
        playingAlbum = findViewById(R.id.playingAlbum);
        playingProgress = findViewById(R.id.playingProgress);
        weatherPanel = findViewById(R.id.weatherPanel);
        radarPanel = findViewById(R.id.radarPanel);
        musicIdle = findViewById(R.id.musicIdle);
        analogClock = findViewById(R.id.analogClock);
        radar = findViewById(R.id.flightRadar);
        dashboard = new DashboardData(this, new DashboardData.Listener() {
            @Override public void weather(DashboardData.Weather value, String status) { updateWeather(value,status); }
            @Override public void flights(List<DashboardData.Flight> flights, String status) { updateFlights(flights,status); }
        });
        flowingBackground = findViewById(R.id.flowingBackground);
        glow.setVisibility(View.GONE);

        sports = new SportsData(localZone(), (events,status) -> {
            sportsEvents=events; sportsStatus=status; sportsPage=0; renderSports();
        });
        ((TextView)findViewById(R.id.sportsHeading)).setTypeface(Fonts.outfit(this,500));
        ((TextClock) findViewById(R.id.clock)).setTypeface(Fonts.outfit(this, 120));
        ((TextClock) findViewById(R.id.clockMinutes)).setTypeface(Fonts.outfit(this, 120));
        ((TextClock) findViewById(R.id.clockPeriod)).setTypeface(Fonts.outfit(this, 400));
        ((TextClock) findViewById(R.id.date)).setTypeface(Fonts.outfit(this, 300));
        dockTitle.setTypeface(Fonts.outfit(this, 250));
        for (int i = PILL_INPUTS; i < pills.length; i++)
            ((TextView) ((LinearLayout) pills[i]).getChildAt(1)).setTypeface(Fonts.outfit(this, 400));
        ((TextView) findViewById(R.id.playingLabel)).setTypeface(Fonts.outfit(this, 400));
        playingArtist.setTypeface(Fonts.outfit(this, 400));
        playingAlbum.setTypeface(Fonts.outfit(this, 400));
        playingSong = findViewById(R.id.playingSong);
        spotify = new SpotifyPlayback(this, this::updateNowPlaying);
        for (int id : new int[] {R.id.sportsStatus,R.id.sportsHint,R.id.weatherSummary,R.id.weatherFeels,R.id.weatherWind,
                R.id.weatherRange,R.id.weatherUpdated,R.id.localPlace,R.id.indiaTime,R.id.sunTimes,
                R.id.flightCallsign,R.id.flightDetails,R.id.flightStatus,R.id.musicIdle})
            ((TextView)findViewById(id)).setTypeface(Fonts.outfit(this,400));
        ((TextView)findViewById(R.id.weatherTemp)).setTypeface(Fonts.outfit(this,200));
        if (dashboard.location != null) {
            text(R.id.localPlace,dashboard.location.name.toUpperCase(Locale.ROOT));
            analogClock.setTimeZone(dashboard.location.timezone);
            for (int id : new int[] {R.id.clock,R.id.clockMinutes,R.id.clockPeriod,R.id.date})
                ((TextClock)findViewById(id)).setTimeZone(dashboard.location.timezone);
        }

        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(480 * density);
        glowDrawable.setGradientCenter(0.5f, 0.5f);
        setGlowColors(glowColor);
        glow.setBackground(glowDrawable);
        glow.setScaleY(0.42f); // squash the circle into a wide ellipse behind the dock

        pageSpring = new SpringValue(240f, 0.92f, v -> page.setTranslationY(-v));

        adapter = new GridAdapter(grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) { gridScrollActual += dy; if (zone == GRID) moveHighlight(); }
        });

        // The Wi-Fi pill changes width with the network name: keep the highlight on it
        pillRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            positionPlayingCard();
            if (zone == TOP) moveHighlight();
        });
        playingCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (zone == NOW_PLAYING) moveHighlight();
            updateFlowingBackground();
        });

        zone = WEATHER;
        dockIdx = prefs.getInt("dockIdx", 0);
        topIdx = clamp(prefs.getInt("topIdx", PILL_INPUTS), PILL_INPUTS, PILL_SETTINGS);
        restoreGridPkg = prefs.getString("gridPkg", null);

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packageReceiver, pkgFilter);

        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopped = false;
        resetDashboard(false);
        flowingBackground.setRunning(!staticWallpaper);
        dashboard.start();
        sports.start();
        main.postDelayed(sportsRotation,10000);
        registerReceiver(spotifyAccessReceiver, new IntentFilter(SpotifyAccessService.ACCESS_CHANGED));
        spotify.start();
        IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, timeFilter);
        connectivity.registerDefaultNetworkCallback(networkCallback);
        updateGreeting();
        updateWifi();
        loadWallpaper();
        if (configStamp() != loadedConfigStamp) loadApps(); // dock.txt / hidden.txt edited
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopped = true;
        flowingBackground.setRunning(false);
        dashboard.stop();
        sports.stop();
        main.removeCallbacks(sportsRotation);
        spotify.stop();
        unregisterReceiver(spotifyAccessReceiver);
        unregisterReceiver(timeReceiver);
        connectivity.unregisterNetworkCallback(networkCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor e = prefs.edit()
                .putInt("zone", zone)
                .putInt("dockIdx", dockIdx)
                .putInt("topIdx", topIdx);
        if (gridIdx < gridApps.size()) e.putString("gridPkg", gridApps.get(gridIdx).packageName);
        e.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dashboard.destroy();
        sports.destroy();
        unregisterReceiver(packageReceiver);
        io.shutdownNow();
    }

    /** Home pressed. From an app: keep the remembered focus. Already home: go back to the dock. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("reload", false)) {
            loadApps();
            wallpaperStamp = -1;
            loadWallpaper();
        }
        resetDashboard(true);
    }

    private void resetDashboard(boolean animate) {
        closeMenu();
        panelMode = PanelNavigation.home(panelMode);
        zone = WEATHER;
        applyFocus(animate);
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        loadedConfigStamp = configStamp();
        Context app = getApplicationContext();
        io.execute(() -> {
            AppLoader.Result result = AppLoader.load(app);
            main.post(() -> onAppsLoaded(result));
        });
    }

    private void onAppsLoaded(AppLoader.Result result) {
        String selected = gridIdx < gridApps.size() ? gridApps.get(gridIdx).packageName : restoreGridPkg;
        restoreGridPkg = null;
        dockApps.clear();
        dockApps.addAll(result.dock);
        dockRow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppInfo app : dockApps) {
            View cell = inflater.inflate(R.layout.item_recent, dockRow, false);
            Tiles.bind(cell, app);
            dockRow.addView(cell);
        }
        gridApps = new ArrayList<>(result.dock);
        gridApps.addAll(result.grid);
        java.text.Collator collator = java.text.Collator.getInstance();
        gridApps.sort((a,b) -> collator.compare(a.label,b.label));
        adapter.setApps(gridApps);
        dockIdx = clamp(dockIdx,0,dockApps.size()-1);
        int found = -1;
        for (int i=0;selected!=null && i<gridApps.size();i++)
            if (selected.equals(gridApps.get(i).packageName)) found=i;
        gridIdx = clamp(found>=0 ? found : gridIdx,0,gridApps.size()-1);
        if ((zone==DOCK && dockApps.isEmpty()) || (zone==GRID && gridApps.isEmpty())) {
            panelMode=PanelNavigation.HOME;
            zone=WEATHER;
        }
        afterLayout(() -> applyFocus(false));
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (!handles(key)) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (menuOpen) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) { resetDashboard(true); return true; }
            menuKey(key);
            return true;
        }
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: left(); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (event.getRepeatCount()==0) right(); break;
            case KeyEvent.KEYCODE_DPAD_UP: up(); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: down(); break;
            case KeyEvent.KEYCODE_BACK: back(); break;
            case KeyEvent.KEYCODE_MENU: openAppInfo(); break;
            default: if (event.getRepeatCount() == 0) activate(); break;
        }
        return true;
    }

    private static boolean handles(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return false;
        }
    }

    private void left() {
        if (panelMode != PanelNavigation.HOME) { resetDashboard(true); return; }
        if (zone == SPORTS) zone = WEATHER;
        else if (zone == RADAR) zone = musicActive ? NOW_PLAYING : WEATHER;
        else if (zone == NOW_PLAYING) zone = WEATHER;
        applyFocus(true);
    }

    private void right() {
        if (panelMode == PanelNavigation.SETTINGS) {
            topIdx = (topIdx+1)%pills.length;
        } else if (panelMode == PanelNavigation.RECENTS) {
            panelMode = PanelNavigation.right(panelMode);
            zone = GRID;
            gridIdx = 0;
        } else if (panelMode == PanelNavigation.DRAWER) {
            return;
        } else if (zone == WEATHER || zone == SPORTS) {
            zone = musicActive ? NOW_PLAYING : RADAR;
        } else if (zone == NOW_PLAYING) {
            zone = RADAR;
        } else {
            panelMode = PanelNavigation.right(panelMode);
            zone = DOCK;
            dockIdx = 0;
        }
        applyFocus(true);
    }

    private void up() {
        if (panelMode==PanelNavigation.HOME && (zone==WEATHER || zone==SPORTS)) {
            if (zone==SPORTS) sportsPage=Math.max(0,sportsPage-1);
            zone=SPORTS; renderSports(); applyFocus(true); return;
        }
        if (panelMode == PanelNavigation.SETTINGS) { resetDashboard(true); return; }
        if (panelMode == PanelNavigation.RECENTS && dockIdx > 0) dockIdx--;
        else if (panelMode == PanelNavigation.DRAWER && gridIdx > 0) gridIdx--;
        else return;
        applyFocus(true);
    }

    private void down() {
        if (panelMode==PanelNavigation.HOME && zone==SPORTS) {
            if (sportsPage+1<sportsPageCount()) { sportsPage++; renderSports(); }
            else zone=WEATHER;
            applyFocus(true); return;
        }
        if (panelMode == PanelNavigation.HOME) {
            panelMode = PanelNavigation.down(panelMode);
            zone = TOP;
            topIdx = 0;
        } else if (panelMode == PanelNavigation.RECENTS && dockIdx+1 < dockApps.size()) {
            dockIdx++;
        } else if (panelMode == PanelNavigation.DRAWER && gridIdx+1 < gridApps.size()) {
            gridIdx++;
        } else return;
        applyFocus(true);
    }

    private void back() { resetDashboard(true); }

    private void activate() {
        if (zone == SPORTS) {
            showSports();
        } else if (zone == WEATHER) {
            showWeather();
        } else if (zone == RADAR) {
            showFlights();
        } else if (zone == NOW_PLAYING) {
            openSpotify();
        } else if (zone == TOP) {
            if (topIdx == PILL_INPUTS) openMenu();
            else if (topIdx == PILL_WIFI) launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            else openSettingsMenu();
        } else {
            AppInfo app = focusedApp();
            if (app != null) launch(app.launchIntent);
        }
    }

    /** Menu button on a tile opens that app's system info page (force stop, clear cache...). */
    private void openAppInfo() {
        AppInfo app = focusedApp();
        if (app == null) return;
        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName, null)));
    }

    private AppInfo focusedApp() {
        if (zone == DOCK && dockIdx < dockApps.size()) return dockApps.get(dockIdx);
        if (zone == GRID && gridIdx < gridApps.size()) return gridApps.get(gridIdx);
        return null;
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Can't open that here", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNowPlaying(SpotifyPlayback.Track track, PendingIntent sessionActivity) {
        boolean showing = track != null;
        musicActive = showing;
        if (showing) {
            String key = track.title + "\n" + track.artist + "\n" + track.album;
            if (paletteArtwork != track.artwork || !key.equals(paletteTrack)) {
                paletteArtwork = track.artwork;
                paletteTrack = key;
                int generation = ++paletteGeneration;
                Bitmap artwork = track.artwork;
                io.execute(() -> {
                    int[] colors;
                    try { colors = ArtworkColors.sample(artwork); }
                    catch (RuntimeException unavailable) { colors = ArtworkColors.dock(0xFF1DB954); }
                    final int[] sampled = colors;
                    main.post(() -> {
                        if (generation != paletteGeneration || isDestroyed()) return;
                        musicColors = sampled;
                        updateFlowingBackground();
                    });
                });
            }
        }
        openSpotify = sessionActivity;
        playingSong.setSong(showing ? track.title : "");
        playingArtist.setText(showing ? track.artist : "");
        playingAlbum.setText(showing ? track.album : "");
        if (showing && track.artwork != null) {
            albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            albumArt.setImageBitmap(track.artwork);
        } else {
            albumArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            albumArt.setImageResource(R.drawable.ic_music_note);
        }
        playingProgress.setPlayback(showing ? track.state : null, showing ? track.duration : 0);
        playingCard.setVisibility(showing ? View.VISIBLE : View.GONE);
        musicIdle.setVisibility(showing ? View.GONE : View.VISIBLE);
        if (!showing && zone == NOW_PLAYING) zone = WEATHER;
        positionPlayingCard();
        updateFlowingBackground();
        afterLayout(() -> applyFocus(false));
    }

    private void updateFlowingBackground() {
        if (flowingBackground == null || staticWallpaper || root.getWidth() == 0 || root.getHeight() == 0) return;
        if (musicActive) {
            float x = playingCard.getLeft() + albumArt.getLeft() + albumArt.getWidth() / 2f;
            float y = playingCard.getTop() + albumArt.getTop() + albumArt.getHeight() / 2f;
            flowingBackground.scene(musicColors, true, x / root.getWidth(), y / root.getHeight());
        } else {
            int color = zone==GRID && !gridApps.isEmpty() ? gridApps.get(gridIdx).glowColor
                    : dockApps.isEmpty() ? NEUTRAL_GLOW : dockApps.get(dockIdx).glowColor;
            float x = panelMode==PanelNavigation.HOME ? 0.5f : 0.92f;
            if (panelMode==PanelNavigation.HOME && dockIdx < dockRow.getChildCount()) {
                View tile = dockRow.getChildAt(dockIdx);
                x = (dockRow.getLeft() + tile.getLeft() + tile.getWidth() / 2f) / root.getWidth();
            }
            flowingBackground.scene(ArtworkColors.dock(color), false, x, 0.80f);
        }
    }

    private void positionPlayingCard() {
        // Dashboard card has a stable position beneath the clocks.
    }

    private void openSpotify() {
        if (openSpotify != null) {
            try { openSpotify.send(); return; }
            catch (PendingIntent.CanceledException ignored) { /* App restarted. */ }
        }
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(SpotifyPlayback.PACKAGE);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(SpotifyPlayback.PACKAGE);
        if (intent != null) launch(intent);
    }

    // ---------------------------------------------------------------- focus visuals

    private void applyFocus(boolean animate) {
        setSidePanel(recentPanel,panelMode==PanelNavigation.RECENTS,animate,false);
        setSidePanel(drawerPanel,panelMode==PanelNavigation.DRAWER,animate,false);
        setSidePanel(pillRow,panelMode==PanelNavigation.SETTINGS,animate,true);
        for (int i=0;i<dockRow.getChildCount();i++)
            Tiles.setFocused(dockRow.getChildAt(i),zone==DOCK && i==dockIdx,animate);
        adapter.setFocused(zone==GRID ? gridIdx : RecyclerView.NO_POSITION);
        for (int i=0;i<pills.length;i++) {
            float scale=zone==TOP && i==topIdx ? 1.06f : 1f;
            pills[i].animate().scaleX(scale).scaleY(scale).setDuration(animate?180:0).start();
        }
        // Avoid fragments of the music text peeking around the settings capsule.
        musicIdle.setAlpha(panelMode==PanelNavigation.SETTINGS?0f:1f);
        playingCard.setAlpha(panelMode==PanelNavigation.SETTINGS?0f:1f);
        playingCard.setScaleX(1);
        playingCard.setScaleY(1);
        pageSpring.snapTo(0);
        float contentAlpha=panelMode==PanelNavigation.DRAWER?0.16f:panelMode==PanelNavigation.RECENTS?0.42f:1f;
        page.animate().alpha(contentAlpha).setDuration(animate?220:0).start();
        if (zone==GRID) {
            GridLayoutManager manager=(GridLayoutManager)grid.getLayoutManager();
            View cell=manager.findViewByPosition(gridIdx);
            boolean visible=cell!=null && manager.getDecoratedTop(cell)>=grid.getPaddingTop()
                    && manager.getDecoratedBottom(cell)<=grid.getHeight()-grid.getPaddingBottom();
            if (!visible) {
                if (animate) grid.smoothScrollToPosition(gridIdx);
                else manager.scrollToPositionWithOffset(gridIdx,grid.getPaddingTop());
            }
        }
        updateDockTitle(animate);
        root.post(this::moveHighlight);
    }

    private void setSidePanel(View panel, boolean show, boolean animate, boolean bottom) {
        if (Boolean.valueOf(show).equals(panel.getTag())) return;
        panel.setTag(show);
        panel.animate().cancel();
        float distance=bottom ? Math.max(panel.getHeight(),dp(64))+dp(30)
                : Math.max(panel.getWidth(),panel.getLayoutParams().width)+dp(16);
        panel.setVisibility(View.VISIBLE);
        if (!animate) {
            if (bottom) panel.setTranslationY(show?0:distance);
            else panel.setTranslationX(show?0:distance);
            panel.setAlpha(show?1:0);
            panel.setVisibility(show?View.VISIBLE:View.INVISIBLE);
            return;
        }
        highlight.setAlpha(0);
        android.view.ViewPropertyAnimator motion=panel.animate().alpha(show?1:0).setDuration(260).setInterpolator(ease);
        if (bottom) motion.translationY(show?0:distance); else motion.translationX(show?0:distance);
        motion.withEndAction(() -> {
            panel.setVisibility(show?View.VISIBLE:View.INVISIBLE);
            moveHighlight();
        }).start();
    }

    private void moveHighlight() {
        View target;
        boolean tile=false;
        if (root.getWidth()==0) return;
        // Dashboard cards stay borderless; their headings indicate remote focus.

        findViewById(R.id.flightCallsign).setAlpha(zone==RADAR?1f:0.82f);
        if (zone==WEATHER || zone==RADAR) { highlight.setAlpha(0); return; }
        if (zone==SPORTS) target=sportsPanel;
        else if (zone==WEATHER) target=weatherPanel;
        else if (zone==RADAR) target=radarPanel;
        else if (zone==NOW_PLAYING) target=playingCard;
        else if (zone==TOP) target=pills[topIdx];
        else if (zone==DOCK && dockIdx<dockRow.getChildCount()) {
            target=dockRow.getChildAt(dockIdx).findViewById(R.id.tile);
            tile=true;
        } else if (zone==GRID) {
            RecyclerView.ViewHolder cell=grid.findViewHolderForAdapterPosition(gridIdx);
            if (cell==null) { highlight.setAlpha(0); return; }
            target=cell.itemView.findViewById(R.id.tile);
            tile=true;
        } else return;
        if (target.getWidth()==0) return;
        Rect bounds=new Rect();
        target.getDrawingRect(bounds);
        ((ViewGroup)root).offsetDescendantRectToMyCoords(target,bounds);
        float padding=dp(tile?4:3);
        // offsetDescendantRectToMyCoords already includes tile scale and sidebar translations.
        highlight.moveTo(bounds.left-padding,bounds.top-padding,bounds.width()+padding*2,
                bounds.height()+padding*2,dp(tile?12:20));
        highlight.setAlpha(1);
    }

    private void updateDockTitle(boolean animate) {
        updateFlowingBackground();
        // Full in the dock, dimmed under the top bar, gone once the page slides up
        float alpha = zone == DOCK ? 1f : zone == TOP ? 0.4f : 0f;
        glow.animate().alpha(zone == DOCK ? 1f : 0.5f).setDuration(animate ? 300 : 0).start();
        if (dockApps.isEmpty()) {
            dockTitle.setText("");
            return;
        }
        AppInfo app = dockApps.get(dockIdx);
        dockTitle.animate().cancel();
        if (!app.label.contentEquals(dockTitle.getText())) {
            dockTitle.setText(app.label);
            if (animate) {
                dockTitle.setAlpha(0f);
                dockTitle.setTranslationX(dp(10));
            }
            animateGlow(app.glowColor, animate);
        }
        if (animate) {
            dockTitle.animate().alpha(alpha).translationX(0).setDuration(260).setInterpolator(ease).start();
        } else {
            dockTitle.setAlpha(alpha);
            dockTitle.setTranslationX(0);
        }
    }

    private void animateGlow(int color, boolean animate) {
        if (glowAnimator != null) glowAnimator.cancel();
        if (!animate) {
            glowColor = color;
            setGlowColors(color);
            return;
        }
        glowAnimator = ValueAnimator.ofArgb(glowColor, color);
        glowAnimator.setDuration(500);
        glowAnimator.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            setGlowColors(glowColor);
        });
        glowAnimator.start();
    }

    private void setGlowColors(int c) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        glowDrawable.setColors(new int[] {Color.argb(120, r, g, b), Color.argb(46, r, g, b), Color.argb(0, r, g, b)});
    }

    // ---------------------------------------------------------------- inputs menu

    private void openMenu() {
        showMenu(InputsMenu.load(this));
    }

    private void openSettingsMenu() {
        List<InputsMenu.Entry> entries = new ArrayList<>();
        String[][] pages = {
                {"My Fire TV", "com.amazon.device.settings.action.DEVICE"},
                {"Preferences", "com.amazon.device.settings.action.PREFERENCES"},
                {"Display & Sounds", Settings.ACTION_DISPLAY_SETTINGS},
                {"Applications", "com.amazon.device.settings.action.APPLICATIONS"},
                {"Controllers & Bluetooth", "android.settings.CONTROLLERS_SETTINGS"},
                {"Accessibility", "com.amazon.device.settings.action.ACCESSIBILITY_CLOSED_CAPTION"}
        };
        for (String[] p : pages) {
            Intent intent = new Intent(p[1]).setPackage("com.amazon.tv.settings.v2");
            if (intent.resolveActivity(getPackageManager()) != null) {
                entries.add(new InputsMenu.Entry(p[0], intent));
            }
        }
        showMenu(entries);
    }

    private void showMenu(List<InputsMenu.Entry> entries) {
        menuEntries = entries;
        inputsMenu.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        menuIdx = -1;
        for (int i = 0; i < menuEntries.size(); i++) {
            InputsMenu.Entry entry = menuEntries.get(i);
            TextView row = (TextView) inflater.inflate(R.layout.item_input, inputsMenu, false);
            row.setText(entry.label);
            row.setTypeface(Fonts.outfit(this, 400));
            if (entry.intent == null) row.setAlpha(0.55f);
            else if (menuIdx < 0) menuIdx = i;
            inputsMenu.addView(row);
        }
        updateMenuSelection();

        inputsMenu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        View pill = pills[topIdx];
        float x = pillRow.getLeft() + pill.getLeft();
        x = Math.min(x, root.getWidth() - inputsMenu.getMeasuredWidth() - dp(24));
        float y = pillRow.getTop() + pill.getBottom() + dp(12);
        if (y+inputsMenu.getMeasuredHeight()>root.getHeight()-dp(16))
            y=Math.max(dp(16),pillRow.getTop()-inputsMenu.getMeasuredHeight()-dp(12));
        inputsMenu.setX(x);
        inputsMenu.setY(y - dp(8));
        inputsMenu.setAlpha(0f);
        inputsMenu.setVisibility(View.VISIBLE);
        inputsMenu.animate().alpha(1f).y(y).setDuration(180).setInterpolator(ease).start();
        menuOpen = true;
    }

    private void closeMenu() {
        if (!menuOpen) return;
        menuOpen = false;
        inputsMenu.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> inputsMenu.setVisibility(View.GONE)).start();
    }

    private void menuKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                for (int i = menuIdx - 1; i >= 0; i--) if (menuEntries.get(i).intent != null) { menuIdx = i; break; }
                updateMenuSelection();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                for (int i = menuIdx + 1; i < menuEntries.size(); i++) if (menuEntries.get(i).intent != null) { menuIdx = i; break; }
                updateMenuSelection();
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (menuIdx >= 0) {
                    closeMenu();
                    launch(menuEntries.get(menuIdx).intent);
                }
                break;
            default: // Back, Left, Right, Menu close it
                closeMenu();
        }
    }

    private void updateMenuSelection() {
        for (int i = 0; i < inputsMenu.getChildCount(); i++) {
            inputsMenu.getChildAt(i).setBackgroundResource(i == menuIdx ? R.drawable.menu_row_selected : 0);
        }
    }

    // ---------------------------------------------------------------- clock, greeting, wifi, wallpaper

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateGreeting(); }
    };

    private void updateGreeting() {
        String zone = localZone();
        text(R.id.musicIdle,SportsTime.greeting(System.currentTimeMillis(),zone));
        analogClock.invalidate();
        text(R.id.indiaTime,DashboardTime.indiaLine(System.currentTimeMillis(),zone));
    }

    private String localZone() {
        return dashboard.location==null?TimeZone.getDefault().getID():dashboard.location.timezone;
    }
    private int sportsPageCount() { return Math.max(1,(sportsEvents.size()+SPORTS_PAGE_SIZE-1)/SPORTS_PAGE_SIZE); }
    private void renderSports() {
        LinearLayout rows=findViewById(R.id.sportsRows);
        rows.removeAllViews();
        sportsPage=Math.min(sportsPage,sportsPageCount()-1);
        if (sportsEvents.isEmpty()) {
            TextView empty=new TextView(this);
            empty.setText(sportsStatus.contains("Loading")?"Loading today's fixtures…":
                    sportsStatus.contains("Unavailable")?"Schedule partly unavailable":"No events today");
            empty.setTextColor(0xCCFFFFFF); empty.setTextSize(15); empty.setTypeface(Fonts.outfit(this,400));
            rows.addView(empty);
        }
        int end=Math.min(sportsEvents.size(),(sportsPage+1)*SPORTS_PAGE_SIZE);
        for (int i=sportsPage*SPORTS_PAGE_SIZE;i<end;i++) {
            SportsData.Event event=sportsEvents.get(i);
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            rows.addView(row,new LinearLayout.LayoutParams(-1,(int)dp(45)));
            LinearLayout headline=new LinearLayout(this);
            headline.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.addView(headline,new LinearLayout.LayoutParams(-1,-2));
            TextView title=new TextView(this); title.setText(event.title); title.setTextColor(0xF2FFFFFF);
            title.setTextSize(13); title.setTypeface(Fonts.outfit(this,400)); title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            headline.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
            if (!event.score.isEmpty()) {
                TextView score=new TextView(this); score.setText(event.score); score.setTextSize(15);
                score.setTextColor(Color.WHITE); score.setTypeface(Fonts.outfit(this,600));
                score.setPadding((int)dp(8),0,0,0);
                headline.addView(score,new LinearLayout.LayoutParams(-2,-2));
            }
            TextView time=new TextView(this); time.setText(event.timing(localZone())); time.setTextSize(10);
            time.setTextColor(0xB3FFFFFF); time.setTypeface(Fonts.outfit(this,400));
            row.addView(time,new LinearLayout.LayoutParams(-1,-2));
        }
        text(R.id.sportsStatus,sportsStatus);
        text(R.id.sportsHint,(zone==SPORTS?"Up/Down · browse":"Up · browse")+"   Select · all"
                +(sportsPageCount()>1?"   "+(sportsPage+1)+"/"+sportsPageCount():""));
    }
    private void showSports() {
        StringBuilder details=new StringBuilder("ISL · Premier League · Champions League · F1\nStart times in "+localZone()+"\n\n");
        for (SportsData.Event event:sportsEvents)
            details.append(event.title).append(event.score.isEmpty()?"":"   "+event.score)
                    .append('\n').append(event.timing(localZone())).append("\n\n");
        if (sportsEvents.isEmpty()) details.append("No events returned for today.\n\n");
        details.append(sportsStatus).append("\nPublic ESPN feeds; schedule and coverage may change.");
        TextView body=new TextView(this); body.setText(details.toString()); body.setTextSize(17);
        body.setTextColor(Color.WHITE); body.setPadding((int)dp(22),(int)dp(12),(int)dp(22),(int)dp(12));
        android.widget.ScrollView scroll=new android.widget.ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Today's sport").setView(scroll).setPositiveButton("Close",null).show();
    }

    private void text(int id, String value) { ((TextView)findViewById(id)).setText(value); }

    private void updateWeather(DashboardData.Weather weather, String status) {
        latestWeather = weather;
        weatherStatus = status;
        text(R.id.weatherUpdated,status);
        if (weather == null) {
            text(R.id.weatherTemp,"--°"); text(R.id.weatherSummary,"Weather unavailable");
            text(R.id.weatherFeels,""); text(R.id.weatherWind,""); text(R.id.weatherRange,"");
            text(R.id.sunTimes,"Sunrise --:--   ·   Sunset --:--");
            return;
        }
        text(R.id.weatherTemp,String.format(Locale.ROOT,"%.0f°",weather.temperature));
        text(R.id.weatherSummary,DashboardData.condition(weather.code));
        text(R.id.weatherFeels,String.format(Locale.ROOT,"Feels like %.0f° · Humidity %.0f%%",weather.feelsLike,weather.humidity));
        text(R.id.weatherWind,String.format(Locale.ROOT,"Wind %.0f km/h",weather.wind));
        text(R.id.weatherRange,String.format(Locale.ROOT,"High %.0f°   ·   Low %.0f°",weather.high,weather.low));
        text(R.id.sunTimes,"Sunrise " + weather.sunrise + "   ·   Sunset " + weather.sunset);
    }

    private void updateFlights(List<DashboardData.Flight> flights, String status) {
        nearbyFlights = flights;
        flightStatus = status;
        radar.setFlights(flights);
        text(R.id.flightStatus,status);
        if (flights.isEmpty()) {
            text(R.id.flightCallsign,status.contains("unavailable") ? "Unavailable" : "No aircraft");
            text(R.id.flightDetails,"Within 50 km\nCoverage varies");
        } else {
            DashboardData.Flight first=flights.get(0);
            text(R.id.flightCallsign,first.callsign.isEmpty() ? first.type : first.callsign);
            text(R.id.flightDetails,String.format(Locale.ROOT,"%s · %.1f km\n%,d ft",first.type,first.distance,first.altitude));
        }
    }
    private void showFlights() {
        String location=dashboard.location==null ? "Nearby" : dashboard.location.name;
        StringBuilder details=new StringBuilder(flightStatus+"\n\n");
        for (DashboardData.Flight flight : nearbyFlights.subList(0,Math.min(8,nearbyFlights.size())))
            details.append(flight.summary()).append("\n\n");
        details.append("Community ADS-B positions via ADSB.lol (ODbL). Coverage and updates vary.");
        new AlertDialog.Builder(this).setTitle("Overhead · "+location).setMessage(details.toString())
                .setPositiveButton("Close",null).show();
    }
    private void showWeather() {
        String details=weatherStatus;
        if (latestWeather!=null) details=String.format(Locale.ROOT,
                "%s\nTemperature %.1f°C · feels like %.1f°C\nHumidity %.0f%% · wind %.0f km/h\nHigh %.1f°C · low %.1f°C\nSunrise %s · sunset %s\n\nWeather forecast data: Open-Meteo (CC BY 4.0).",
                DashboardData.condition(latestWeather.code),latestWeather.temperature,latestWeather.feelsLike,
                latestWeather.humidity,latestWeather.wind,latestWeather.high,latestWeather.low,latestWeather.sunrise,latestWeather.sunset);
        new AlertDialog.Builder(this).setTitle("Weather").setMessage(details).setPositiveButton("Close",null).show();
    }

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { main.post(HomeActivity.this::updateWifi); }
        @Override public void onLost(Network n) { main.post(HomeActivity.this::updateWifi); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { main.post(HomeActivity.this::updateWifi); }
    };

    private void updateWifi() {
        Network network = connectivity.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
        String text;
        if (caps == null) {
            text = "Offline";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            text = "Ethernet";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            text = "Wi-Fi"; // Connection status without location access or exposing the SSID.
        } else {
            text = "Connected";
        }
        if (!text.contentEquals(wifiName.getText())) wifiName.setText(text);
    }

    /** Optional wallpaper: wallpaper.jpg or wallpaper.png in the app's files dir (see configure.sh). */
    private void loadWallpaper() {
        File file = null;
        for (String name : new String[] {"wallpaper.jpg", "wallpaper.png"}) {
            File f = new File(getFilesDir(), name);
            if (f.isFile()) { file = f; break; }
        }
        long stamp = file == null ? 0 : file.lastModified() + file.length();
        if (stamp == wallpaperStamp) return;
        wallpaperStamp = stamp;
        if (file == null) {
            wallpaper.setImageDrawable(null);
            wallpaper.setVisibility(View.GONE);
            staticWallpaper=false;
            flowingBackground.setVisibility(View.VISIBLE);
            flowingBackground.setRunning(!stopped);
            updateFlowingBackground();
            return;
        }
        staticWallpaper=true;
        flowingBackground.setRunning(false);
        flowingBackground.setVisibility(View.GONE);
        String path = file.getAbsolutePath();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        io.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= screenW) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory, fine for a dimmed photo
            Bitmap bmp = BitmapFactory.decodeFile(path, o);
            main.post(() -> {
                wallpaper.setImageBitmap(bmp);
                wallpaper.setColorFilter(Color.argb(153, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
                if (isDestroyed()) return;
                wallpaper.setVisibility(bmp == null ? View.GONE : View.VISIBLE);
                staticWallpaper=bmp!=null;
                flowingBackground.setVisibility(staticWallpaper?View.GONE:View.VISIBLE);
                flowingBackground.setRunning(!staticWallpaper && !stopped);
            });
        });
    }

    // ---------------------------------------------------------------- helpers

    /** Changes whenever dock.txt or hidden.txt is added, edited or removed. */
    private long configStamp() {
        long stamp = 0;
        for (String name : new String[] {"dock.txt", "hidden.txt"}) {
            File f = new File(getFilesDir(), name);
            if (f.isFile()) stamp = stamp * 31 + f.lastModified() + f.length();
        }
        return stamp;
    }

    private void afterLayout(Runnable r) {
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
        root.requestLayout();
    }

    private int rowHeight() { return Math.round(dp(144)); }

    private int maxGridScroll() {
        int rows = (gridApps.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows * rowHeight() + grid.getPaddingBottom() - grid.getHeight());
    }

    private float dp(float v) { return v * density; }

    private static int clamp(int v, int min, int max) { return max < min ? min : Math.max(min, Math.min(max, v)); }
}
