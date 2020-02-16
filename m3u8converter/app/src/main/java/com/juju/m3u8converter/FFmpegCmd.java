package com.juju.m3u8converter;

import android.util.Log;
import java.util.ArrayList;

public class FFmpegCmd {
    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("ffmpeg-cmd");
    }

    //执行FFmpeg命令
    private static native int exec(int cmdLen, String[] cmd);

    //获取命令执行进度,已转换的帧数
    private static native int getProgress();

    //获取转码速率
    private static native double getSpeed();

    //获取视频信息
    public static native String retrieveInfo(String path);

    /**
     * 执行FFMpeg命令， 同步模式
     *
     * @param cmd
     * @return
     */
    public static void run(final ArrayList<String> cmd) {
        Log.d("FFmpegCmd", "run: " + cmd.toString());
        exec(cmd.size(), cmd.toArray(new String[cmd.size()]));
    }

    /**
     * 执行FFMpeg命令， 异步模式
     *
     * @param cmd
     * @return
     */
    public static void run(final ArrayList<String> cmd, final ConvertListener listener) {
        Log.d("FFmpegCmd", "run: " + cmd.toString());
        new Thread()
        {
            @Override
            public void run() {
                while(getProgress()==0);
                listener.onStart();
                for(;;)
                {
                    if(getProgress()==0)
                    {
                        listener.onStop();
                        return;
                    }
                    //Log.d("isWorking:", "getprogress: "+String.valueOf(getProgress()));
                    listener.onProgress(getProgress());
                    try{
                        sleep(500);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
        new Thread()
        {
            @Override
            public void run()
            {
                exec(cmd.size(), cmd.toArray(new String[cmd.size()]));
            }
        }.start();
    }

    public static void transcode(String srcPath, String outPath,ConvertListener listener) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");

        cmd.add("-allowed_extensions");
        cmd.add("ALL");

        cmd.add("-i");
        cmd.add(srcPath);

        cmd.add("-c");
        cmd.add("copy");

        cmd.add(outPath);
        run(cmd,listener);
    }

    public interface ConvertListener {
        void onProgress(int progress);
        void onStart();
        void onStop();
    }
}
