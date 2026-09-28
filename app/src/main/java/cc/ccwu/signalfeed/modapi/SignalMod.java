package cc.ccwu.signalfeed.modapi;
import android.app.Activity;
import android.view.View;
import java.io.File;
/** API 1. Modules run with the app's permissions. Never bundle this interface in classes.dex. */
public interface SignalMod {
    default void onAttach(Activity activity, File moduleDirectory) {}
    default String transformFeed(String feedJson) { return feedJson; }
    default String rewriteUrl(String url) { return url; }
    default View createHome(Activity activity) { return null; }
    default void onDetach() {}
}
