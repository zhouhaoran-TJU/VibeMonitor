package com.vibecoding.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class WidgetPinCallbackReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "小组件添加请求已由桌面处理", Toast.LENGTH_LONG).show();
    }
}
