package com.pocketai.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * EDITH Cab Fares. Nothing here invents a fare.
 *  - Distance and travel time come from OpenStreetMap (Nominatim + OSRM). They are NOT fares.
 *  - Every fare is typed in by the user from a provider's own app (SRC_MANUAL) and is stored
 *    with its date, whether it is a quote or an estimate, and any extra fees.
 * No free, permitted automatic fare source exists for Uber/Ola/Rapido without approved credentials.
 */
public final class EdithCabs {

    public static final String KIND_QUOTE = "QUOTE";
    public static final String KIND_ESTIMATE = "ESTIMATE";
    public static final String SRC_MANUAL = "MANUAL";
    public static final long STALE_MS = 24L * 60 * 60 * 1000;

    private static final String PREFS = "edith_cabs";
    private static final String KEY = "routes";
    private static final int MAX_OBS = 1000;
    private static final String UA = "EDITH-Android/1.0 (personal assistant app)";
    private static final String CHANNEL_ID = "edith_price_alerts";

    private EdithCabs() {}

    // ------------------------------------------------------------------
    // Distance and time (OpenStreetMap). Blocking: call off the main thread.
    // ------------------------------------------------------------------
    public static final class Place {
        public double lat, lon;
        public String label = "";
    }

    public static final class Trip {
        public double km, minutes;
    }

    public static final class Lookup {
        public Place from, to;
        public Trip trip;
        public String error; // null when everything worked
    }

    public static Lookup lookup(String pickup, String drop) {
        Lookup r = new Lookup();
        try {
            r.from = geocode(pickup);
            if (r.from == null) {
                r.error = "Couldn't find \"" + pickup + "\" on the map. Try adding the area or city.";
                return r;
            }
            Thread.sleep(1100); // Nominatim's policy: at most one request per second
            r.to = geocode(drop);
            if (r.to == null) {
                r.error = "Couldn't find \"" + drop + "\" on the map. Try adding the area or city.";
                return r;
            }
            r.trip = route(r.from, r.to);
            if (r.trip == null) r.error = "No driving route was found between those places.";
        } catch (Exception e) {
            r.error = "Couldn't reach the map service ("
                    + (e.getMessage() == null ? "network error" : e.getMessage()) + ").";
        }
        return r;
    }

    private static Place geocode(String query) throws Exception {
        String q = URLEncoder.encode(query, "UTF-8");
        String body = httpGet("https://nominatim.openstreetmap.org/search?format=json&limit=1"
                + "&countrycodes=in&q=" + q);
        JSONArray arr = new JSONArray(body);
        if (arr.length() == 0) return null;
        JSONObject o = arr.getJSONObject(0);
        Place p = new Place();
        p.lat = Double.parseDouble(o.getString("lat"));
        p.lon = Double.parseDouble(o.getString("lon"));
        String label = o.optString("display_name", query);
        p.label = label.length() > 90 ? label.substring(0, 90) + "…" : label;
        return p;
    }

    private static Trip route(Place a, Place b) throws Exception {
        String url = "https://router.project-osrm.org/route/v1/driving/"
                + a.lon + "," + a.lat + ";" + b.lon + "," + b.lat + "?overview=false";
        JSONObject o = new JSONObject(httpGet(url));
        if (!"Ok".equals(o.optString("code"))) return null;
        JSONObject r = o.getJSONArray("routes").getJSONObject(0);
        Trip t = new Trip();
        t.km = r.getDouble("distance") / 1000.0;
        t.minutes = r.getDouble("duration") / 60.0;
        return t;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 500000) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ------------------------------------------------------------------
    // Storage (SharedPreferences, JSON)
    // route: {id, pickup, drop, when, pax, km?, min?, pickupMatch?, dropMatch?, target?,
    //         obs:[ {t, prov, cat, p, pmax?, fee, cur, kind, src, ride} ]}
    // ------------------------------------------------------------------
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONArray load(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(Context ctx, JSONArray all) {
        prefs(ctx).edit().putString(KEY, all.toString()).apply();
    }

    private static JSONObject findIn(JSONArray all, String id) {
        if (id == null) return null;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    public static JSONObject findRoute(Context ctx, String id) {
        return findIn(load(ctx), id);
    }

    /** Saves (or updates) a route. res may be null when the map lookup failed. Returns the route id. */
    public static synchronized String saveRoute(Context ctx, String pickup, String drop, Lookup res,
                                                String when, String pax) throws Exception {
        JSONArray all = load(ctx);
        JSONObject route = null;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && pickup.equalsIgnoreCase(o.optString("pickup"))
                    && drop.equalsIgnoreCase(o.optString("drop"))) {
                route = o;
                break;
            }
        }
        if (route == null) {
            route = new JSONObject();
            route.put("id", UUID.randomUUID().toString());
            route.put("pickup", pickup);
            route.put("drop", drop);
            route.put("obs", new JSONArray());
            all.put(route);
        }
        route.put("when", when == null ? "" : when);
        route.put("pax", pax == null ? "1" : pax);
        if (res != null && res.error == null && res.trip != null) {
            route.put("km", res.trip.km);
            route.put("min", res.trip.minutes);
            route.put("pickupMatch", res.from.label);
            route.put("dropMatch", res.to.label);
        }
        save(ctx, all);
        return route.getString("id");
    }

    public static synchronized void deleteRoute(Context ctx, String routeId) {
        JSONArray all = load(ctx);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && !routeId.equals(o.optString("id"))) kept.put(o);
        }
        save(ctx, kept);
    }

    public static synchronized JSONObject recordFare(Context ctx, String routeId, String provider,
                                                     String category, double fare, double fareMax,
                                                     double fees, String kind, String ride) throws Exception {
        JSONArray all = load(ctx);
        JSONObject route = findIn(all, routeId);
        if (route == null) throw new Exception("That route no longer exists.");
        JSONArray obs = route.getJSONArray("obs");
        JSONObject e = new JSONObject();
        e.put("t", System.currentTimeMillis());
        e.put("prov", provider);
        e.put("cat", category);
        e.put("p", fare);
        if (fareMax > fare) e.put("pmax", fareMax);
        e.put("fee", fees);
        e.put("cur", "INR");
        e.put("kind", kind);
        e.put("src", SRC_MANUAL);
        e.put("ride", ride == null ? "" : ride);
        obs.put(e);
        while (obs.length() > MAX_OBS) obs.remove(0);
        save(ctx, all);
        return e;
    }

    public static synchronized void deleteOption(Context ctx, String routeId, String provider, String category) {
        JSONArray all = load(ctx);
        JSONObject route = findIn(all, routeId);
        if (route == null) return;
        JSONArray obs = route.optJSONArray("obs");
        JSONArray kept = new JSONArray();
        for (int i = 0; obs != null && i < obs.length(); i++) {
            JSONObject o = obs.optJSONObject(i);
            if (o == null) continue;
            if (!(provider.equalsIgnoreCase(o.optString("prov")) && category.equalsIgnoreCase(o.optString("cat")))) {
                kept.put(o);
            }
        }
        try {
            route.put("obs", kept);
        } catch (Exception ignored) {
            return;
        }
        save(ctx, all);
    }

    public static double targetOf(JSONObject route) {
        return route == null ? 0 : route.optDouble("target", 0);
    }

    /** Sets (or clears, when target <= 0) the "tell me at or below this total" amount. */
    public static synchronized void setTarget(Context ctx, String routeId, double target) {
        JSONArray all = load(ctx);
        JSONObject route = findIn(all, routeId);
        if (route == null) return;
        try {
            if (target > 0) route.put("target", target);
            else route.remove("target");
        } catch (Exception ignored) {
            return;
        }
        save(ctx, all);
    }

    // ------------------------------------------------------------------
    // Comparison and history
    // ------------------------------------------------------------------
    public static final class Option {
        public String provider, category;
        public JSONObject latest;
        public double total;
    }

    public static double totalOf(JSONObject obs) {
        return obs.optDouble("p") + obs.optDouble("fee", 0);
    }

    /** Latest entry per provider + category, cheapest total first. */
    public static List<Option> options(JSONObject route) {
        Map<String, JSONObject> latest = new LinkedHashMap<>();
        JSONArray obs = route.optJSONArray("obs");
        for (int i = 0; obs != null && i < obs.length(); i++) {
            JSONObject o = obs.optJSONObject(i);
            if (o == null) continue;
            latest.put(o.optString("prov").toLowerCase(Locale.ROOT) + "|"
                    + o.optString("cat").toLowerCase(Locale.ROOT), o);
        }
        List<Option> out = new ArrayList<>();
        for (JSONObject o : latest.values()) {
            Option op = new Option();
            op.provider = o.optString("prov");
            op.category = o.optString("cat");
            op.latest = o;
            op.total = totalOf(o);
            out.add(op);
        }
        Collections.sort(out, (a, b) -> Double.compare(a.total, b.total));
        return out;
    }

    public static List<JSONObject> history(JSONObject route, String provider, String category) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray obs = route.optJSONArray("obs");
        for (int i = 0; obs != null && i < obs.length(); i++) {
            JSONObject o = obs.optJSONObject(i);
            if (o != null && provider.equalsIgnoreCase(o.optString("prov"))
                    && category.equalsIgnoreCase(o.optString("cat"))) out.add(o);
        }
        return out;
    }

    public static boolean isStale(JSONObject obs) {
        return System.currentTimeMillis() - obs.optLong("t") > STALE_MS;
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------
    private static String when(long t) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date(t));
    }

    public static String ageText(long t) {
        long d = Math.max(0, System.currentTimeMillis() - t);
        long min = d / 60000;
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 48) return h + " h ago";
        return (h / 24) + " days ago";
    }

    public static String kindLabel(String kind) {
        return KIND_QUOTE.equals(kind) ? "quote" : "estimate";
    }

    public static String fareText(JSONObject o) {
        String s = EdithShopping.money(o.optDouble("p"));
        double max = o.optDouble("pmax", 0);
        if (max > 0) s += "\u2013" + EdithShopping.money(max);
        double fee = o.optDouble("fee", 0);
        if (fee > 0) s += " + " + EdithShopping.money(fee) + " fees";
        return s;
    }

    public static String describe(JSONObject o) {
        StringBuilder sb = new StringBuilder();
        sb.append(fareText(o)).append("  \u00B7  ").append(kindLabel(o.optString("kind"))).append("\n");
        sb.append("Entered by you  \u00B7  ").append(when(o.optLong("t")))
                .append(" (").append(ageText(o.optLong("t"))).append(")");
        if (isStale(o)) sb.append("\nOlder than 24 h, so it may be out of date");
        return sb.toString();
    }

    public static String tripLine(JSONObject route) {
        StringBuilder sb = new StringBuilder();
        if (route.has("km")) {
            sb.append(String.format(Locale.US, "%.1f km  \u00B7  about %d min",
                    route.optDouble("km"), Math.round(route.optDouble("min"))));
            sb.append("\nDistance and time from OpenStreetMap, without live traffic. Not a fare.");
        } else {
            sb.append("Distance and time not available for this route.");
        }
        String w = route.optString("when");
        if (!w.isEmpty()) sb.append("\nWhen: ").append(w);
        sb.append("\nPassengers: ").append(route.optString("pax", "1"));
        return sb.toString();
    }

    /** Latest, previous, change, and lowest observed for one provider + category. */
    public static String statsText(List<JSONObject> h) {
        if (h.isEmpty()) return "No fares entered yet.";
        JSONObject last = h.get(h.size() - 1);
        double now = totalOf(last);
        StringBuilder sb = new StringBuilder();
        sb.append("Latest: ").append(EdithShopping.money(now)).append(" (")
                .append(kindLabel(last.optString("kind"))).append(", ")
                .append(ageText(last.optLong("t"))).append(")\n");
        if (h.size() >= 2) {
            double prev = totalOf(h.get(h.size() - 2));
            sb.append("Previous: ").append(EdithShopping.money(prev)).append("\n");
            double diff = now - prev;
            if (diff == 0) {
                sb.append("Change: none\n");
            } else {
                sb.append("Change: ").append(diff < 0 ? "\u25BC " : "\u25B2 ")
                        .append(EdithShopping.money(Math.abs(diff)))
                        .append(String.format(Locale.US, " (%.1f%%)\n", Math.abs(diff) / prev * 100.0));
            }
        } else {
            sb.append("Previous: none yet\n");
        }
        double lo = Double.MAX_VALUE;
        for (JSONObject o : h) lo = Math.min(lo, totalOf(o));
        sb.append("Lowest observed here: ").append(EdithShopping.money(lo))
                .append(" across ").append(h.size()).append(h.size() == 1 ? " entry" : " entries")
                .append(" (only what you entered on this phone)");
        return sb.toString();
    }

    public static String historyText(List<JSONObject> h) {
        StringBuilder rows = new StringBuilder();
        for (int i = h.size() - 1; i >= 0; i--) {
            JSONObject o = h.get(i);
            rows.append(when(o.optLong("t"))).append("\n   ").append(fareText(o))
                    .append("  (").append(kindLabel(o.optString("kind"))).append(")\n");
        }
        return rows.toString();
    }

    // ------------------------------------------------------------------
    // Alert when a fare you enter reaches the target
    // ------------------------------------------------------------------
    public static void notifyTarget(Context ctx, JSONObject route, JSONObject obs) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "Price drops", NotificationManager.IMPORTANCE_DEFAULT));
            }
            Intent open = new Intent(ctx, MainActivity.class)
                    .putExtra("edith_open_cabs", route.optString("id"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            int id = route.optString("id").hashCode();
            PendingIntent tap = PendingIntent.getActivity(ctx, id, open, flags);

            String title = "Target fare reached: " + route.optString("pickup") + " \u2192 " + route.optString("drop");
            String body = obs.optString("prov") + " " + obs.optString("cat") + ": "
                    + EdithShopping.money(totalOf(obs)) + " (" + kindLabel(obs.optString("kind"))
                    + " you entered). Confirm the price in the provider's app before booking.";
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(tap)
                    .setAutoCancel(true);
            nm.notify(id, b.build());
        } catch (Exception ignored) {
            // Notification permission denied or unavailable: the in-app alert still shows.
        }
    }
}