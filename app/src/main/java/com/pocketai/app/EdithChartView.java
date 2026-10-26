package com.pocketai.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small line chart of recorded fares, drawn with Canvas (no library). Points are evenly spaced. */
public class EdithChartView extends View {

    private final double[] values;
    private final long[] times;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public EdithChartView(Context c, double[] values, long[] times,
                          int lineColor, int textColor, int gridColor) {
        super(c);
        this.values = values;
        this.times = times;
        float d = c.getResources().getDisplayMetrics().density;
        line.setColor(lineColor);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        dot.setColor(lineColor);
        dot.setStyle(Paint.Style.FILL);
        grid.setColor(gridColor);
        grid.setStrokeWidth(1f * d);
        label.setColor(textColor);
        label.setTextSize(11f * d);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float left = 8 * d, right = getWidth() - 8 * d;
        float top = 20 * d, bottom = getHeight() - 24 * d;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double lo = min, hi = max;
        if (hi - lo < 1e-9) {
            lo -= 1;
            hi += 1;
        } else {
            double pad = (hi - lo) * 0.1;
            lo -= pad;
            hi += pad;
        }

        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, grid);
        }

        path.reset();
        for (int i = 0; i < n; i++) {
            float x = n == 1 ? (left + right) / 2f : left + (right - left) * i / (n - 1f);
            float y = (float) (bottom - (values[i] - lo) / (hi - lo) * (bottom - top));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            canvas.drawCircle(x, y, 3.5f * d, dot);
        }
        if (n > 1) canvas.drawPath(path, line);

        label.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(EdithShopping.money(max), left, top - 6 * d, label);
        if (max != min) canvas.drawText(EdithShopping.money(min), left, bottom - 4 * d, label);
        SimpleDateFormat f = new SimpleDateFormat("dd MMM", Locale.US);
        canvas.drawText(f.format(new Date(times[0])), left, getHeight() - 6 * d, label);
        if (n > 1) {
            label.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(f.format(new Date(times[n - 1])), right, getHeight() - 6 * d, label);
        }
    }
}