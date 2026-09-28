package sample;
import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import cc.ccwu.signalfeed.modapi.SignalMod;
public final class WelcomeMod implements SignalMod {
    @Override public View createHome(Activity activity) {
        TextView view = new TextView(activity);
        view.setText("Runtime module loaded\n\nThis home screen comes from the ZIP module\n\nUninstall in Settings, exit and reopen to restore the original home");
        view.setTextSize(22);
        view.setPadding(48, 80, 48, 48);
        return view;
    }
}
