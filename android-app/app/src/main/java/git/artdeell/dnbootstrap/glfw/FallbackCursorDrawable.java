package git.artdeell.dnbootstrap.glfw;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;


public class FallbackCursorDrawable extends Drawable {
    private final Paint fallbackRectPaint = new Paint();

    public FallbackCursorDrawable() {
        fallbackRectPaint.setStyle(Paint.Style.STROKE);
        fallbackRectPaint.setColor(Color.RED);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), fallbackRectPaint);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }
}
