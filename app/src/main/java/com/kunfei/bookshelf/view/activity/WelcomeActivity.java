//Copyright (c) 2017. 章钦豪. All rights reserved.
package com.kunfei.bookshelf.view.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;

import com.kunfei.bookshelf.DbHelper;
import com.kunfei.bookshelf.R;
import com.kunfei.bookshelf.presenter.ReadBookPresenter;

/**
 * 启动页：只负责"预热数据库 + 跳到书城/阅读页"，不 inflate 任何布局。
 *
 * 原来是继承 MBaseActivity（AppCompatActivity）并加载欢迎页布局，
 * 在 Kindle 这类老设备上，光是把 AppCompat/Material 那套 decor 建起来就要几百毫秒；
 * 这里改成最朴素的 Activity，启动路径更短。
 */
public class WelcomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 避免从桌面启动程序后，会重新实例化入口类的 activity
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }
        // 后台预热数据库（原来也是这么做的）
        AsyncTask.execute(DbHelper::getDaoSession);
        SharedPreferences preferences = getSharedPreferences("CONFIG", 0);
        if (preferences.getBoolean(getString(R.string.pk_default_read), false)) {
            Intent intent = new Intent(this, ReadBookActivity.class);
            intent.putExtra("openFrom", ReadBookPresenter.OPEN_FROM_APP);
            startActivity(intent);
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        overridePendingTransition(0, 0);
        finish();
    }
}
