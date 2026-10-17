package ai.loko.hk.ui.ocr;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import ai.loko.hk.ui.utils.Logger;

/**
 * The type Media projection helper.
 * <p>
 * Owns the MediaProjection for the lifetime of a capture session. The consent
 * token ({@link #screenshotPermission}) is acquired once from the user; the
 * projection itself is acquired lazily, reused for every screenshot and only
 * stopped when the floating service is destroyed or when Android/the user ends
 * the session (detected via {@link MediaProjection.Callback#onStop()}).
 * <p>
 * The previous behaviour - stopping the projection after every screenshot and
 * re-acquiring it from the same consent token for the next one - is what made
 * captures suddenly fail after a while on newer Android versions: the token is
 * only guaranteed for one projection lifetime, and once the session ended the
 * app had no way to notice.
 */
public class MediaProjectionHelper {

    /**
     * The constant screenshotPermission.
     */
    public static volatile Intent screenshotPermission;
    private static MediaProjectionManager mediaProjectionManager;
    private static MediaProjection mMediaProjection;

    /**
     * Notified (on the main thread) when the capture session ends for a reason
     * outside the app's control. Set by the floating screenshot service so it
    * can remove its nonfunctional button instead of leaving it stuck on screen.
     */
    public interface SessionListener {
        void onSessionEnded();
    }

    private static volatile SessionListener sSessionListener;

    /**
     * Sets media projection manager.
     *
     * @param mediaProjectionManager the media projection manager
     */
    public static void setMediaProjectionManager(MediaProjectionManager mediaProjectionManager) {
        MediaProjectionHelper.mediaProjectionManager = mediaProjectionManager;
    }

    /**
     * Sets screenshot permission.
     *
     * @param screenshotPermission the screenshot permission
     */
    public static void setScreenshotPermission(Intent screenshotPermission) {
        MediaProjectionHelper.screenshotPermission = screenshotPermission;
    }

    /**
     * Registers the listener that is told when the session dies. Pass null to
     * unregister (done in the service's onDestroy).
     */
    public static void setSessionListener(SessionListener listener) {
        sSessionListener = listener;
    }

    /**
     * Get media projection, reusing the one already acquired for this session.
     * Returns null instead of throwing when the consent token was revoked or is
     * otherwise unusable; callers treat null as "session gone".
     *
     * @param context the context
     * @return the media projection or null
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected static synchronized MediaProjection getMediaProjection(Context context) {
        if (mMediaProjection != null) {
            return mMediaProjection;
        }
        if (screenshotPermission == null) {
            return null;
        }
        try {
            MediaProjectionManager mgr =
                    (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mMediaProjection = mgr.getMediaProjection(Activity.RESULT_OK,
                    (Intent) screenshotPermission.clone());
            if (mMediaProjection != null) {
                // Registering the callback is mandatory before the first
                // createVirtualDisplay() on Android 14+, and it is how we learn
                // that the user/system ended the session (quick settings
                // "stop casting", revocation, screen lock policies, ...).
                mMediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        handleSessionEnded();
                    }
                }, new Handler(Looper.getMainLooper()));
            }
        } catch (Exception e) {
            // Revoked or already consumed consent token, invalid display, ...
            Logger.logException(e);
            mMediaProjection = null;
        }
        return mMediaProjection;
    }

    /**
     * Stops and forgets the projection (used when the capture service dies).
     * Safe to call repeatedly; a following capture simply re-acquires it.
     */
    public static synchronized void stopMediaProjection() {
        if (mMediaProjection != null) {
            try {
                mMediaProjection.stop();
            } catch (Exception ignored) {
                // Already stopped by the system.
            }
            mMediaProjection = null;
        }
    }

    /**
     * Called from the projection callback: the session is dead for good, so
     * drop the token (any later capture attempt must go through START
     * SCREENSHOT / a fresh consent) and tell the listener.
     */
    private static void handleSessionEnded() {
        mMediaProjection = null;
        screenshotPermission = null;
        SessionListener listener = sSessionListener;
        if (listener != null) {
            listener.onSessionEnded();
        }
    }
}
