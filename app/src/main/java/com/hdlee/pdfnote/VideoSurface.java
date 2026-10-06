package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

/**
 * A VideoView replacement that draws into a TextureView. A SurfaceView (VideoView) shows its picture through a hole punched in the window, which a
 * parent with its own background, elevation or clipping can hide while the sound still plays; a TextureView is an ordinary view and always shows.
 */
final class VideoSurface extends TextureView implements TextureView.SurfaceTextureListener {
    interface Prepared { void onPrepared(); }
    interface Failed { void onFailed(); }

    private MediaPlayer player;
    private Uri uri;
    private Surface surface;
    private boolean prepared, startWhenReady;
    private int videoWidth, videoHeight;
    private Prepared preparedListener;
    private Failed failedListener;

    VideoSurface(Context context) { super(context); setSurfaceTextureListener(this); }

    void setVideoURI(Uri uri) { this.uri = uri; open(); }
    void setOnPreparedListener(Prepared listener) { preparedListener = listener; }
    void setOnErrorListener(Failed listener) { failedListener = listener; }

    private void open() {
        if (uri == null || surface == null || player != null) return;
        try {
            player = new MediaPlayer();
            player.setSurface(surface);
            player.setDataSource(getContext(), uri);
            player.setOnVideoSizeChangedListener((m, w, h) -> { videoWidth = w; videoHeight = h; requestLayout(); });
            player.setOnPreparedListener(m -> {
                prepared = true; videoWidth = m.getVideoWidth(); videoHeight = m.getVideoHeight(); requestLayout();
                if (videoWidth <= 0 || videoHeight <= 0) { fail(); return; }   // sound only: this device cannot decode the picture
                if (preparedListener != null) preparedListener.onPrepared();
                if (startWhenReady) m.start();
            });
            player.setOnErrorListener((m, what, extra) -> { fail(); return true; });
            player.prepareAsync();
        } catch (Exception error) { fail(); }
    }

    private void fail() { if (failedListener != null) failedListener.onFailed(); }

    void start() { startWhenReady = true; if (player != null && prepared) player.start(); }
    void pause() { startWhenReady = false; if (player != null && prepared) player.pause(); }
    boolean isPlaying() { try { return player != null && prepared && player.isPlaying(); } catch (IllegalStateException e) { return false; } }
    int getDuration() { return player != null && prepared ? Math.max(0, player.getDuration()) : 0; }
    int getCurrentPosition() { return player != null && prepared ? player.getCurrentPosition() : 0; }
    void seekTo(int ms) { if (player != null && prepared) player.seekTo(ms); }

    void stopPlayback() {
        startWhenReady = false; prepared = false;
        if (player != null) { try { player.stop(); } catch (IllegalStateException ignored) {} player.release(); player = null; }
    }

    /** Fits the picture inside the view, keeping its proportions and centring it. */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec), maxH = MeasureSpec.getSize(heightSpec);
        if (videoWidth <= 0 || videoHeight <= 0) { setMeasuredDimension(maxW, maxH); return; }
        float fit = Math.min(maxW / (float) videoWidth, maxH / (float) videoHeight);
        setMeasuredDimension(Math.round(videoWidth * fit), Math.round(videoHeight * fit));
    }

    static FrameLayout.LayoutParams centred() { return new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER); }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) { surface = new Surface(texture); if (player != null) player.setSurface(surface); else open(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { if (player != null) player.setSurface(null); if (surface != null) { surface.release(); surface = null; } return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
