# M3U8批量转换开发总结

## 开发时间表  

+ 2020年1月13日  
开始java层开发，设计demoUI，制作文件搜索功能
+ 2020年1月24日  
开始cpp层开发，尝试使用ndk编译ffmpeg.so，尝试使用jni调用ffmpeg转码功能
+ 2020年2月10日  
开始制作最终版UI，结合前期完成的cpp层应用内核，制作第一版M3U8批量转换
+ 2020年2月16日  
发布第一版app  

## 背景知识  

>M3U8 是 Unicode 版本的 M3U，用 UTF-8 编码。"M3U" 和 "M3U8" 文件都是苹果公司使用的 HTTP Live Streaming（HLS） 协议格式的基础，这种协议格式可以在 iPhone 和 Macbook 等设备播放。

这种格式将一整段视频分割为多个数秒的ts分片文件，便于网络传输。其中还可以将ts分片文件加密。由于chrome开始放弃对flash的支持，最近互联网上很多视频都是使用m3u8格式进行推流的。除了分片文件外还有一个.m3u8格式的文件列表，用于保存所有分片文件的URI。  

目前UC浏览器和QQ浏览器都支持将m3u8视频流缓存到本地，其中QQ浏览器还可以直接转码为MP4，但是只能一个一个转换，不能批量转换，因此我就开发了这个app

## app原理分析  

我们都知道ffmpeg是一个很强大的命令行音频视频处理工具，可以用于各种视频格式的相互转换。具体地我们要把一个m3u8视频文件转化成mp4格式的，命令行这样使用：

```bash
ffmpeg -allowed_extensions ALL -i index.m3u8 -c copy new.mp4
#-allowed_extensions ALL启用对m3u8格式的支持，否则会显示不识别的文件格式
#-i index.m3u8 设置输入文件
#-c copy 复制产生新的文件
#new.mp4 指定输出文件名
```

所以我们app开发的目的就是使用java-jni调用c层的ffmpeg函数，实现视频格式的转换

## ffmpeg结构分析  

按照网络上的教程编译出来的ffmpeg项目有两个可执行文件`ffmpeg`和`ffprobe`，其中`ffmpeg`用来给视频转码，`ffprobe`用来查看视频文件的信息  

这两个executable文件都需要使用7个动态链接库so文件

```bash
libavcodec.so #编解码
libavdevice.so #设备抽象
libavfilter.so #滤镜
libavformat.so #各种格式的支持
libavutil.so #项目自用工具集
libswresample.so #重新采样
libswscale.so #尺寸缩放
```

这些库我们使用编译好的现成的就可以了，不需要在android-studio中编译，网络上还有自己把这7个库编译成一个`ffmpeg.so`库文件的，这样在使用的时候也就方便了

我们只需要修改ffmpeg可执行文件的源码，实现jni调用ffmpeg的main函数(当然要改名)，进行视频转码、获得转码进度和获得视频信息

## 创建android-studio NDK项目

不要选android-studio自带的c++项目，不好用，我们先创建一个empty activity，然后自己导入c++模块  

在app模块的build.gradle中加入

```groovy
android {
    ******
    defaultConfig {
        ******
        externalNativeBuild {
            cmake {
                cppFlags ""
            }
        }
        //指定要ndk编译的目标平台
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a'
        }
    }
    //指定cpp文件夹的路径
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/cpp/ffmpeg/prebuilt/']
        }
    }
    //指定cmake文件的路径
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
    }
}
```

修改完成后，将cpp项目放到指定位置，同步一下gradle，android-studio就会出现cpp文件夹和jniLibs模块

## 设计demoUI界面，完成m3u8文件的搜索功能

这里主要是java层的开发，不会的百度谷歌一下就可，直接上界面图

![m3u8demo界面图](m3u8demo.jpeg)

## java层调用c层实现m3u8视频的格式转换

这里我定义了一个工具类`FFmpegCmd`，用于模拟执行ffmpeg命令行，调用c层ffmpeg实现转换

+ 首先加载c库

```java
    static {
        //首先加载ffmpeg库(我将7个库编译成了一个)
        System.loadLibrary("ffmpeg");
        //ffmpeg-cmd实际上就是ffmpeg这个二进制文件的源码构成的项目,包含这些文件和ffmpeg项目的很多头文件(我直接将所有的头文件都拷贝了进去)
        //cmdutils.c cmdutils.h cmdutils_opencl.c config.h ffmpeg.c
        //ffmpeg.h ffmpeg-cmd.cpp ffmpeg_filter.c ffmpeg_hw.c ffmpeg_opt.c
        System.loadLibrary("ffmpeg-cmd");
    }
```

+ 调用转码函数

```java
    //模拟命令行执行的转码函数
    //srcPath：m3u8文件路径
    //outPath：输出文件路径
    //listener：回调接口用于获得进度
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
```

+ run函数调用native层`ffmpeg-cmd.cpp`的`exec`函数，`exec`函数调用`ffmpeg.c`中的`ffmpeg_exec`执行转码(`ffmpeg_exec`就是原来的main函数)

```java
exec(cmd.size(), cmd.toArray(new String[cmd.size()]));
```

```cpp
extern "C"
JNIEXPORT jint JNICALL
Java_com_juju_m3u8converter_FFmpegCmd_exec(JNIEnv *env, jclass type, jint cmdLen,jobjectArray cmd) {
    //set java vm
    JavaVM *jvm = NULL;
    env->GetJavaVM(&jvm);
    av_jni_set_java_vm(jvm, NULL);
    //处理传入的参数，将jstring转为char *
    char *argCmd[cmdLen] ;
    jstring buf[cmdLen];
    for (int i = 0; i < cmdLen; ++i) {
        buf[i] = static_cast<jstring>(env->GetObjectArrayElement(cmd, i));
        char *string = const_cast<char *>(env->GetStringUTFChars(buf[i], JNI_FALSE));
        argCmd[i] = string;
        LOGD("argCmd=%s",argCmd[i]);
    }
    //执行ffmpeg_exec并获得返回值
    int retCode = ffmpeg_exec(cmdLen, argCmd);
    LOGD("ffmpeg-invoke: retCode=%d",retCode);
    return retCode;
}
```

这样确实能转码成功，但是看不到转换进度

## 获得视频总帧数等信息

`ffmpeg_exec`中可获得已经转换的视频的帧数，所以还要获得视频的总帧数，才能算出转换进度  

总帧数又可以用fps和视频的总时间长度相乘获得，因此就是要获得视频的信息

```cpp
extern "C"
JNIEXPORT jstring JNICALL
Java_com_juju_m3u8converter_FFmpegCmd_retrieveInfo(JNIEnv *env, jclass clazz, jstring _path) {
    //此函数的执行和ffmpeg_exec内部执行流程类似，都是先解析传入的视频信息
    const char* path=env->GetStringUTFChars(_path, JNI_FALSE);
    AVFormatContext* ctx = nullptr;
    //ffmpeg初始化执行环境
    av_register_all();
    avcodec_register_all();
    avfilter_register_all();
    avformat_network_init();
    //相当于是执行了没有带传入参数的ffmpeg_exec，所以要自己加入参数，否则报错文件不识别
    //初始化词典，加入参数-allowed_extensions ALL
    //AVDictionary dic;
    AVDictionary *format_opts= nullptr;//初始化为空就行了
    av_dict_set(&format_opts, "allowed_extensions", "ALL", 0);//会自动分配内存
    //初始化ic,不能为空，否则m3u8文件无法解析
    /* get default parameters from command line */
    const char* filename=path;
    AVFormatContext* ic = avformat_alloc_context();
    if (!ic) {
        print_error(filename, AVERROR(ENOMEM));
        return nullptr;
    }
    ic->flags |= AVFMT_FLAG_KEEP_SIDE_DATA;
    ic->flags |= AVFMT_FLAG_NONBLOCK;
    //ic->interrupt_callback = int_cb;//暂停回调函数先不使用
    //扫描全部的ts流的"Program Map Table"表
    if (!av_dict_get(format_opts, "scan_all_pmts", NULL, AV_DICT_MATCH_CASE)) {
        av_dict_set(&format_opts, "scan_all_pmts", "1", AV_DICT_DONT_OVERWRITE);
        //scan_all_pmts_set = 1;
    }
    //打开了输入文件
    ctx=ic;
    int ret = avformat_open_input(&ctx, path, nullptr, &format_opts);
    if (ret != 0) {
        LOGE("avformat_open_input() open failed! path:%s, err:%s", path, av_err2str(ret));
        return nullptr;
    }
    //获取了输入视频文件的信息
    ret=avformat_find_stream_info(ctx, nullptr);
    if (ret < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot find stream information\n");
        LOGE("avformat_find_stream_info() failed! path:%s, err:%s", path, av_err2str(ret));
        return nullptr;
    }
    env->ReleaseStringUTFChars(_path,path);
    int nStreams = ctx->nb_streams;

    AVStream **pStream = ctx->streams;
    AVStream *vStream = nullptr;

    for (int i = 0; i < nStreams; i++) {
        //if (pStream[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
        if (pStream[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            vStream = pStream[i];
        }
    }
    //对视频文件信息整理输出
    int width = 0;
    int height = 0;
    int rotation = 0;
    long fps = 0;
    char *vCodecName = nullptr;
    if(vStream!=nullptr){
        width = vStream->codecpar->width;
        height = vStream->codecpar->height;
        rotation = static_cast<int>(get_rotation(vStream));
        int num = vStream->avg_frame_rate.num;
        int den = vStream->avg_frame_rate.den;
        if (den > 0) {
            fps = lround(num * 1.0 / den);
        }

        const char *codecName = avcodec_get_name(vStream->codecpar->codec_id);
        vCodecName = const_cast<char *>(codecName);
    }

    long bitrate = ctx->bit_rate;
    long duration = ctx->duration / 1000;//ms
    //最后一定记得关闭，不然会出错
    avformat_close_input(&ctx);
    //返回json格式字符串
    std::ostringstream buffer;
    buffer << "{\"rotation\":"<<rotation<<",\"width\":"<<width<<",\"height\":"<<height<<",\"duration\":"<<duration<<",\"bitrate\":"<< bitrate<<",\"fps\":"<<fps<<R"(,"videoCodec":")"<<vCodecName<<"\"}";
    std::string result = buffer.str();
    return env->NewStringUTF(result.c_str());
}
```
## 对一个坑的回忆

这个地方我踩到了一个坑，根据ffmpeg官方文档

>avformat_open_input  
avformat_find_stream_info  
avformat_close_input

一套下来就完成了，但是程序一运行就崩溃，说不识别m3u8格式的文件，我就觉得很奇怪，你直接`ffmpeg_exec`就没问题，我照着`ffmpeg_exec`写的`retrieveInfo`就报错了

想来想去搞不懂，看官方文档也没说清楚，最后用debug，执行一个ffmpeg转码命令，在ffmpeg_exec中加断点，才明白要怎么在cpp代码中加传入的参数

```cpp
    //初始化词典，加入参数-allowed_extensions ALL
    //AVDictionary dic;
    AVDictionary *format_opts= nullptr;//初始化为空就行了
    av_dict_set(&format_opts, "allowed_extensions", "ALL", 0);//会自动分配内存
```

这个坑解决了，就可以获得文件的总帧数了

## 获得转码进度

启动一个java新线程，循环调用`getProgress`方法获得实时已转换帧数，再启动一个新线程执行`exec`转码方法，防止阻塞主线程

```java
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
```

## 读取m3u8文件，获得分片文件个数

用java代码解析m3u8文件也是可以的，要是java能实现解密，就可以用纯java实现m3u8格式的转码，但是我研究了几天，发现java8对`aes-128`的加密解密和之前的不一样，我写了几个demo都没有成功，所以就放弃了

但是目前可以获得分片文件数，但是这也不是必要的

```java
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
    }
}
```

## 感悟

1. 一定要用google搜索，看英文文档，这是成为大神的必须之路

2. 不懂的话多看看别人的代码，看看大神们是怎么写的，他们不一定会像我这样写技术文档，知识都在代码中

3. 这次开发经历，学习了android-studio的使用，学习了android-java知识，学习了ffmpeg的编译和调用，历时两个月，过得很充实，最后做出了成果，很有成就感

## 项目代码和demo.apk查看和下载
[https://github.com/q77190858/m3u8demo](https://github.com/q77190858/m3u8demo)