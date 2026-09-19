package com.kunfei.bookshelf.view.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.kunfei.bookshelf.R;
import com.kunfei.bookshelf.bean.CacheBookBean;
import com.kunfei.bookshelf.view.activity.DownloadActivity;
import com.kunfei.bookshelf.widget.image.CoverImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 缓存管理列表：只显示“已经缓存过章节”的书，展示缓存进度，带 继续 / 停止 两个按钮。
 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.MyViewHolder> {
    private final DownloadActivity activity;
    private final List<CacheBookBean> data = new ArrayList<>();
    private final Object mLock = new Object();

    public DownloadAdapter(DownloadActivity activity) {
        this.activity = activity;
    }

    public void setData(List<CacheBookBean> dataS) {
        synchronized (mLock) {
            data.clear();
            if (dataS != null) {
                data.addAll(dataS);
            }
            // 缓存进度高的排前面
            Collections.sort(data, (o1, o2) -> {
                if (o1.getPercent() != o2.getPercent()) {
                    return o2.getPercent() - o1.getPercent();
                }
                return String.valueOf(o1.getName()).compareTo(String.valueOf(o2.getName()));
            });
        }
        notifyDataSetChanged();
    }

    public CacheBookBean find(String noteUrl) {
        if (TextUtils.isEmpty(noteUrl)) {
            return null;
        }
        synchronized (mLock) {
            for (CacheBookBean bean : data) {
                if (TextUtils.equals(noteUrl, bean.getNoteUrl())) {
                    return bean;
                }
            }
        }
        return null;
    }

    private int indexOf(String noteUrl) {
        if (TextUtils.isEmpty(noteUrl)) {
            return -1;
        }
        synchronized (mLock) {
            for (int i = 0; i < data.size(); i++) {
                if (TextUtils.equals(noteUrl, data.get(i).getNoteUrl())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 缓存进度变化
     */
    public void updateProgress(String noteUrl, int cachedCount) {
        int index = indexOf(noteUrl);
        if (index < 0) {
            return;
        }
        synchronized (mLock) {
            CacheBookBean bean = data.get(index);
            bean.setCachedCount(Math.min(bean.getTotalCount(), Math.max(bean.getCachedCount(), cachedCount)));
        }
        notifyItemChanged(index);
    }

    /**
     * 正在缓存 / 已经停止
     */
    public void setDownloading(String noteUrl, boolean downloading) {
        int index = indexOf(noteUrl);
        if (index < 0) {
            return;
        }
        synchronized (mLock) {
            data.get(index).setDownloading(downloading);
        }
        notifyItemChanged(index);
    }

    public void clearDownloading() {
        synchronized (mLock) {
            for (CacheBookBean bean : data) {
                bean.setDownloading(false);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        CacheBookBean item = data.get(position);
        holder.ivCover.load(item.getCoverUrl(), item.getName(), null);
        holder.tvName.setText(item.getName());
        holder.tvDownload.setText(activity.getString(R.string.cache_progress, item.getCachedCount(), item.getTotalCount()));
        holder.pbCache.setProgress(item.getPercent());

        boolean canResume = !item.isAllCached() && !item.isDownloading();
        holder.ivResume.setEnabled(canResume);
        holder.ivResume.setAlpha(canResume ? 1f : 0.3f);
        holder.ivResume.setOnClickListener(canResume ? view -> activity.resumeCache(item) : null);

        boolean canStop = item.isDownloading();
        holder.ivStop.setEnabled(canStop);
        holder.ivStop.setAlpha(canStop ? 1f : 0.3f);
        holder.ivStop.setOnClickListener(canStop ? view -> activity.stopCache(item) : null);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class MyViewHolder extends RecyclerView.ViewHolder {
        CoverImageView ivCover;
        TextView tvName;
        TextView tvDownload;
        ProgressBar pbCache;
        AppCompatImageView ivResume;
        AppCompatImageView ivStop;

        MyViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDownload = itemView.findViewById(R.id.tv_download);
            pbCache = itemView.findViewById(R.id.pb_cache);
            ivResume = itemView.findViewById(R.id.iv_resume);
            ivStop = itemView.findViewById(R.id.iv_stop);
        }
    }
}
