package cn.wildfire.chat.utils;

import android.content.Context;
import android.content.SharedPreferences;

import cn.wildfire.chat.app.BaseApp;
import cn.wildfire.chat.app.login.model.LoginResult;

public class SpUtil {

   private static SpUtil instance;

   private SharedPreferences sp;


    public static SpUtil getInstance() {
        if (instance == null){
            instance = new SpUtil();
        }
        return instance;
    }

    public static void init(){

   }

    /**
     * 保存config信息
     * @param loginResult
     */
    public void saveConfig(LoginResult loginResult) {
        SharedPreferences sp = BaseApp.getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        sp.edit().putString("id", loginResult.getUserId())
                .putString("token", loginResult.getToken())
                .apply();
    }

    /**
     * 获取用户config信息
     * @return
     */
    public SharedPreferences getConfig() {
        SharedPreferences sharedPreferences = BaseApp.getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        return sharedPreferences;
    }
}
