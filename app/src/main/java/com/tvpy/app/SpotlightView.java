package com.tvpy.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

public class SpotlightView extends View {
    private View targetView;
    private Paint overlayPaint;
    private Paint borderPaint;
    private Paint eraserPaint;
    private float cornerRadius;
    private float padding;

    public SpotlightView(Context context) {
        super(context);
        init();
    }

    public SpotlightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        // Pintura del fondo oscuro semi-transparente (color de fondo de la app #0B0F19 con opacidad ~75%)
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(0xC0090F19);

        // Pintura para borrar el área del botón (hacer el recorte transparente)
        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setColor(0xFFFFFFFF);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // Pintura para el borde de enfoque de neón azul
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xFF3B82F6); // Azul vibrante coincidente con el botón
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3 * density); // Borde de 3dp de grosor

        cornerRadius = 12 * density; // Radio de esquinas del botón
        padding = 4 * density;       // Margen extra alrededor del botón para el enfoque

        // Habilitar capa de hardware para permitir el modo CLEAR
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setTargetView(View target) {
        this.targetView = target;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (targetView == null) return;

        // 1. Dibujar el fondo oscuro sobre todo el canvas
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);

        // 2. Obtener la ubicación absoluta del botón y la de este view
        int[] targetLocation = new int[2];
        targetView.getLocationInWindow(targetLocation);

        int[] myLocation = new int[2];
        getLocationInWindow(myLocation);

        // Calcular posición relativa del botón respecto a este view
        int left = targetLocation[0] - myLocation[0];
        int top = targetLocation[1] - myLocation[1];
        int right = left + targetView.getWidth();
        int bottom = top + targetView.getHeight();

        // 3. Recortar la zona del botón (hacerla transparente)
        canvas.drawRoundRect(
            left - padding,
            top - padding,
            right + padding,
            bottom + padding,
            cornerRadius,
            cornerRadius,
            eraserPaint
        );

        // 4. Dibujar el borde de enfoque de neón azul
        canvas.drawRoundRect(
            left - padding,
            top - padding,
            right + padding,
            bottom + padding,
            cornerRadius,
            cornerRadius,
            borderPaint
        );
    }
}
