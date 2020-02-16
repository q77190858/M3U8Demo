package com.juju.m3u8converter;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/*处理m3u8文件的核心类*/
public class M3u8File {
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
    Map<String,String> keyMap;//如果加密则keyMap.size不为0
    public M3u8File(String path)
    {
        //初始化变量
        this.status="就绪";
        this.fullPath=path;
        this.convertedFullPath=path.replaceAll("(.+)\\.(m|M)3(u|U)8", "$1")+".mp4";
        this.videoList=new ArrayList<>();
        this.fileNum=0;
        this.fps=0;
        this.timeLength=0;
        this.isChecked=true;
        this.rotation=0;
        this.width=0;
        this.height=0;
        this.videoCodec=null;
        this.keyMap=new HashMap<>();
        //读取m3u8文件内容
        BufferedReader reader = null;
        try {
            FileReader freader = new FileReader(path);
            reader = new BufferedReader(freader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String line = null;
        String absp = null, datadir = null;
        //循环读取每一行
        while (true)
        {
            try {
                line = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(line.startsWith("#"))//#开头说明为标签
            {
                if (line == null || line.equals("#EXT-X-ENDLIST"))
                    break;
                    // 如果有#EXT-X-KEY匹配，说明是加密的视频，获得keymap
                else if (line.matches("#EXT-X-KEY:.*")) {
                    String s = line.replaceAll("#EXT-X-KEY:(.*)", "$1");
                    // System.out.println("key:"+s);
                    String[] sarray = s.split(",");
                    for (int i = 0; i < sarray.length; i++) {
                        String[] kv = sarray[i].split("=");
                        if (kv[0].equals("URI")) {
                            String kpath = kv[1].replaceAll("\"(\\w://)?(.*)\"", "$2");
                            kv[1] = kpath;
                        }
                        keyMap.put(kv[0], kv[1]);
                    }
                }
//                else if (line.matches("#EXTINF:.*")) {//放弃解析#EXTINF:来获得视频时长，因为inf后跟时长是可选的
//                    // 计算视频时间长度
//                    String s = line.replaceAll("#EXTINF:(.*),", "$1");
//                    double tlength = Double.valueOf(s);
//                    this.timeLength += tlength;
//                    // 读取视频路径
//                    try {
//                        line = reader.readLine();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }
            else if(line.matches("(\\w+://)?(/.*)"))//满足此正则就是文件的URI
            {
                // 匹配获得单个视频文件的绝对路径
                absp = line.replaceAll("(\\w+://)?(/.*)", "$2");
                File f=new File(absp);
                if(!f.exists()||!f.canRead())status="损坏";
                this.videoList.add(absp);
            }
        }
        try {
            reader.close();
            //使用ffmpeg的retrieveInfo函数解析第一个分片视频的信息
            //不管是m3u8文件还是视频文件，都直接用这个函数解析
            String info=FFmpegCmd.retrieveInfo(fullPath);
//            if(keyMap.size()==0)
//                info=FFmpegCmd.retrieveInfo(videoList.get(0));//未加密，直接读取第一个分片文件
//            else//加密了，直接输入m3u8文件
//            {
//                ArrayList<String> cmd=new ArrayList<>();
//                cmd.add("ffmpeg");
//                cmd.add("-allowed_extensions");
//                cmd.add("ALL");
//                cmd.add("-i");
//                cmd.add(fullPath);
//                FFmpegCmd.run(cmd);
//                info=FFmpegCmd.retrieveInfo(fullPath);
//            }
            Log.d(fullPath, "M3u8File: "+info);
            //Log.d(fullPath, "this.videoList.get(0) "+this.videoList.get(0));
            JSONObject jsonObject = new JSONObject(info);
            this.rotation=jsonObject.getInt("rotation");
            this.width=jsonObject.getInt("width");
            this.height=jsonObject.getInt("height");
            this.timeLength=jsonObject.getInt("duration");//返回毫秒
            this.fps=jsonObject.getInt("fps");
            this.videoCodec=jsonObject.getString("videoCodec");

        } catch (Exception e) {
            this.status="损坏";
            e.printStackTrace();
        }
        this.fileNum=this.videoList.size();
        this.frames=this.timeLength/1000*this.fps;
        int i=this.videoList.get(0).lastIndexOf("/");
        i=(i==-1?0:i);
        this.prefix=this.videoList.get(0).substring(0,i);
    }

    public void startConvert(final MainActivity mainActivity)
    {
        if(isChecked==false)return;
        final M3u8Adapter adapter=(M3u8Adapter) mainActivity.listView.getAdapter();
        //ffmpeg -allowed_extensions ALL -i index.m3u8 -c copy new.mp4
        File f=new File(convertedFullPath);
        if(f.exists())f.delete();
        FFmpegCmd.transcode(fullPath, convertedFullPath, new FFmpegCmd.ConvertListener() {
            @Override
            public void onProgress(final int progress) {
                status=String.valueOf(frames==0?progress:progress*100/frames)+"%";
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
        });
        //Log.d("retrieveInfo", "startConvert: "+FFmpegCmd.retrieveInfo(fullPath));
    }
    //删除旧的m3u8文件
    public void deleteOldFiles()
    {
        File f=null;
        if(keyMap.get("URI")!=null)
        {
            f=new File(keyMap.get("URI"));
            f.delete();
        }
        for(int i=0;i<videoList.size();i++)
        {
            f=new File(videoList.get(i));
            f.delete();
        }
        if(f!=null&&f.getParentFile().listFiles().length==0)f.getParentFile().delete();
        f=new File(fullPath);
        f.delete();
    }
}
