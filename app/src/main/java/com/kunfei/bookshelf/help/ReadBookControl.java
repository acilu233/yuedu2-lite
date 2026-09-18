//Copyright (c) 2017. 章钦豪. All rights reserved.
package com.kunfei.bookshelf.help;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.DisplayMetrics;

import com.kunfei.bookshelf.MApplication;
import com.kunfei.bookshelf.utils.BitmapUtil;
import com.kunfei.bookshelf.utils.MeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kunfei.bookshelf.widget.page.PageLoader.DEFAULT_MARGIN_WIDTH;

public class ReadBookControl {
    /** 墨水屏：阅读配色只保留两套 —— 0=白底黑字，1=黑底白字 */
    private static final int DEFAULT_BG = 0;
    private static final int BG_COUNT = 2;
    private int textDrawableIndex = DEFAULT_BG;
    private List<Map<String, Integer>> textDrawable;
    private Bitmap bgBitmap;
    private int screenDirection;
    private int textSize;
    private int textColor;
    private boolean bgIsColor;
    private int bgColor;
    private float lineMultiplier;
    private float paragraphSize;
    private int pageMode;
    private Boolean lightNovelParagraph;
    private Boolean hideStatusBar;
    private Boolean hideNavigationBar;
    private String fontPath;
    private int textConvert;
    private int navBarColor;
    private Boolean textBold;
    private Boolean canClickTurn;
    private Boolean canKeyTurn;
    private int CPM;
    private Boolean clickAllNext;
    private Boolean showTitle;
    private Boolean showTimeBattery;
    private Boolean showLine;
    private Boolean darkStatusIcon;
    private int indent;
    private int screenTimeOut;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private int tipPaddingLeft;
    private int tipPaddingTop;
    private int tipPaddingRight;
    private int tipPaddingBottom;
    private float textLetterSpacing;
    private boolean canSelectText;
    public int minCPM = 200;
    public int maxCPM = 2000;
    private int defaultCPM = 500;

    private SharedPreferences preferences;

    private static ReadBookControl readBookControl;

    public static ReadBookControl getInstance() {
        if (readBookControl == null) {
            synchronized (ReadBookControl.class) {
                if (readBookControl == null) {
                    readBookControl = new ReadBookControl();
                }
            }
        }
        return readBookControl;
    }

    private ReadBookControl() {
        preferences = MApplication.getConfigPreferences();
        initTextDrawable();
        updateReaderSettings();
    }

    public void updateReaderSettings() {
        this.lightNovelParagraph = preferences.getBoolean("light_novel_paragraph", false);
        // 墨水屏默认：阅读时全屏（状态栏+导航栏都隐藏）。
        // 调出菜单/弹层时会重新显示系统栏，但阅读窗口始终按整屏布局
        // （见 ReadBookActivity.initImmersionBar 的 translucent 处理），
        // 所以系统栏只覆盖内容，不会挤动正文、不会重新排版。
        this.hideStatusBar = preferences.getBoolean("hide_status_bar", true);
        this.hideNavigationBar = preferences.getBoolean("hide_navigation_bar", true);
        this.indent = preferences.getInt("indent", 2);
        this.textSize = preferences.getInt("textSize", 20);
        this.canClickTurn = preferences.getBoolean("canClickTurn", true);
        this.canKeyTurn = preferences.getBoolean("canKeyTurn", true);
        this.lineMultiplier = preferences.getFloat("lineMultiplier", 1);
        this.paragraphSize = preferences.getFloat("paragraphSize", 1);
        this.CPM = preferences.getInt("CPM", defaultCPM) > maxCPM
                ? minCPM : preferences.getInt("CPM", defaultCPM);
        this.clickAllNext = preferences.getBoolean("clickAllNext", false);
        this.fontPath = preferences.getString("fontPath", null);
        this.textConvert = preferences.getInt("textConvertInt", 0);
        this.textBold = preferences.getBoolean("textBold", false);
        this.showTitle = preferences.getBoolean("showTitle", true);
        this.showTimeBattery = preferences.getBoolean("showTimeBattery", true);
        this.showLine = preferences.getBoolean("showLine", true);
        this.screenTimeOut = preferences.getInt("screenTimeOut", 0);
        this.paddingLeft = preferences.getInt("paddingLeft", DEFAULT_MARGIN_WIDTH);
        this.paddingTop = preferences.getInt("paddingTop", 0);
        this.paddingRight = preferences.getInt("paddingRight", DEFAULT_MARGIN_WIDTH);
        this.paddingBottom = preferences.getInt("paddingBottom", 0);
        this.tipPaddingLeft = preferences.getInt("tipPaddingLeft", DEFAULT_MARGIN_WIDTH);
        this.tipPaddingTop = preferences.getInt("tipPaddingTop", 0);
        this.tipPaddingRight = preferences.getInt("tipPaddingRight", DEFAULT_MARGIN_WIDTH);
        this.tipPaddingBottom = preferences.getInt("tipPaddingBottom", 0);
        // 墨水屏默认：无翻页动画（0=覆盖 1=仿真 2=滑动 3=滚动 4=无动画）
        this.pageMode = preferences.getInt("pageMode", 4);
        this.screenDirection = preferences.getInt("screenDirection", 0);
        this.navBarColor = preferences.getInt("navBarColorInt", 0);
        this.textLetterSpacing = preferences.getFloat("textLetterSpacing", 0);
        this.canSelectText = preferences.getBoolean("canSelectText", false);
        initTextDrawableIndex();
    }

    //阅读背景
    private void initTextDrawable() {
        if (null == textDrawable) {
            textDrawable = new ArrayList<>();
            // 白天：白底黑字
            Map<String, Integer> temp1 = new HashMap<>();
            temp1.put("textColor", Color.parseColor("#000000"));
            temp1.put("bgIsColor", 1);
            temp1.put("textBackground", Color.parseColor("#FFFFFF"));
            temp1.put("darkStatusIcon", 1);
            textDrawable.add(temp1);

            // 夜间：黑底白字
            Map<String, Integer> temp2 = new HashMap<>();
            temp2.put("textColor", Color.parseColor("#FFFFFF"));
            temp2.put("bgIsColor", 1);
            temp2.put("textBackground", Color.parseColor("#000000"));
            temp2.put("darkStatusIcon", 0);
            textDrawable.add(temp2);
        }
    }

    public void initTextDrawableIndex() {
        if (getIsNightTheme()) {
            textDrawableIndex = preferences.getInt("textDrawableIndexNight", 1);
        } else {
            textDrawableIndex = preferences.getInt("textDrawableIndex", DEFAULT_BG);
        }
        // 老版本存过 0~4 的配色索引，现在只剩两套，越界就回到默认（否则阅读页会取色越界）
        if (textDrawableIndex < 0 || textDrawableIndex >= BG_COUNT) {
            textDrawableIndex = DEFAULT_BG;
        }
        initPageStyle();
        setTextDrawable();
    }

    @SuppressWarnings("ConstantConditions")
    private void initPageStyle() {
        int bgCustom = getBgCustom(textDrawableIndex);
        if ((bgCustom == 2 || bgCustom == 3) && getBgPath(textDrawableIndex) != null) {
            bgIsColor = false;
            String bgPath = getBgPath(textDrawableIndex);
            Resources resources = MApplication.getInstance().getResources();
            DisplayMetrics dm = resources.getDisplayMetrics();
            int width = dm.widthPixels;
            int height = dm.heightPixels;
            if (bgCustom == 2) {
                bgBitmap = BitmapUtil.getFitSampleBitmap(bgPath, width, height);
            } else {
                bgBitmap = MeUtils.getFitAssetsSampleBitmap(MApplication.getInstance().getAssets(), bgPath, width, height);
            }
            if (bgBitmap != null) {
                return;
            }
        } else if (getBgCustom(textDrawableIndex) == 1) {
            bgIsColor = true;
            bgColor = getBgColor(textDrawableIndex);
            return;
        }
        bgIsColor = true;
        bgColor = getDrawableSafely(textDrawableIndex).get("textBackground");
    }

    private void setTextDrawable() {
        darkStatusIcon = getDarkStatusIcon(textDrawableIndex);
        textColor = getTextColor(textDrawableIndex);
    }

    public int getTextColor(int textDrawableIndex) {
        // 墨水屏：只用白/黑两套配色，忽略历史保存的自定义文字颜色
        return getDefaultTextColor(textDrawableIndex);
    }

    public void setTextColor(int textDrawableIndex, int textColor) {
        preferences.edit()
                .putInt("textColor" + textDrawableIndex, textColor)
                .apply();
    }

    @SuppressWarnings("ConstantConditions")
    public Drawable getBgDrawable(int textDrawableIndex, Context context, int width, int height) {
        int color;
        try {
            Bitmap bitmap = null;
            switch (getBgCustom(textDrawableIndex)) {
                case 3:
                    bitmap = MeUtils.getFitAssetsSampleBitmap(context.getAssets(), getBgPath(textDrawableIndex), width, height);
                    if (bitmap != null) {
                        return new BitmapDrawable(context.getResources(), bitmap);
                    }
                case 2:
                    bitmap = BitmapUtil.getFitSampleBitmap(getBgPath(textDrawableIndex), width, height);
                    if (bitmap != null) {
                        return new BitmapDrawable(context.getResources(), bitmap);
                    }
                    break;
                case 1:
                    color = getBgColor(textDrawableIndex);
                    return new ColorDrawable(color);
            }
            if (getDrawableSafely(textDrawableIndex).get("bgIsColor") != 0) {
                color = getDrawableSafely(textDrawableIndex).get("textBackground");
                return new ColorDrawable(color);
            } else {
                return getDefaultBgDrawable(textDrawableIndex, context);
            }
        } catch (Exception e) {
            if (getDrawableSafely(textDrawableIndex).get("bgIsColor") != 0) {
                color = getDrawableSafely(textDrawableIndex).get("textBackground");
                return new ColorDrawable(color);
            } else {
                return getDefaultBgDrawable(textDrawableIndex, context);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public Drawable getDefaultBgDrawable(int textDrawableIndex, Context context) {
        if (getDrawableSafely(textDrawableIndex).get("bgIsColor") != 0) {
            return new ColorDrawable(getDrawableSafely(textDrawableIndex).get("textBackground"));
        } else {
            return context.getResources().getDrawable(getDefaultBg(textDrawableIndex));
        }
    }

    public int getBgCustom(int textDrawableIndex) {
        return preferences.getInt("bgCustom" + textDrawableIndex, 0);
    }

    public void setBgCustom(int textDrawableIndex, int bgCustom) {
        preferences.edit()
                .putInt("bgCustom" + textDrawableIndex, bgCustom)
                .apply();
    }

    public String getBgPath(int textDrawableIndex) {
        return preferences.getString("bgPath" + textDrawableIndex, null);
    }

    public void setBgPath(int textDrawableIndex, String bgUri) {
        preferences.edit()
                .putString("bgPath" + textDrawableIndex, bgUri)
                .apply();
    }

    @SuppressWarnings("ConstantConditions")
    public int getDefaultTextColor(int textDrawableIndex) {
        return getDrawableSafely(textDrawableIndex).get("textColor");
    }

    @SuppressWarnings("ConstantConditions")
    private int getDefaultBg(int textDrawableIndex) {
        return getDrawableSafely(textDrawableIndex).get("textBackground");
    }

    /**
     * 取配色表（带兜底）。
     * 老版本存过 0~4 的配色索引，现在只剩两套；任何直接传旧索引的调用都必须回落到默认，
     * 否则 IndexOutOfBoundsException 会让阅读页一打开就崩。
     */
    private Map<String, Integer> getDrawableSafely(int index) {
        initTextDrawable();
        if (index < 0 || index >= textDrawable.size()) {
            index = DEFAULT_BG;
        }
        return textDrawable.get(index);
    }

    public int getBgColor(int index) {
        return preferences.getInt("bgColor" + index, Color.parseColor("#1e1e1e"));
    }

    public void setBgColor(int index, int bgColor) {
        preferences.edit()
                .putInt("bgColor" + index, bgColor)
                .apply();
    }

    private boolean getIsNightTheme() {
        return MApplication.getInstance().isNightTheme();
    }

    public boolean getImmersionStatusBar() {
        return preferences.getBoolean("immersionStatusBar", false);
    }

    public void setImmersionStatusBar(boolean immersionStatusBar) {
        preferences.edit()
                .putBoolean("immersionStatusBar", immersionStatusBar)
                .apply();
    }

    public int getTextSize() {
        return textSize;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
        preferences.edit()
                .putInt("textSize", textSize)
                .apply();
    }

    public int getTextColor() {
        return textColor;
    }

    public boolean bgIsColor() {
        return bgIsColor;
    }

    public Drawable getTextBackground(Context context) {
        if (bgIsColor) {
            return new ColorDrawable(bgColor);
        }
        return new BitmapDrawable(context.getResources(), bgBitmap);
    }

    public int getBgColor() {
        return bgColor;
    }

    public boolean bgBitmapIsNull() {
        return bgBitmap == null || bgBitmap.isRecycled();
    }

    public Bitmap getBgBitmap() {
        return bgBitmap.copy(Bitmap.Config.ARGB_8888, true);
    }

    public int getTextDrawableIndex() {
        return textDrawableIndex;
    }

    public void setTextDrawableIndex(int textDrawableIndex) {
        this.textDrawableIndex = textDrawableIndex;
        if (getIsNightTheme()) {
            preferences.edit()
                    .putInt("textDrawableIndexNight", textDrawableIndex)
                    .apply();
        } else {
            preferences.edit()
                    .putInt("textDrawableIndex", textDrawableIndex)
                    .apply();
        }
        setTextDrawable();
    }

    public void setTextConvert(int textConvert) {
        this.textConvert = textConvert;
        preferences.edit()
                .putInt("textConvertInt", textConvert)
                .apply();
    }

    public void setNavBarColor(int navBarColor) {
        this.navBarColor = navBarColor;
        preferences.edit()
                .putInt("navBarColorInt", navBarColor)
                .apply();
    }

    public int getNavBarColor() {
        return navBarColor;
    }


    public void setTextBold(boolean textBold) {
        this.textBold = textBold;
        preferences.edit()
                .putBoolean("textBold", textBold)
                .apply();
    }

    public void setReadBookFont(String fontPath) {
        this.fontPath = fontPath;
        preferences.edit()
                .putString("fontPath", fontPath)
                .apply();
    }

    public String getFontPath() {
        return fontPath;
    }

    public int getTextConvert() {
        return textConvert == -1 ? 2 : textConvert;
    }

    public Boolean getTextBold() {
        return textBold;
    }

    public Boolean getCanKeyTurn() {
        return canKeyTurn;
    }

    public void setCanKeyTurn(Boolean canKeyTurn) {
        this.canKeyTurn = canKeyTurn;
        preferences.edit()
                .putBoolean("canKeyTurn", canKeyTurn)
                .apply();
    }

    public Boolean getCanClickTurn() {
        return canClickTurn;
    }

    public void setCanClickTurn(Boolean canClickTurn) {
        this.canClickTurn = canClickTurn;
        preferences.edit()
                .putBoolean("canClickTurn", canClickTurn)
                .apply();
    }

    public float getTextLetterSpacing() {
        return textLetterSpacing;
    }

    public void setTextLetterSpacing(float textLetterSpacing) {
        this.textLetterSpacing = textLetterSpacing;
        preferences.edit()
                .putFloat("textLetterSpacing", textLetterSpacing)
                .apply();
    }

    public float getLineMultiplier() {
        return lineMultiplier;
    }

    public void setLineMultiplier(float lineMultiplier) {
        this.lineMultiplier = lineMultiplier;
        preferences.edit()
                .putFloat("lineMultiplier", lineMultiplier)
                .apply();
    }

    public float getParagraphSize() {
        return paragraphSize;
    }

    public void setParagraphSize(float paragraphSize) {
        this.paragraphSize = paragraphSize;
        preferences.edit()
                .putFloat("paragraphSize", paragraphSize)
                .apply();
    }

    public int getCPM() {
        return CPM;
    }

    public void setCPM(int cpm) {
        if (cpm < minCPM || cpm > maxCPM) cpm = defaultCPM;
        this.CPM = cpm;
        preferences.edit()
                .putInt("CPM", cpm)
                .apply();
    }

    public Boolean getClickAllNext() {
        return clickAllNext;
    }

    public void setClickAllNext(Boolean clickAllNext) {
        this.clickAllNext = clickAllNext;
        preferences.edit()
                .putBoolean("clickAllNext", clickAllNext)
                .apply();
    }

    public Boolean getShowTitle() {
        return showTitle;
    }

    public void setShowTitle(Boolean showTitle) {
        this.showTitle = showTitle;
        preferences.edit()
                .putBoolean("showTitle", showTitle)
                .apply();
    }

    public Boolean getShowTimeBattery() {
        return showTimeBattery;
    }

    public void setShowTimeBattery(Boolean showTimeBattery) {
        this.showTimeBattery = showTimeBattery;
        preferences.edit()
                .putBoolean("showTimeBattery", showTimeBattery)
                .apply();
    }

    public Boolean getLightNovelParagraph(){return lightNovelParagraph;}

    public void setLightNovelParagraph(Boolean lightNovelParagraph) {
        this.lightNovelParagraph = lightNovelParagraph;
        preferences.edit()
                .putBoolean("light_novel_paragraph", lightNovelParagraph)
                .apply();
    }

    public Boolean getHideStatusBar() {
        return hideStatusBar;
    }

    public void setHideStatusBar(Boolean hideStatusBar) {
        this.hideStatusBar = hideStatusBar;
        preferences.edit()
                .putBoolean("hide_status_bar", hideStatusBar)
                .apply();
    }

    public Boolean getToLh() {
        return preferences.getBoolean("toLh", false);
    }

    public void setToLh(Boolean toLh) {
        preferences.edit()
                .putBoolean("toLh", toLh)
                .apply();
    }

    public Boolean getHideNavigationBar() {
        return hideNavigationBar;
    }

    public void setHideNavigationBar(Boolean hideNavigationBar) {
        this.hideNavigationBar = hideNavigationBar;
        preferences.edit()
                .putBoolean("hide_navigation_bar", hideNavigationBar)
                .apply();
    }

    public Boolean getShowLine() {
        return showLine;
    }

    public void setShowLine(Boolean showLine) {
        this.showLine = showLine;
        preferences.edit()
                .putBoolean("showLine", showLine)
                .apply();
    }

    public boolean getDarkStatusIcon() {
        return darkStatusIcon;
    }

    @SuppressWarnings("ConstantConditions")
    public boolean getDarkStatusIcon(int textDrawableIndex) {
        return preferences.getBoolean("darkStatusIcon" + textDrawableIndex,
                getDrawableSafely(textDrawableIndex).get("darkStatusIcon") != 0);
    }

    public void setDarkStatusIcon(int textDrawableIndex, Boolean darkStatusIcon) {
        preferences.edit()
                .putBoolean("darkStatusIcon" + textDrawableIndex, darkStatusIcon)
                .apply();
    }

    public int getScreenTimeOut() {
        return screenTimeOut;
    }

    public void setScreenTimeOut(int screenTimeOut) {
        this.screenTimeOut = screenTimeOut;
        preferences.edit()
                .putInt("screenTimeOut", screenTimeOut)
                .apply();
    }

    public int getPaddingLeft() {
        return paddingLeft;
    }

    public void setPaddingLeft(int paddingLeft) {
        this.paddingLeft = paddingLeft;
        preferences.edit()
                .putInt("paddingLeft", paddingLeft)
                .apply();
    }

    public int getPaddingTop() {
        return paddingTop;
    }

    public void setPaddingTop(int paddingTop) {
        this.paddingTop = paddingTop;
        preferences.edit()
                .putInt("paddingTop", paddingTop)
                .apply();
    }

    public int getPaddingRight() {
        return paddingRight;
    }

    public void setPaddingRight(int paddingRight) {
        this.paddingRight = paddingRight;
        preferences.edit()
                .putInt("paddingRight", paddingRight)
                .apply();
    }

    public int getPaddingBottom() {
        return paddingBottom;
    }

    public void setPaddingBottom(int paddingBottom) {
        this.paddingBottom = paddingBottom;
        preferences.edit()
                .putInt("paddingBottom", paddingBottom)
                .apply();
    }

    public int getTipPaddingLeft() {
        return tipPaddingLeft;
    }

    public void setTipPaddingLeft(int tipPaddingLeft) {
        this.tipPaddingLeft = tipPaddingLeft;
        preferences.edit()
                .putInt("tipPaddingLeft", tipPaddingLeft)
                .apply();
    }

    public boolean isCanSelectText() {
        return canSelectText;
    }

    public void setCanSelectText(boolean canSelectText) {
        this.canSelectText = canSelectText;
        preferences.edit()
                .putBoolean("canSelectText", canSelectText)
                .apply();
    }

    public int getTipPaddingTop() {
        return tipPaddingTop;
    }

    public void setTipPaddingTop(int tipPaddingTop) {
        this.tipPaddingTop = tipPaddingTop;
        preferences.edit()
                .putInt("tipPaddingTop", tipPaddingTop)
                .apply();
    }

    public int getTipPaddingRight() {
        return tipPaddingRight;
    }

    public void setTipPaddingRight(int tipPaddingRight) {
        this.tipPaddingRight = tipPaddingRight;
        preferences.edit()
                .putInt("tipPaddingRight", tipPaddingRight)
                .apply();
    }

    public int getTipPaddingBottom() {
        return tipPaddingBottom;
    }

    public void setTipPaddingBottom(int tipPaddingBottom) {
        this.tipPaddingBottom = tipPaddingBottom;
        preferences.edit()
                .putInt("tipPaddingBottom", tipPaddingBottom)
                .apply();
    }

    public int getPageMode() {
        return pageMode;
    }

    public void setPageMode(int pageMode) {
        this.pageMode = pageMode;
        preferences.edit()
                .putInt("pageMode", pageMode)
                .apply();
    }

    public int getScreenDirection() {
        return screenDirection;
    }

    public void setScreenDirection(int screenDirection) {
        this.screenDirection = screenDirection;
        preferences.edit()
                .putInt("screenDirection", screenDirection)
                .apply();
    }

    public void setIndent(int indent) {
        this.indent = indent;
        preferences.edit()
                .putInt("indent", indent)
                .apply();
    }

    public int getIndent() {
        return indent;
    }

    public int getLight() {
        return preferences.getInt("light", getScreenBrightness());
    }

    public void setLight(int light) {
        preferences.edit()
                .putInt("light", light)
                .apply();
    }

    public Boolean getLightFollowSys() {
        return preferences.getBoolean("lightFollowSys", true);
    }

    public void setLightFollowSys(boolean isFollowSys) {
        preferences.edit()
                .putBoolean("lightFollowSys", isFollowSys)
                .apply();
    }

    private int getScreenBrightness() {
        int value = 0;
        ContentResolver cr = MApplication.getInstance().getContentResolver();
        try {
            value = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException ignored) {
        }
        return value;
    }

    public boolean disableScrollClickTurn() {
        return preferences.getBoolean("disableScrollClickTurn", false);
    }
}
