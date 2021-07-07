package com.juju.m3u8converter;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/*处理m3u8文件的核心类*/
public class M3u8File {
    //引入ffmepg库
    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("ffmpeg-cmd");
    }

    //native执行FFmpeg命令
    private static native int exec(int cmdLen, String[] cmd);
    //获取命令执行进度,已转换的帧数
    private static native int getProgress();
    //获取转码速率
    private static native double getSpeed();
    //获取视频信息
    public static native String retrieveInfo(String path);

    public String status;//状态：就绪、正在转换、完成
    public String fullPath;//m3u8文件的路径
    public String convertedFullPath;//转换后应该放置的路径
    public ArrayList<String> videoList;//视频路径列表
    public int fileNum;//小文件数
    public int fps;//每秒的帧数
    public int timeLength;//单位毫秒，0.001s
    public boolean isChecked;//是否被选中
    public int position;//在list中的位置
    String prefix;//视频分片文件路径前缀
    int rotation;//旋转？不太懂
    int width;//画面宽度
    int height;//画面高度
    String videoCodec;//编码器
    int frames;//m3u8视频的总帧数，=timelength*fps
    public M3u8File(String path) throws Exception
    {
        //初始化变量
        status="就绪";
        fullPath=path;
        convertedFullPath=path.replaceAll("(.+)\\.(m|M)3(u|U)8", "$1")+".mp4";
        videoList=new ArrayList<>();
        fileNum=0;
        fps=0;
        timeLength=0;
        isChecked=true;
        rotation=0;
        width=0;
        height=0;
        videoCodec=null;
        try {
            //使用ffmpeg的retrieveInfo函数解析第一个分片视频的信息
            //不管是m3u8文件还是视频文件，都直接用这个函数解析
            String info=retrieveInfo(fullPath);
            Log.d(fullPath, "M3u8File: "+info);
            //Log.d(fullPath, "this.videoList.get(0) "+this.videoList.get(0));
            JSONObject jsonObject = new JSONObject(info);
            rotation=jsonObject.getInt("rotation");
            width=jsonObject.getInt("width");
            height=jsonObject.getInt("height");
            timeLength=jsonObject.getInt("duration");//返回毫秒
            fps=jsonObject.getInt("fps");
            videoCodec=jsonObject.getString("videoCodec");

        } catch (Exception e) {
            this.status="损坏";
            e.printStackTrace();
        }

        FileReader reader = null;
        BufferedReader br = null;
        try{
            reader=new FileReader(path);
            br = new BufferedReader(reader);
            //如果不以#EXTM3U则格式错误
            String line=br.readLine();
            if(!line.startsWith("#EXTM3U")){
                Log.d("err", "M3u8File: file not start with #EXTM3U");
                return;
            }
            while ((line = br.readLine()) != null){
                if(line.startsWith("#EXTINF:")){
                    videoList.add(br.readLine());
                }
                /*m3u8文件结束符*/
                else if(line.startsWith("#EXT-X-ENDLIST")){
                    break;
                }
            }
        }catch (Exception e){
            this.status="损坏";
            e.printStackTrace();
        }finally {
            br.close();
            reader.close();
        }
        fileNum=videoList.size();
        frames=timeLength/1000*this.fps;
        File ts0=new File(videoList.get(0));
        prefix=ts0.getCanonicalFile().getParent();
    }

    public void startConvert(final MainActivity mainActivity)
    {
        if(isChecked==false)return;
        final M3u8Adapter adapter=(M3u8Adapter) mainActivity.listView.getAdapter();
        //ffmpeg -allowed_extensions ALL -i index.m3u8 -c copy new.mp4
        File f=new File(convertedFullPath);
        if(f.exists())f.delete();
        final ConvertListener listener= new ConvertListener() {
            @Override
            public void onProgress(final long progress) {
                if(mainActivity.radioButtonUseFFmpeg.isChecked())
                    status=String.valueOf(frames==0?progress:progress*100/frames)+"%";
                else status=String.valueOf(progress*100/fileNum)+"%";
                //Log.d(status, "onProgress: ");
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onStart() {
                status="运行";
                //Log.d(status, "onStart: ");
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onStop() {
                status="完成";
                //如果勾选删除源文件，就在转换完成后删除源文件
                if(mainActivity.checkBoxDelOld.isChecked())deleteOldFiles();
                //Log.d(status, "onStop: ");
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                        mainActivity.onConvertFineshed();
                    }
                });
            }
        };
        final String[] cmd={"ffmpeg","-allowed_extensions","ALL","-i",fullPath,"-c","copy",convertedFullPath};
        Log.d("FFmpegCmd", "run: " + cmd);
        //使用ffmpeg进行转码
        if(mainActivity.radioButtonUseFFmpeg.isChecked()){
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
                    exec(cmd.length, cmd);
                }
            }.start();
        }
        //使用java进行转码
        else {
            new Thread()
            {
                @Override
                public void run() {
                    while(JavaParser.getProgress()==0);
                    listener.onStart();
                    for(;;)
                    {
                        if(JavaParser.getProgress()==0)
                        {
                            listener.onStop();
                            return;
                        }
                        //Log.d("isWorking:", "getprogress: "+String.valueOf(getProgress()));
                        listener.onProgress(JavaParser.getProgress());
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
                    try {
                        JavaParser.java_parse_m3u8(new String[]{fullPath});
                    }catch (Exception e){
                        Log.d("err", "run: JavaParser.java_parse_m3u8 fail");
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    public interface ConvertListener {
        void onProgress(long progress);
        void onStart();
        void onStop();
    }

    //删除旧的m3u8文件
    public void deleteOldFiles()
    {
        boolean canDel=true;
        for(int i=0;i<videoList.size();i++){
            if(!videoList.get(i).startsWith(prefix)){
                canDel=false;
                break;
            }
        }
        File m3u8Dir=new File(prefix);
        File m3u8File=new File(fullPath);
        if(canDel && m3u8Dir.exists() && m3u8File.exists()){
            m3u8Dir.delete();
            m3u8File.delete();
        }else{
            Log.d("err", "deleteOldFiles: can't delete old file");
        }
    }
}
