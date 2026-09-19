package com.kunfei.bookshelf.view.popupwindow;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.kunfei.bookshelf.R;
import com.kunfei.bookshelf.databinding.PopReadMenuBinding;

public class ReadBottomMenu extends FrameLayout {

    private PopReadMenuBinding binding = PopReadMenuBinding.inflate(LayoutInflater.from(getContext()), this, true);
    private Callback callback;

    public ReadBottomMenu(Context context) {
        super(context);
    }

    public ReadBottomMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReadBottomMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setListener(Callback callback) {
        this.callback = callback;
        bindEvent();
    }

    private void bindEvent() {
        //自动翻页
        binding.llAutoPage.setOnClickListener(view -> callback.autoPage());
        binding.llAutoPage.setOnLongClickListener(view -> {
            callback.toast(R.string.auto_next_page);
            return true;
        });

        //夜间模式
        binding.llNightTheme.setOnClickListener(view -> callback.setNightTheme());
        binding.llNightTheme.setOnLongClickListener(view -> {
            callback.toast(R.string.night_theme);
            return true;
        });

        //上一章
        binding.tvPre.setOnClickListener(view -> callback.skipPreChapter());

        //下一章
        binding.tvNext.setOnClickListener(view -> callback.skipNextChapter());

        //目录
        binding.llCatalog.setOnClickListener(view -> callback.openChapterList());

        //调节
        binding.llAdjust.setOnClickListener(view -> callback.openAdjust());

        //界面
        binding.llFont.setOnClickListener(view -> callback.openReadInterface());

        //设置
        binding.llSetting.setOnClickListener(view -> callback.openMoreSetting());

    }

    public void setTvPre(boolean enable) {
        binding.tvPre.setEnabled(enable);
    }

    public void setTvNext(boolean enable) {
        binding.tvNext.setEnabled(enable);
    }

    public void setAutoPage(boolean autoPage) {
        if (autoPage) {
            binding.ivAutoPage.setImageResource(R.drawable.ic_auto_page_stop);
            binding.ivAutoPage.setContentDescription(getContext().getString(R.string.auto_next_page_stop));
            binding.llAutoPage.setContentDescription(getContext().getString(R.string.auto_next_page_stop));
        } else {
            binding.ivAutoPage.setImageResource(R.drawable.ic_auto_page);
            binding.ivAutoPage.setContentDescription(getContext().getString(R.string.auto_next_page));
            binding.llAutoPage.setContentDescription(getContext().getString(R.string.auto_next_page));
        }
    }

    public void setFabNightTheme(boolean isNightTheme) {
        if (isNightTheme) {
            binding.ivNightTheme.setImageResource(R.drawable.ic_daytime);
        } else {
            binding.ivNightTheme.setImageResource(R.drawable.ic_brightness);
        }
    }

    public interface Callback {
        void autoPage();

        void setNightTheme();

        void skipPreChapter();

        void skipNextChapter();

        void openChapterList();

        void openAdjust();

        void openReadInterface();

        void openMoreSetting();

        void toast(int id);
    }

}
