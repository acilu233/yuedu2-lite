package com.kunfei.bookshelf.bean;

/**
 * 缓存管理列表项：只表示“已经缓存过章节”的书，以及它的缓存进度。
 */
public class CacheBookBean {
    private String name;
    private String noteUrl;
    private String coverUrl;
    private int cachedCount;
    private int totalCount;
    private boolean downloading;

    public CacheBookBean(String name, String noteUrl, String coverUrl, int cachedCount, int totalCount) {
        this.name = name;
        this.noteUrl = noteUrl;
        this.coverUrl = coverUrl;
        this.cachedCount = cachedCount;
        this.totalCount = totalCount;
    }

    public String getName() {
        return name;
    }

    public String getNoteUrl() {
        return noteUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public int getCachedCount() {
        return cachedCount;
    }

    public void setCachedCount(int cachedCount) {
        this.cachedCount = cachedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public void setDownloading(boolean downloading) {
        this.downloading = downloading;
    }

    public boolean isAllCached() {
        return totalCount > 0 && cachedCount >= totalCount;
    }

    public int getPercent() {
        if (totalCount <= 0) {
            return 0;
        }
        return (int) (cachedCount * 100f / totalCount);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CacheBookBean) {
            return android.text.TextUtils.equals(((CacheBookBean) obj).getNoteUrl(), noteUrl);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return noteUrl == null ? 0 : noteUrl.hashCode();
    }
}
