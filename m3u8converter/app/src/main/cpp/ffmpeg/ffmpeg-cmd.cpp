//
// Created by xulin on 2018/6/28 0028.
//

#include <jni.h>
#include <string>
#include "android/log.h"
#include <sstream>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "ffmpeg-cmd", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ffmpeg-cmd", __VA_ARGS__)

extern "C"{
#include "ffmpeg.h"
#include "libavcodec/jni.h"
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_juju_m3u8converter_M3u8File_exec(JNIEnv *env, jclass type, jint cmdLen,
                                             jobjectArray cmd) {
    //set java vm
    JavaVM *jvm = NULL;
    env->GetJavaVM(&jvm);
    av_jni_set_java_vm(jvm, NULL);

    char *argCmd[cmdLen] ;
    jstring buf[cmdLen];

    for (int i = 0; i < cmdLen; ++i) {
        buf[i] = static_cast<jstring>(env->GetObjectArrayElement(cmd, i));
        char *string = const_cast<char *>(env->GetStringUTFChars(buf[i], JNI_FALSE));
        argCmd[i] = string;
        LOGD("argCmd=%s",argCmd[i]);
    }

    int retCode = ffmpeg_exec(cmdLen, argCmd);
    LOGD("ffmpeg-invoke: retCode=%d",retCode);

    return retCode;

}

extern "C"
JNIEXPORT jint JNICALL
Java_com_juju_m3u8converter_M3u8File_getProgress(JNIEnv *env, jclass clazz) {
    return get_progress();
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_juju_m3u8converter_M3u8File_getSpeed(JNIEnv *env, jclass clazz) {
    return get_speed();
}
/*说明：
 * 本函数经过juju改造后，已经可以支持m3u8文件格式的直接解析
 * 邮箱：296768324@qq.com*/
extern "C"
JNIEXPORT jstring JNICALL
Java_com_juju_m3u8converter_M3u8File_retrieveInfo(JNIEnv *env, jclass clazz, jstring _path) {
    const char* path=env->GetStringUTFChars(_path, JNI_FALSE);
    AVFormatContext* ctx = nullptr;

    av_register_all();
    avcodec_register_all();
    avfilter_register_all();
    avformat_network_init();

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

    ctx=ic;
    int ret = avformat_open_input(&ctx, path, nullptr, &format_opts);
    if (ret != 0) {
        LOGE("avformat_open_input() open failed! path:%s, err:%s", path, av_err2str(ret));
        return nullptr;
    }
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

    avformat_close_input(&ctx);
    std::ostringstream buffer;
    buffer << "{\"rotation\":"<<rotation<<",\"width\":"<<width<<",\"height\":"<<height<<",\"duration\":"<<duration<<",\"bitrate\":"<< bitrate<<",\"fps\":"<<fps<<R"(,"videoCodec":")"<<vCodecName<<"\"}";
    std::string result = buffer.str();
    return env->NewStringUTF(result.c_str());
}