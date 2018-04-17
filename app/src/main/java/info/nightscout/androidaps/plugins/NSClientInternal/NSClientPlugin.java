package info.nightscout.androidaps.plugins.NSClientInternal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.Html;
import android.text.Spanned;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class NSClientPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(NSClientPlugin.class);

    static NSClientPlugin nsClientPlugin;

    static public NSClientPlugin getPlugin() {
        if (nsClientPlugin == null) {
            nsClientPlugin = new NSClientPlugin();
        }
        return nsClientPlugin;
    }

    public Handler handler;

    private final List<EventNSClientNewLog> listLog = new ArrayList<>();
    Spanned textLog = Html.fromHtml("");

    public boolean paused = false;
    boolean autoscroll = true;

    public String status = "";

    public NSClientService nsClientService = null;

    public NSClientPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(NSClientFragment.class.getName())
                .pluginName(R.string.nsclientinternal)
                .shortName(R.string.nsclientinternal_shortname)
                .preferencesId(R.xml.pref_nsclientinternal)
        );

        if (Config.NSCLIENT || Config.G5UPLOADER) {
            pluginDescription.alwaysEnabled(true).visibleByDefault(true);
        }
        paused = SP.getBoolean(R.string.key_nsclientinternal_paused, false);
        autoscroll = SP.getBoolean(R.string.key_nsclientinternal_autoscroll, true);

        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(NSClientPlugin.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, NSClientService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(mConnection);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            nsClientService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            NSClientService.LocalBinder mLocalBinder = (NSClientService.LocalBinder) service;
            if (mLocalBinder != null) // is null when running in roboelectric
                nsClientService = mLocalBinder.getServiceInstance();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        if (nsClientService != null)
            MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientNewLog ev) {
        addToLog(ev);
        log.debug(ev.action + " " + ev.logText);
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientStatus ev) {
        status = ev.status;
        MainApp.bus().post(new EventNSClientUpdateGUI());
    }

    synchronized void clearLog() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listLog) {
                    listLog.clear();
                }
                MainApp.bus().post(new EventNSClientUpdateGUI());
            }
        });
    }

    private synchronized void addToLog(final EventNSClientNewLog ev) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listLog) {
                    listLog.add(ev);
                    // remove the first line if log is too large
                    if (listLog.size() >= Constants.MAX_LOG_LINES) {
                        listLog.remove(0);
                    }
                }
                MainApp.bus().post(new EventNSClientUpdateGUI());
            }
        });
    }

    synchronized void updateLog() {
        try {
            StringBuilder newTextLog = new StringBuilder();
            synchronized (listLog) {
                for (EventNSClientNewLog log : listLog) {
                    newTextLog.append(log.toPreparedHtml());
                }
            }
            textLog = Html.fromHtml(newTextLog.toString());
        } catch (OutOfMemoryError e) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Out of memory!\nStop using this phone !!!", R.raw.error);
        }
    }

    void resend(String reason) {
        if (nsClientService != null)
            nsClientService.resend(reason);
    }

    public UploadQueue queue() {
        return NSClientService.uploadQueue;
    }

    public String url() {
        return NSClientService.nsURL;
    }

    public boolean hasWritePermission() {
        return nsClientService.hasWriteAuth;
    }
}
