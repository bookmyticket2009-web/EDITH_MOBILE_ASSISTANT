package com.pocketai.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Re-checks tracked prices in the background (about every 6 hours, on any network) and posts a
 * notification when a page now shows a lower price than EDITH last recorded.
 * Uses the framework JobScheduler, so no extra library is needed.
 */
public class EdithPriceJob extends JobService {

    private static final int JOB_ID = 7301;
    private static final long PERIOD_MS = 6L * 60 * 60 * 1000;
    private static final int MAX_LISTINGS_PER_RUN = 30;
    private static final String CHANNEL_ID = "edith_price_alerts";

    private final AtomicBoolean stop = new AtomicBoolean(false);
    private Thread worker;

    /** Safe to call repeatedly: does nothing if the job is already scheduled. */
    public static void schedule(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) {
                if (j.getId() == JOB_ID) return;
            }
            ComponentName cn = new ComponentName(ctx, EdithPriceJob.class);
            try {
                // Survives reboots (needs RECEIVE_BOOT_COMPLETED in the manifest).
                js.schedule(new JobInfo.Builder(JOB_ID, cn)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(PERIOD_MS)
                        .setPersisted(true)
                        .build());
            } catch (SecurityException noBootPermission) {
                js.schedule(new JobInfo.Builder(JOB_ID, cn)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(PERIOD_MS)
                        .build());
            }
        } catch (Exception e) {
            Log.e("EDITH", "Could not schedule price checks", e);
        }
    }

    public static void cancel(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        stop.set(false);
        worker = new Thread(() -> {
            try {
                List<EdithShopping.Alert> alerts =
                        EdithShopping.refreshAll(getApplicationContext(), MAX_LISTINGS_PER_RUN, stop);
                for (EdithShopping.Alert a : alerts) notifyDrop(a);
            } catch (Throwable t) {
                Log.e("EDITH", "Price check failed", t);
            }
            jobFinished(params, false);
        }, "edith-price-check");
        worker.start();
        return true; // still working on a background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stop.set(true);
        return true; // run again later
    }

    private void notifyDrop(EdithShopping.Alert a) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Price drops", NotificationManager.IMPORTANCE_DEFAULT));
        }

        Intent open = new Intent(this, MainActivity.class)
                .putExtra("edith_open_shop", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        int id = (a.productId + a.store).hashCode();
        PendingIntent tap = PendingIntent.getActivity(this, id, open, flags);

        String title = (a.hitTarget ? "Target reached: " : "Price drop: ") + a.productName;
        String body = a.store + " now shows " + EdithShopping.money(a.newPrice)
                + " (was " + EdithShopping.money(a.oldPrice) + "). Read from the page, so check before buying.";

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info) // swap for your own icon
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true);
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
            // Notification permission denied on Android 13+.
        }
    }
}