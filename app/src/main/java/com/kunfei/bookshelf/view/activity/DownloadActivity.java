package com.kunfei.bookshelf.view.activity;

import static com.kunfei.bookshelf.service.DownloadService.addDownloadAction;
import static com.kunfei.bookshelf.service.DownloadService.finishDownloadAction;
import static com.kunfei.bookshelf.service.DownloadService.obtainDownloadListAction;
import static com.kunfei.bookshelf.service.DownloadService.progressDownloadAction;
import static com.kunfei.bookshelf.service.DownloadService.removeDownloadAction;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.kunfei.basemvplib.impl.IPresenter;
import com.kunfei.bookshelf.R;
import com.kunfei.bookshelf.base.MBaseActivity;
import com.kunfei.bookshelf.bean.BookChapterBean;
import com.kunfei.bookshelf.bean.BookShelfBean;
import com.kunfei.bookshelf.bean.CacheBookBean;
import com.kunfei.bookshelf.bean.DownloadBookBean;
import com.kunfei.bookshelf.databinding.ActivityRecyclerVewBinding;
import com.kunfei.bookshelf.help.BookshelfHelp;
import com.kunfei.bookshelf.service.DownloadService;
import com.kunfei.bookshelf.utils.theme.ThemeStore;
import com.kunfei.bookshelf.view.adapter.DownloadAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存管理：只列已经缓存过章节的书，显示缓存进度，可对每本书 继续 / 停止 缓存。
 */
public class DownloadActivity extends MBaseActivity<IPresenter> {

    private ActivityRecyclerVewBinding binding;
    private DownloadAdapter adapter;
    private DownloadReceiver receiver;
    /**
     * 任务开始时的已缓存章节数：服务上报的 successCount 是本次任务的完成数，
     * 加上这个基数才是这本书的总缓存进度。
     */
    private final Map<String, Integer> taskBaseCached = new HashMap<>();
    /**
     * 当前正在缓存的书（noteUrl）。列表是异步扫描出来的，扫完会整体替换，
     * 所以扫完要用这个集合把“正在缓存”的状态重新贴回去。
     */
    private final Set<String> activeNoteUrls = new HashSet<>();

    public static void startThis(Activity activity) {
        Intent intent = new Intent(activity, DownloadActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        super.onDestroy();
    }

    /**
     * P层绑定   若无则返回null;
     */
    @Override
    protected IPresenter initInjector() {
        return null;
    }

    /**
     * 布局载入  setContentView()
     */
    @Override
    protected void onCreateActivity() {
        getWindow().getDecorView().setBackgroundColor(ThemeStore.backgroundColor(this));
        binding = ActivityRecyclerVewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        this.setSupportActionBar(binding.toolbar);
        setupActionBar();
    }

    /**
     * 数据初始化
     */
    @Override
    protected void initData() {
        receiver = new DownloadReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(addDownloadAction);
        filter.addAction(removeDownloadAction);
        filter.addAction(progressDownloadAction);
        filter.addAction(obtainDownloadListAction);
        filter.addAction(finishDownloadAction);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void bindView() {
        initRecyclerView();
    }

    private void initRecyclerView() {
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadAdapter(this);
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setItemAnimator(null);
        loadCacheList();
        // 服务在跑的话，同步一下哪些书正在缓存
        DownloadService.obtainDownloadList(this);
    }

    //设置ToolBar
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.cache_manage);
        }
    }

    /**
     * 扫描书架：只保留“已经缓存过章节”的书，并算出缓存进度。
     */
    private void loadCacheList() {
        AsyncTask.execute(() -> {
            List<CacheBookBean> cacheBooks = new ArrayList<>();
            for (BookShelfBean bookShelf : BookshelfHelp.getAllBook()) {
                if (bookShelf.getBookInfoBean() == null) {
                    continue;
                }
                List<BookChapterBean> chapters = BookshelfHelp.getChapterList(bookShelf.getNoteUrl());
                if (chapters.isEmpty()) {
                    continue;
                }
                int cachedCount = 0;
                for (BookChapterBean chapter : chapters) {
                    if (chapter.getHasCache(bookShelf.getBookInfoBean())) {
                        cachedCount++;
                    }
                }
                if (cachedCount <= 0) {
                    // 一章都没缓存的不显示
                    continue;
                }
                cacheBooks.add(new CacheBookBean(
                        bookShelf.getBookInfoBean().getName(),
                        bookShelf.getNoteUrl(),
                        bookShelf.getBookInfoBean().getCoverUrl(),
                        cachedCount,
                        chapters.size()));
            }
            runOnUiThread(() -> {
                if (isFinishing() || adapter == null) {
                    return;
                }
                adapter.setData(cacheBooks);
                if (cacheBooks.isEmpty()) {
                    toast(R.string.cache_empty);
                }
                // 列表整体替换过，重新贴上正在缓存的状态
                for (String noteUrl : activeNoteUrls) {
                    adapter.setDownloading(noteUrl, true);
                }
                // 服务在跑的话再问一次当前任务（在上面之后问，列表已经有了才能标上）
                DownloadService.obtainDownloadList(this);
            });
        });
    }

    /**
     * 继续缓存：从第一个没缓存的章节开始，一直缓存到最后一章。
     */
    public void resumeCache(CacheBookBean cacheBook) {
        if (cacheBook == null || cacheBook.isDownloading()) {
            return;
        }
        AsyncTask.execute(() -> {
            BookShelfBean bookShelf = BookshelfHelp.getBook(cacheBook.getNoteUrl());
            if (bookShelf == null || bookShelf.getBookInfoBean() == null) {
                return;
            }
            List<BookChapterBean> chapters = BookshelfHelp.getChapterList(cacheBook.getNoteUrl());
            int start = -1;
            for (int i = 0; i < chapters.size(); i++) {
                if (!chapters.get(i).getHasCache(bookShelf.getBookInfoBean())) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                return;
            }
            DownloadBookBean downloadBook = new DownloadBookBean();
            downloadBook.setName(bookShelf.getBookInfoBean().getName());
            downloadBook.setNoteUrl(bookShelf.getNoteUrl());
            downloadBook.setCoverUrl(bookShelf.getBookInfoBean().getCoverUrl());
            downloadBook.setStart(start);
            downloadBook.setEnd(chapters.size() - 1);
            downloadBook.setFinalDate(System.currentTimeMillis());
            DownloadService.addDownload(this, downloadBook);
        });
    }

    /**
     * 停止这本书的缓存
     */
    public void stopCache(CacheBookBean cacheBook) {
        if (cacheBook == null) {
            return;
        }
        DownloadService.removeDownload(this, cacheBook.getNoteUrl());
        taskBaseCached.remove(cacheBook.getNoteUrl());
        activeNoteUrls.remove(cacheBook.getNoteUrl());
        adapter.setDownloading(cacheBook.getNoteUrl(), false);
    }

    // 添加菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book_download, menu);
        return super.onCreateOptionsMenu(menu);
    }

    //菜单
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_cancel) {
            // 停止全部缓存
            DownloadService.cancelDownload(this);
            taskBaseCached.clear();
            activeNoteUrls.clear();
            adapter.clearDownloading();
        } else if (id == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private static class DownloadReceiver extends BroadcastReceiver {

        WeakReference<DownloadActivity> ref;

        DownloadReceiver(DownloadActivity activity) {
            this.ref = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadActivity activity = ref.get();
            if (activity == null || activity.adapter == null || intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case addDownloadAction: {
                    DownloadBookBean downloadBook = intent.getParcelableExtra("downloadBook");
                    if (downloadBook != null && downloadBook.getNoteUrl() != null) {
                        CacheBookBean cacheBook = activity.adapter.find(downloadBook.getNoteUrl());
                        if (cacheBook != null) {
                            activity.taskBaseCached.put(downloadBook.getNoteUrl(), cacheBook.getCachedCount());
                        }
                        activity.activeNoteUrls.add(downloadBook.getNoteUrl());
                        activity.adapter.setDownloading(downloadBook.getNoteUrl(), true);
                    }
                    break;
                }
                case progressDownloadAction: {
                    DownloadBookBean downloadBook = intent.getParcelableExtra("downloadBook");
                    if (downloadBook != null && downloadBook.getNoteUrl() != null) {
                        Integer base = activity.taskBaseCached.get(downloadBook.getNoteUrl());
                        activity.adapter.updateProgress(downloadBook.getNoteUrl(),
                                (base == null ? 0 : base) + downloadBook.getSuccessCount());
                        activity.activeNoteUrls.add(downloadBook.getNoteUrl());
                        activity.adapter.setDownloading(downloadBook.getNoteUrl(), true);
                    }
                    break;
                }
                case removeDownloadAction: {
                    DownloadBookBean downloadBook = intent.getParcelableExtra("downloadBook");
                    if (downloadBook != null && downloadBook.getNoteUrl() != null) {
                        activity.taskBaseCached.remove(downloadBook.getNoteUrl());
                        activity.activeNoteUrls.remove(downloadBook.getNoteUrl());
                        activity.adapter.setDownloading(downloadBook.getNoteUrl(), false);
                    }
                    break;
                }
                case obtainDownloadListAction: {
                    ArrayList<DownloadBookBean> downloadBooks = intent.getParcelableArrayListExtra("downloadBooks");
                    if (downloadBooks != null) {
                        for (DownloadBookBean downloadBook : downloadBooks) {
                            if (downloadBook.getNoteUrl() == null) {
                                continue;
                            }
                            CacheBookBean cacheBook = activity.adapter.find(downloadBook.getNoteUrl());
                            if (cacheBook != null) {
                                activity.taskBaseCached.put(downloadBook.getNoteUrl(), cacheBook.getCachedCount());
                            }
                            activity.activeNoteUrls.add(downloadBook.getNoteUrl());
                            activity.adapter.setDownloading(downloadBook.getNoteUrl(), true);
                        }
                    }
                    break;
                }
                case finishDownloadAction: {
                    // 全部任务结束：重新扫一遍，拿到真实进度
                    activity.taskBaseCached.clear();
                    activity.activeNoteUrls.clear();
                    activity.adapter.clearDownloading();
                    activity.loadCacheList();
                    break;
                }
            }
        }
    }
}
