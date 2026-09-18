//Copyright (c) 2017. 章钦豪. All rights reserved.
package com.kunfei.bookshelf.view.popupwindow;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import com.kunfei.bookshelf.databinding.PopReadAdjustBinding;
import com.kunfei.bookshelf.help.ReadBookControl;

public class ReadAdjustPop extends FrameLayout {

    private PopReadAdjustBinding binding = PopReadAdjustBinding.inflate(LayoutInflater.from(getContext()), this, true);
    private Activity activity;
    private ReadBookControl readBookControl = ReadBookControl.getInstance();

    public ReadAdjustPop(Context context) {
        super(context);
        init(context);
    }

    public ReadAdjustPop(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ReadAdjustPop(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        binding.vwBg.setOnClickListener(null);
    }

    public void setListener(Activity activity) {
        this.activity = activity;
        initData();
        bindEvent();
        initLight();
    }

    public void show() {
        initLight();
    }

    private void initData() {
        //CPM范围设置 每分钟阅读200字到2000字 默认500字/分钟
        binding.hpbClick.setMax(readBookControl.maxCPM - readBookControl.minCPM);
        binding.hpbClick.setProgress(readBookControl.getCPM());
        binding.tvAutoPage.setText(String.format("%sCPM", readBookControl.getCPM()));
    }

    private void bindEvent() {
        //亮度调节
        binding.llFollowSys.setOnClickListener(v -> {
            binding.scbFollowSys.setChecked(!binding.scbFollowSys.isChecked(), true);
        });
        binding.scbFollowSys.setOnCheckedChangeListener((checkBox, isChecked) -> {
            readBookControl.setLightFollowSys(isChecked);
            if (isChecked) {
                //跟随系统
                binding.hpbLight.setEnabled(false);
                setScreenBrightness();
            } else {
                //不跟随系统
                binding.hpbLight.setEnabled(true);
                setScreenBrightness(readBookControl.getLight());
            }
        });
        binding.hpbLight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (!readBookControl.getLightFollowSys()) {
                    readBookControl.setLight(i);
                    setScreenBrightness(i);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //自动翻页阅读速度(CPM)
        binding.hpbClick.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                binding.tvAutoPage.setText(String.format("%sCPM", i + readBookControl.minCPM));
                readBookControl.setCPM(i + readBookControl.minCPM);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    public void setScreenBrightness() {
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        activity.getWindow().setAttributes(params);
    }

    public void setScreenBrightness(int value) {
        if (value < 1) value = 1;
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.screenBrightness = value * 1.0f / 255f;
        activity.getWindow().setAttributes(params);
    }

    public void initLight() {
        binding.hpbLight.setProgress(readBookControl.getLight());
        binding.scbFollowSys.setChecked(readBookControl.getLightFollowSys());
        if (!readBookControl.getLightFollowSys()) {
            setScreenBrightness(readBookControl.getLight());
        }
    }

}
