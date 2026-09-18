//Copyright (c) 2017. 章钦豪. All rights reserved.
package com.kunfei.bookshelf;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDex;

import com.kunfei.bookshelf.constant.AppConstant;
import com.kunfei.bookshelf.help.AppFrontBackHelper;
import com.kunfei.bookshelf.help.CrashHandler;
import com.kunfei.bookshelf.help.FileHelp;
import com.kunfei.bookshelf.model.UpLastChapterModel;
import com.kunfei.bookshelf.utils.theme.ThemeStore;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.internal.functions.Functions;
import io.reactivex.plugins.RxJavaPlugins;
import timber.log.Timber;

public class MApplication extends Application {
    public final static String channelIdDownload = "channel_download";
    public static String downloadPath;
    public static boolean isEInkMode;
    public static String SEARCH_GROUP = null;
    private static MApplication instance;
    private static String versionName;
    private static int versionCode;
    private SharedPreferences configPreferences;

    public static MApplication getInstance() {
        return instance;
    }

    public static int getVersionCode() {
        return versionCode;
    }

    public static String getVersionName() {
        return versionName;
    }

    public static Resources getAppResources() {
        return getInstance().getResources();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        long bootT0 = System.currentTimeMillis();
        android.util.Log.i("Boot", "MApplication.onCreate 开始");
        instance = this;
        long tMark = bootT0;
        CrashHandler.getInstance().init(this);
        android.util.Log.i("Boot", "  CrashHandler.init = " + (System.currentTimeMillis() - tMark) + "ms");
        tMark = System.currentTimeMillis();
        Timber.plant(new Timber.DebugTree());
        RxJavaPlugins.setErrorHandler(Functions.emptyConsumer());
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionCode = 0;
            versionName = "0.0.0";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannelId();
        }
        android.util.Log.i("Boot", "  版本号读取 = " + (System.currentTimeMillis() - tMark) + "ms");
        tMark = System.currentTimeMillis();
        configPreferences = getSharedPreferences("CONFIG", 0);
        android.util.Log.i("Boot", "    getSharedPreferences = " + (System.currentTimeMillis() - tMark) + "ms");
        long tPref = System.currentTimeMillis();
        downloadPath = configPreferences.getString(getString(R.string.pk_download_path), "");
        android.util.Log.i("Boot", "    prefs.getString = " + (System.currentTimeMillis() - tPref) + "ms");
        if (TextUtils.isEmpty(downloadPath) | Objects.equals(downloadPath, FileHelp.getCachePath())) {
            long tSet = System.currentTimeMillis();
            setDownloadPath(null);
            android.util.Log.i("Boot", "    setDownloadPath = " + (System.currentTimeMillis() - tSet) + "ms");
        }
        android.util.Log.i("Boot", "  下载路径初始化 = " + (System.currentTimeMillis() - tMark) + "ms");
        tMark = System.currentTimeMillis();
        initNightTheme();
        // 墨水屏：始终强制黑白主题（老版本存过自定义颜色的，这里会被纠正回来）
        int wantPrimary = isNightTheme() ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
        if (!ThemeStore.isConfigured(this, versionCode) || ThemeStore.primaryColor(this) != wantPrimary) {
            upThemeStore();
        }
        android.util.Log.i("Boot", "  主题初始化 = " + (System.currentTimeMillis() - tMark) + "ms");
        tMark = System.currentTimeMillis();
        AppFrontBackHelper.getInstance().register(this, new AppFrontBackHelper.OnAppStatusListener() {
            @Override
            public void onFront() {
            }

            @Override
            public void onBack() {
                UpLastChapterModel.destroy();
            }
        });
        android.util.Log.i("Boot", "  前后台监听注册 = " + (System.currentTimeMillis() - tMark) + "ms");
        tMark = System.currentTimeMillis();
        upEInkMode();
        android.util.Log.i("Boot", "  墨水屏模式 = " + (System.currentTimeMillis() - tMark) + "ms");
        // 内置番茄源：幂等注册（已存在就不动）。放到后台线程，避免启动时在主线程做数据库读写
        new Thread(() -> com.kunfei.bookshelf.fanqie.FanqieBookSource.INSTANCE.ensureInserted(),
                "fanqie-source-init").start();
        android.util.Log.i("Boot", "MApplication.onCreate 结束 用时=" + (System.currentTimeMillis() - bootT0) + "ms");
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public void initNightTheme() {
        if (isNightTheme()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    /**
     * 初始化主题
     */
    public void upThemeStore() {
        // 墨水屏：主题只保留黑白两色，白天/夜间各一套（不再允许自定义颜色）
        if (isNightTheme()) {
            ThemeStore.editTheme(this)
                    .primaryColor(android.graphics.Color.BLACK)
                    .accentColor(android.graphics.Color.WHITE)
                    .backgroundColor(android.graphics.Color.BLACK)
                    .apply();
        } else {
            ThemeStore.editTheme(this)
                    .primaryColor(android.graphics.Color.WHITE)
                    .accentColor(android.graphics.Color.BLACK)
                    .backgroundColor(android.graphics.Color.WHITE)
                    .apply();
        }
    }

    public boolean isNightTheme() {
        return configPreferences.getBoolean("nightTheme", false);
    }

    /**
     * 设置下载地址
     */
    public void setDownloadPath(String path) {
        if (TextUtils.isEmpty(path)) {
            downloadPath = FileHelp.getFilesPath();
        } else {
            downloadPath = path;
        }
        AppConstant.BOOK_CACHE_PATH = downloadPath + File.separator + "book_cache" + File.separator;
        configPreferences.edit()
                .putString(getString(R.string.pk_download_path), path)
                .apply();
    }

    public static SharedPreferences getConfigPreferences() {
        return getInstance().configPreferences;
    }

    public void upEInkMode() {
        MApplication.isEInkMode = configPreferences.getBoolean("E-InkMode", false);
    }

    /**
     * 创建通知ID
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannelId() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //用唯一的ID创建渠道对象
        NotificationChannel downloadChannel = new NotificationChannel(channelIdDownload,
                getString(R.string.download_offline),
                NotificationManager.IMPORTANCE_LOW);
        //初始化channel
        downloadChannel.enableLights(false);
        downloadChannel.enableVibration(false);
        downloadChannel.setSound(null, null);

        //向notification manager 提交channel
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(downloadChannel);
        }
    }

}
