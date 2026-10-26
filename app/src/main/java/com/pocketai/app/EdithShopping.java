package com.pocketai.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EDITH Smart Shopping (Stages 1-3): store search links, price reading from a product page,
 * a dated price history, and background re-checks with price-drop alerts. Nothing here invents prices: every stored price is either
 * read from the page (SRC_PAGE) or typed by the user (SRC_MANUAL), and is labelled as such.
 */
public final class EdithShopping {

    public static final String SRC_PAGE = "PAGE";
    public static final String SRC_MANUAL = "MANUAL";
    private static final String PREFS = "edith_shopping";
    private static final String KEY = "products";
    private static final int MAX_HISTORY = 500;

    private EdithShopping() {}

    /** Result of trying to read a price from a page. price == null means "could not read". */
    public static final class Read {
        public Double price;
        public String title = "";
        public String note = "";
    }

    // ------------------------------------------------------------------
    // Stage 1: store search links (opened in the browser / store app)
    // ------------------------------------------------------------------
    public static String[][] searchLinks(String query) {
        String q;
        try {
            q = URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            q = query.replace(" ", "+");
        }
        return new String[][]{
                {"Amazon.in", "https://www.amazon.in/s?k=" + q},
                {"Flipkart", "https://www.flipkart.com/search?q=" + q},
                {"Croma", "https://www.croma.com/searchB?q=" + q},
                {"Reliance Digital", "https://www.reliancedigital.in/search?q=" + q},
                {"Google Shopping", "https://www.google.com/search?tbm=shop&q=" + q},
        };
    }

    /** Pulls the first link out of pasted text, forces https, returns null if it isn't a link. */
    public static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        Matcher m = Pattern.compile("(?i)https?://\\S+").matcher(s);
        if (m.find()) s = m.group();
        else if (s.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}(/\\S*)?$")) s = "https://" + s;
        else return null;
        if (s.toLowerCase(Locale.ROOT).startsWith("http://")) s = "https://" + s.substring(7);
        s = s.replaceAll("[)\\],.]+$", "");
        try {
            String host = new URL(s).getHost();
            if (host == null || !host.contains(".")) return null;
        } catch (Exception e) {
            return null;
        }
        return canonicalUrl(s);
    }

    private static final Pattern AMAZON_ASIN = Pattern.compile("/(?:dp|gp/product|gp/aw/d)/([A-Za-z0-9]{10})");

    /** Strips tracking junk from Amazon product links (…/dp/ASIN) so one product = one listing. */
    public static String canonicalUrl(String url) {
        try {
            String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
            if (host.contains("amazon.")) {
                Matcher m = AMAZON_ASIN.matcher(url);
                if (m.find()) return "https://" + host + "/dp/" + m.group(1).toUpperCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    /** True for stores EDITH knows by name (used to decide if a shared link is a product link). */
    public static boolean isStoreUrl(String url) {
        String s = storeOf(url);
        return s.equals("Amazon") || s.equals("Flipkart") || s.equals("Croma")
                || s.equals("Reliance Digital") || s.equals("Myntra") || s.equals("Ajio")
                || s.equals("Tata CLiQ") || s.equals("Vijay Sales");
    }

    public static String storeOf(String url) {
        String host;
        try {
            host = new URL(url).getHost().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "Store";
        }
        if (host.contains("amazon") || host.contains("amzn")) return "Amazon";
        if (host.contains("flipkart") || host.contains("fkrt")) return "Flipkart";
        if (host.contains("croma")) return "Croma";
        if (host.contains("reliancedigital")) return "Reliance Digital";
        if (host.contains("myntra")) return "Myntra";
        if (host.contains("ajio")) return "Ajio";
        if (host.contains("tatacliq")) return "Tata CLiQ";
        if (host.contains("vijaysales")) return "Vijay Sales";
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    // ------------------------------------------------------------------
    // Reading a price from a product page (blocking: call off the main thread)
    // ------------------------------------------------------------------
    public static Read readPrice(String url) {
        Read r = new Read();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            int code = c.getResponseCode();
            if (code == 403 || code == 429 || code == 503) {
                r.note = "the store blocked automated access (HTTP " + code + ")";
                return r;
            }
            if (code < 200 || code >= 300) {
                r.note = "the page returned HTTP " + code;
                return r;
            }
            String html = readLimited(c.getInputStream(), 1500000);
            r.title = findTitle(html);
            extractPrice(html, r);
        } catch (Exception e) {
            r.note = "could not open the page (" + (e.getMessage() == null ? "network error" : e.getMessage()) + ")";
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private static final Pattern LD_BLOCK = Pattern.compile(
            "<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LD_PRICE = Pattern.compile(
            "\"(?:price|lowPrice)\"\\s*:\\s*\"?([0-9][0-9,]*(?:\\.[0-9]+)?)\"?");
    private static final Pattern LD_CURRENCY = Pattern.compile("\"priceCurrency\"\\s*:\\s*\"([A-Za-z]{3})\"");
    private static final Pattern META_A = Pattern.compile(
            "<meta[^>]+(?:property|name|itemprop)=[\"'](?:product:price:amount|og:price:amount)[\"'][^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_B = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name|itemprop)=[\"'](?:product:price:amount|og:price:amount)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CUR = Pattern.compile(
            "<meta[^>]+(?:property|name|itemprop)=[\"'](?:product:price:currency|og:price:currency)[\"'][^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEMPROP = Pattern.compile(
            "itemprop=[\"']price[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    // Amazon rarely ships JSON-LD. Its main price sits in the "priceToPay" block; we read only that
    // block (never the strike-through list price or other items on the page).
    private static final Pattern AMAZON_PRICE = Pattern.compile(
            "priceToPay.{0,800}?a-offscreen[\"']?>\\s*(?:&#8377;|\u20B9|Rs\\.?|INR)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)",
            Pattern.DOTALL);
    private static final Pattern AMAZON_TITLE = Pattern.compile(
            "id=[\"']productTitle[\"'][^>]*>\\s*([^<]+)<", Pattern.CASE_INSENSITIVE);

    /** Only trusts structured product data (JSON-LD / meta tags), never random text on the page. */
    static void extractPrice(String html, Read r) {
        String foreignCurrency = null;

        Matcher blocks = LD_BLOCK.matcher(html);
        while (blocks.find()) {
            String block = blocks.group(1);
            Matcher p = LD_PRICE.matcher(block);
            if (!p.find()) continue;
            Matcher cur = LD_CURRENCY.matcher(block);
            if (cur.find() && !"INR".equalsIgnoreCase(cur.group(1))) {
                foreignCurrency = cur.group(1).toUpperCase(Locale.ROOT);
                continue;
            }
            Double v = parseNumber(p.group(1));
            if (v != null) { r.price = v; return; }
        }

        Matcher cm = META_CUR.matcher(html);
        boolean metaForeign = cm.find() && !"INR".equalsIgnoreCase(cm.group(1).trim());
        if (metaForeign) {
            foreignCurrency = cm.group(1).trim().toUpperCase(Locale.ROOT);
        } else {
            Pattern[] metas = {META_A, META_B, ITEMPROP};
            for (Pattern pat : metas) {
                Matcher m = pat.matcher(html);
                if (m.find()) {
                    Double v = parseNumber(m.group(1));
                    if (v != null) { r.price = v; return; }
                }
            }
        }

        if (foreignCurrency == null) {
            Matcher am = AMAZON_PRICE.matcher(html);
            if (am.find()) {
                Double v = parseNumber(am.group(1));
                if (v != null) { r.price = v; return; }
            }
        }

        if (foreignCurrency != null) {
            r.note = "the page lists its price in " + foreignCurrency + ", not rupees";
        } else {
            String lower = html.toLowerCase(Locale.ROOT);
            if (lower.contains("captcha") || lower.contains("robot check") || lower.contains("are you a human")) {
                r.note = "the store showed a robot check instead of the product";
            } else {
                r.note = "this page doesn't expose its price in a readable form";
            }
        }
    }

    static Double parseNumber(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[^0-9.]", "");
        if (t.isEmpty() || t.equals(".")) return null;
        try {
            double v = Double.parseDouble(t);
            return (v > 0 && v < 100000000d) ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String findTitle(String html) {
        Matcher pt = AMAZON_TITLE.matcher(html);
        if (pt.find()) return cleanTitle(pt.group(1));
        Matcher og = Pattern.compile(
                "<meta[^>]+property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        String t = "";
        if (og.find()) t = og.group(1);
        else {
            Matcher tt = Pattern.compile("<title[^>]*>(.*?)</title>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            if (tt.find()) t = tt.group(1);
        }
        return cleanTitle(t);
    }

    private static String cleanTitle(String t) {
        t = t.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&#x27;", "'").replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80).trim() : t;
    }

    private static String readLimited(InputStream in, int limit) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) > 0 && out.size() < limit) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    // ------------------------------------------------------------------
    // Storage (SharedPreferences, JSON)
    // product: {id, name, listings:[ {id, store, url, history:[ {t, p, src} ]} ]}
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

    private static JSONObject find(JSONArray arr, String id) {
        if (id == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    /** Records one dated price. Creates the product and/or listing if needed. */
    public static synchronized void recordPrice(Context ctx, String productId, String nameHint, String url,
                                   String listingId, String title, double price, String src) throws Exception {
        JSONArray all = load(ctx);
        JSONObject product = find(all, productId);
        if (product == null) {
            product = new JSONObject();
            product.put("id", UUID.randomUUID().toString());
            String name = (nameHint != null && !nameHint.trim().isEmpty()) ? nameHint.trim()
                    : (title != null && !title.isEmpty() ? title : storeOf(url) + " item");
            product.put("name", name);
            product.put("listings", new JSONArray());
            all.put(product);
        }
        JSONArray listings = product.getJSONArray("listings");
        JSONObject listing = find(listings, listingId);
        if (listing == null) {
            for (int i = 0; i < listings.length(); i++) {
                JSONObject l = listings.optJSONObject(i);
                if (l != null && url.equals(l.optString("url"))) { listing = l; break; }
            }
        }
        if (listing == null) {
            listing = new JSONObject();
            listing.put("id", UUID.randomUUID().toString());
            listing.put("store", storeOf(url));
            listing.put("url", url);
            listing.put("history", new JSONArray());
            listings.put(listing);
        }
        JSONArray history = listing.getJSONArray("history");
        JSONObject entry = new JSONObject();
        entry.put("t", System.currentTimeMillis());
        entry.put("p", price);
        entry.put("src", src);
        history.put(entry);
        while (history.length() > MAX_HISTORY) history.remove(0);
        save(ctx, all);
    }

    public static synchronized void deleteProduct(Context ctx, String productId) {
        JSONArray all = load(ctx);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && !productId.equals(o.optString("id"))) kept.put(o);
        }
        save(ctx, kept);
    }

    public static synchronized void deleteListing(Context ctx, String productId, String listingId) {
        JSONArray all = load(ctx);
        JSONObject product = find(all, productId);
        if (product == null) return;
        JSONArray listings = product.optJSONArray("listings");
        JSONArray kept = new JSONArray();
        for (int i = 0; listings != null && i < listings.length(); i++) {
            JSONObject l = listings.optJSONObject(i);
            if (l != null && !listingId.equals(l.optString("id"))) kept.put(l);
        }
        if (kept.length() == 0) {
            deleteProduct(ctx, productId);
            return;
        }
        try {
            product.put("listings", kept);
        } catch (Exception ignored) {
            return;
        }
        save(ctx, all);
    }

    // ------------------------------------------------------------------
    // Stage 3: price-drop alerts
    // ------------------------------------------------------------------

    /** Alert target in rupees for a product, or 0 if none is set. */
    public static double targetOf(JSONObject product) {
        return product == null ? 0 : product.optDouble("target", 0);
    }

    /** Sets (or clears, when target <= 0) the "tell me at or below this price" amount. */
    public static synchronized void setTarget(Context ctx, String productId, double target) {
        JSONArray all = load(ctx);
        JSONObject product = find(all, productId);
        if (product == null) return;
        try {
            if (target > 0) product.put("target", target);
            else product.remove("target");
        } catch (Exception ignored) {
            return;
        }
        save(ctx, all);
    }

    /** One price drop found by a background check. */
    public static final class Alert {
        public String productId, productName, store, url;
        public double oldPrice, newPrice;
        public boolean hitTarget;
    }

    /**
     * Re-reads every listing that already has a price and records what the page says now.
     * Blocking: call from a background thread. A listing whose page can't be read is skipped
     * (nothing is recorded, nothing is guessed). Returns the listings whose price went down.
     */
    public static List<Alert> refreshAll(Context ctx, int maxListings, AtomicBoolean stop) {
        List<Alert> alerts = new ArrayList<>();
        JSONArray all = load(ctx);
        int checked = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject product = all.optJSONObject(i);
            if (product == null) continue;
            JSONArray ls = product.optJSONArray("listings");
            for (int j = 0; ls != null && j < ls.length(); j++) {
                if (stop != null && stop.get()) return alerts;
                if (checked >= maxListings) return alerts;
                JSONObject l = ls.optJSONObject(j);
                JSONObject last = l == null ? null : lastEntry(l);
                if (last == null) continue;

                if (checked > 0) {
                    try { Thread.sleep(2500); } catch (InterruptedException e) { return alerts; }
                }
                checked++;

                String url = l.optString("url");
                Read r = readPrice(url);
                if (r.price == null) continue;
                double old = last.optDouble("p");
                try {
                    recordPrice(ctx, product.optString("id"), null, url, l.optString("id"),
                            r.title, r.price, SRC_PAGE);
                } catch (Exception e) {
                    continue;
                }
                if (r.price < old) {
                    Alert a = new Alert();
                    a.productId = product.optString("id");
                    a.productName = product.optString("name", "Product");
                    a.store = l.optString("store");
                    a.url = url;
                    a.oldPrice = old;
                    a.newPrice = r.price;
                    double target = targetOf(product);
                    a.hitTarget = target > 0 && r.price <= target;
                    alerts.add(a);
                }
            }
        }
        return alerts;
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------
    public static String money(double v) {
        // Indian digit grouping (12,34,567), without depending on the device's locale data.
        long whole = (long) Math.floor(v);
        int paise = (int) Math.round((v - whole) * 100);
        if (paise == 100) { whole++; paise = 0; }
        String digits = Long.toString(whole);
        StringBuilder sb = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            sb.append(digits.charAt(i));
            int left = n - 1 - i;
            if (left > 0 && (left == 3 || (left > 3 && (left - 3) % 2 == 0))) sb.append(',');
        }
        String out = "\u20B9" + sb;
        if (paise > 0) out += String.format(Locale.US, ".%02d", paise);
        return out;
    }

    private static String when(long t) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date(t));
    }

    private static String srcLabel(String src) {
        return SRC_PAGE.equals(src) ? "read from page" : "entered by you";
    }

    private static JSONObject lastEntry(JSONObject listing) {
        JSONArray h = listing.optJSONArray("history");
        return (h == null || h.length() == 0) ? null : h.optJSONObject(h.length() - 1);
    }

    public static String describeLatest(JSONObject listing) {
        JSONObject last = lastEntry(listing);
        if (last == null) return "No price recorded yet";
        StringBuilder sb = new StringBuilder();
        sb.append(money(last.optDouble("p"))).append("  \u00B7  ")
                .append(srcLabel(last.optString("src"))).append("\n")
                .append(when(last.optLong("t")));
        JSONArray h = listing.optJSONArray("history");
        if (h != null && h.length() >= 2) {
            double prev = h.optJSONObject(h.length() - 2).optDouble("p");
            double now = last.optDouble("p");
            if (now < prev) sb.append("\n\u25BC ").append(money(prev - now)).append(" lower than the previous entry");
            else if (now > prev) sb.append("\n\u25B2 ").append(money(now - prev)).append(" higher than the previous entry");
            else sb.append("\nSame as the previous entry");
        }
        return sb.toString();
    }

    /** Index of the listing with the lowest latest recorded price; -1 unless 2+ listings have prices. */
    public static int cheapestIndex(JSONObject product) {
        JSONArray ls = product.optJSONArray("listings");
        if (ls == null) return -1;
        int best = -1, priced = 0;
        double bestPrice = Double.MAX_VALUE;
        for (int i = 0; i < ls.length(); i++) {
            JSONObject last = lastEntry(ls.optJSONObject(i));
            if (last == null) continue;
            priced++;
            double p = last.optDouble("p");
            if (p < bestPrice) { bestPrice = p; best = i; }
        }
        return priced >= 2 ? best : -1;
    }

    public static String historyText(JSONObject listing) {
        JSONArray h = listing.optJSONArray("history");
        if (h == null || h.length() == 0) return "No prices recorded yet.";
        double lo = Double.MAX_VALUE, hi = 0;
        StringBuilder rows = new StringBuilder();
        for (int i = h.length() - 1; i >= 0; i--) {
            JSONObject e = h.optJSONObject(i);
            double p = e.optDouble("p");
            lo = Math.min(lo, p);
            hi = Math.max(hi, p);
            rows.append(when(e.optLong("t"))).append("\n   ")
                    .append(money(p)).append("  (").append(srcLabel(e.optString("src"))).append(")\n");
        }
        return h.length() + " recorded price" + (h.length() == 1 ? "" : "s")
                + "\nLowest: " + money(lo) + "   Highest: " + money(hi) + "\n\n" + rows;
    }
}