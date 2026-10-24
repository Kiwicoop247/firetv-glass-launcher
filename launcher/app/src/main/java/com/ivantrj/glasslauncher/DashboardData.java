package com.ivantrj.glasslauncher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional public feeds. Requests run only while the dashboard is in the foreground. */
final class DashboardData {
    static final class Location {
        final String name, timezone;
        final double latitude, longitude;
        Location(JSONObject json) throws Exception {
            name = json.getString("name");
            timezone = json.getString("timezone");
            latitude = json.getDouble("latitude");
            longitude = json.getDouble("longitude");
            if (name.length() > 80 || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                    || !Double.isFinite(latitude) || !Double.isFinite(longitude)) throw new IOException("Invalid location");
            if (!java.util.Arrays.asList(java.util.TimeZone.getAvailableIDs()).contains(timezone)) throw new IOException("Invalid timezone");
        }
    }
    static final class Weather {
        final double temperature, feelsLike, wind, humidity, high, low;
        final int code;
        final String sunrise, sunset, observed;
        Weather(JSONObject json) throws Exception {
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            temperature = current.getDouble("temperature_2m");
            feelsLike = current.getDouble("apparent_temperature");
            humidity = current.getDouble("relative_humidity_2m");
            wind = current.getDouble("wind_speed_10m");
            code = current.getInt("weather_code");
            observed = clock(current.getString("time"));
            high = daily.getJSONArray("temperature_2m_max").getDouble(0);
            low = daily.getJSONArray("temperature_2m_min").getDouble(0);
            sunrise = clock(daily.getJSONArray("sunrise").optString(0));
            sunset = clock(daily.getJSONArray("sunset").optString(0));
        }
        private static String clock(String iso) { return iso.length() >= 16 ? iso.substring(11,16) : "--:--"; }
    }
    static final class Flight {
        final String callsign, type, registration;
        final double distance, east, north, track;
        final int altitude;
        Flight(JSONObject json, Location location) throws Exception {
            callsign = json.optString("flight", json.optString("hex", "Unknown")).trim();
            type = json.optString("t", "Aircraft");
            registration = json.optString("r", "");
            double lat = json.getDouble("lat"), lon = json.getDouble("lon");
            double lat1 = Math.toRadians(location.latitude), lat2 = Math.toRadians(lat);
            double dlat = lat2-lat1, dlon = Math.toRadians(lon-location.longitude);
            double a = Math.sin(dlat/2)*Math.sin(dlat/2) + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);
            distance = 6371*2*Math.asin(Math.sqrt(Math.min(1,a)));
            double bearing = Math.atan2(Math.sin(dlon)*Math.cos(lat2), Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dlon));
            east = distance*Math.sin(bearing);
            north = distance*Math.cos(bearing);
            track = json.optDouble("track", 0);
            altitude = json.optInt("alt_baro", json.optInt("alt_geom", 0));
        }
        String summary() {
            return String.format(Locale.ROOT, "%s · %s\n%.1f km · %,d ft", callsign, type, distance, altitude);
        }
    }
    interface Listener {
        void weather(Weather weather, String status);
        void flights(List<Flight> flights, String status);
    }
    final Location location;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private boolean running, weatherBusy, flightsBusy;
    private long nextWeather, nextFlights;
    private int generation;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || location == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now >= nextWeather && !weatherBusy) fetchWeather();
            if (now >= nextFlights && !flightsBusy) fetchFlights();
            main.postDelayed(this, 1000);
        }
    };
    DashboardData(Context context, Listener listener) {
        this.listener = listener;
        Location loaded;
        try {
            File file = new File(context.getFilesDir(), "dashboard-location.json");
            if (file.length() > 4096) throw new IOException("Location file too large");
            loaded = new Location(new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
        } catch (Exception missing) { loaded = null; }
        location = loaded;
    }
    void start() {
        running = true;
        if (location == null) {
            listener.weather(null, "Location not configured");
            listener.flights(new ArrayList<>(), "Location not configured");
        } else main.post(tick);
    }
    void stop() {
        running = false;
        generation++;
        weatherBusy = false;
        flightsBusy = false;
        main.removeCallbacks(tick);
    }
    void destroy() { stop(); workers.shutdownNow(); }
    private void fetchWeather() {
        weatherBusy = true;
        nextWeather = android.os.SystemClock.elapsedRealtime()+600000;
        int request = generation;
        workers.execute(() -> {
            Weather result = null;
            String status = "Weather unavailable";
            try {
                String url = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=1&timezone=%s",
                        location.latitude, location.longitude, URLEncoder.encode(location.timezone,"UTF-8"));
                result = new Weather(read(url));
                status = "Open-Meteo · " + result.observed;
            } catch (Exception unavailable) { /* Keep the UI honest when the feed is unavailable. */ }
            Weather weather = result;
            String message = status;
            main.post(() -> {
                if (request != generation) return;
                weatherBusy = false;
                if (!running) return;
                if (weather == null) nextWeather = android.os.SystemClock.elapsedRealtime()+60000;
                listener.weather(weather, message);
            });
        });
    }
    private void fetchFlights() {
        flightsBusy = true;
        nextFlights = android.os.SystemClock.elapsedRealtime()+30000;
        int request = generation;
        workers.execute(() -> {
            List<Flight> flights = new ArrayList<>();
            String status;
            try {
                String url = String.format(Locale.ROOT, "https://api.adsb.lol/v2/point/%.5f/%.5f/27", location.latitude, location.longitude);
                JSONArray aircraft = read(url).getJSONArray("ac");
                for (int i=0; i<aircraft.length(); i++) {
                    JSONObject item = aircraft.getJSONObject(i);
                    if (!item.has("lat") || !item.has("lon") || item.optDouble("seen_pos", 999) > 90) continue;
                    Flight flight = new Flight(item, location);
                    if (flight.altitude > 0 && flight.distance <= 50 && Double.isFinite(flight.distance)) flights.add(flight);
                }
                flights.sort(Comparator.comparingDouble(f -> f.distance));
                status = flights.size() + " aircraft · 50 km";
            } catch (Exception unavailable) {
                flights.clear();
                status = "Feed unavailable · retrying";
            }
            String message = status;
            main.post(() -> {
                if (request != generation) return;
                flightsBusy = false;
                if (running) listener.flights(flights, message);
            });
        });
    }
    static JSONObject read(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "GlassOS7-PersonalDashboard/1.0");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Feed not available");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count=input.read(buffer)) != -1) {
                    if (output.size()+count > 524288) throw new IOException("Feed too large");
                    output.write(buffer,0,count);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    static String condition(int code) {
        if (code == 0) return "Clear sky";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "Weather";
    }
}
