package com.kunfei.bookshelf.help;

/**
 * 待处理跳转。
 *
 * 从目录（或书签）里点章节时，阅读页可能已经被系统回收（低内存设备，
 * 或开发者选项里的“不保留活动”），这时 RxBus 事件发出去没有人接收，
 * 跳转就会丢失。这里用进程内的静态记录把跳转目标带到阅读页重新创建之后。
 */
public class PendingJumpHelp {
    private static String noteUrl;
    private static int chapterIndex = -1;
    private static int pageIndex = 0;

    public static synchronized void set(String url, int chapter, int page) {
        noteUrl = url;
        chapterIndex = chapter;
        pageIndex = page;
    }

    /**
     * 取出并清掉这本书的待处理跳转，没有就返回 null。
     */
    public static synchronized int[] consume(String url) {
        if (url != null && url.equals(noteUrl) && chapterIndex >= 0) {
            int[] result = new int[]{chapterIndex, pageIndex};
            clear();
            return result;
        }
        return null;
    }

    public static synchronized void clear() {
        noteUrl = null;
        chapterIndex = -1;
        pageIndex = 0;
    }
}
