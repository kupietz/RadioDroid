package net.programmierecke.radiodroid2;

import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;

import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import net.programmierecke.radiodroid2.alarm.RadioAlarmManager;
import net.programmierecke.radiodroid2.history.TrackHistoryRepository;
import net.programmierecke.radiodroid2.players.TextToSpeechPlayer;
import net.programmierecke.radiodroid2.players.mpd.MPDClient;
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadataSearcher;
import net.programmierecke.radiodroid2.proxy.ProxySettings;
import net.programmierecke.radiodroid2.recording.RecordingsManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RadioDroidApp extends MultiDexApplication {

    private HistoryManager historyManager;
    private FavouriteManager favouriteManager;
    private RecordingsManager recordingsManager;
    private FallbackStationsManager fallbackStationsManager;
    private RadioAlarmManager alarmManager;

    private TrackHistoryRepository trackHistoryRepository;

    private MPDClient mpdClient;

    private TrackMetadataSearcher trackMetadataSearcher;

    private ConnectionPool connectionPool;
    private OkHttpClient httpClient;
    private TextToSpeechPlayer textToSpeechPlayer;

    public class UserAgentInterceptor implements Interceptor {

        private final String userAgent;

        public UserAgentInterceptor(String userAgent) {
            this.userAgent = userAgent;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        GoogleProviderHelper.use(getBaseContext());

        connectionPool = new ConnectionPool();

        rebuildHttpClient();

        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(this, Integer.MAX_VALUE));
        Picasso picassoInstance = builder.build();
        Picasso.setSingletonInstance(picassoInstance);

        CountryCodeDictionary.getInstance().load(this);
        CountryFlagsLoader.getInstance();

        historyManager = new HistoryManager(this);
        favouriteManager = new FavouriteManager(this);
        fallbackStationsManager = new FallbackStationsManager(this);
        recordingsManager = new RecordingsManager();
        alarmManager = new RadioAlarmManager(this);

        trackHistoryRepository = new TrackHistoryRepository(this);

        mpdClient = new MPDClient(this);

        trackMetadataSearcher = new TrackMetadataSearcher(httpClient);

        textToSpeechPlayer = new TextToSpeechPlayer(this, R.string.warning_network_lost_trying_resume);

        recordingsManager.updateRecordingsList();
    }

    public void rebuildHttpClient() {
        httpClient = newHttpClient()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(new UserAgentInterceptor("RadioDroid2/" + BuildConfig.VERSION_NAME))
                .build();
    }

    public FallbackStationsManager getFallbackStationsManager() {
        return fallbackStationsManager;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public FavouriteManager getFavouriteManager() {
        return favouriteManager;
    }

    public RecordingsManager getRecordingsManager() {
        return recordingsManager;
    }

    public RadioAlarmManager getAlarmManager() {
        return alarmManager;
    }

    public TrackHistoryRepository getTrackHistoryRepository() {
        return trackHistoryRepository;
    }

    public MPDClient getMpdClient() {
        return mpdClient;
    }

    public TrackMetadataSearcher getTrackMetadataSearcher() {
        return trackMetadataSearcher;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public OkHttpClient.Builder newHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().connectionPool(connectionPool);
        if (!setCurrentOkHttpProxy(builder)){
            Toast toast = Toast.makeText(this, getResources().getString(R.string.ignore_proxy_settings_invalid), Toast.LENGTH_SHORT);
            toast.show();
        }
        return Utils.enableTls12OnPreLollipop(builder);
    }

    public OkHttpClient.Builder newHttpClientWithoutProxy() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().connectionPool(connectionPool);
        return Utils.enableTls12OnPreLollipop(builder);
    }

    public boolean setCurrentOkHttpProxy(@NonNull OkHttpClient.Builder builder) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        ProxySettings proxySettings = ProxySettings.fromPreferences(sharedPref);
        if (proxySettings != null) {
            if (!Utils.setOkHttpProxy(builder, proxySettings)){
                // proxy settings are not valid
                return false;
            }
        }
        return true;
    }

    public TextToSpeechPlayer getTextToSpeechPlayer() {
        return textToSpeechPlayer;
    }
}
