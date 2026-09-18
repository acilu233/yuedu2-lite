package com.kunfei.bookshelf.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.drawerlayout.widget.DrawerLayout;

/**
 * 墨水屏专用 DrawerLayout：关闭抽屉一律不做动画。
 *
 * 为什么需要这个类：
 * 代码里调用 closeDrawer(gravity, false) 是瞬时的，但**点击遮罩（抽屉外面的区域）关闭**时，
 * DrawerLayout 内部走的是 closeDrawers(true)，那条路径在外部改不到，所以"滑回"动画一直还在。
 * 这里在触摸事件里拦掉，改成 closeDrawer(child, false)。
 */
public class NoAnimDrawerLayout extends DrawerLayout {

    public NoAnimDrawerLayout(Context context) {
        super(context);
    }

    public NoAnimDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoAnimDrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                try {
                    // 只有真正的抽屉视图才能查询/关闭，内容视图会抛 IllegalArgumentException
                    if (isDrawerOpen(child)) {
                        closeDrawer(child, false);   // 瞬时关闭，不做动画
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 不是抽屉，跳过
                }
            }
        }
        return super.onTouchEvent(event);
    }
}
