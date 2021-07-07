package com.juju.m3u8converter;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class JavaParser {
    /*解决java不支持AES/CBC/PKCS7Padding模式解密*/
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    public static playlist pls=new playlist();

    public static long getProgress(){
        return pls.is_working?pls.n_segment:0;
    }

    public static void java_parse_m3u8(String[] args)throws Exception{

        pls.target_duration=0;
        pls.start_seq_no=0;
        pls.n_segment=0;
        pls.key=new Key();
        pls.key.method=KeyType.KEY_NONE;
        pls.key.url=null;
        pls.key.value="";
        pls.key.iv="";
        pls.is_working=true;
        if(!args[0].endsWith(".m3u8")&&!args[0].endsWith(".M3U8")){
            System.err.println("is not supported m3u8 file "+args[0]);
            return;
        }

        File m3u8_file=new File(args[0]);
        if(!m3u8_file.exists() || !m3u8_file.canRead()){
            System.err.println("m3u8 file open fail");
            return;
        }
        String workdir=m3u8_file.getCanonicalFile().getParent();
        String video_name=m3u8_file.getName().substring(0,m3u8_file.getName().length()-".m3u8".length());
        File output=new File(workdir+File.separator+video_name+".mp4");
        if(output.exists()){
            output.delete();
        }else{
            output.createNewFile();
        }

        FileReader reader = null;
        BufferedReader br = null;
        FileOutputStream fileOutputStream=new FileOutputStream(output);
        DataOutputStream dataOutputStream=new DataOutputStream(fileOutputStream);
        try {
            reader = new FileReader(args[0]);
            br = new BufferedReader(reader);

            //如果不以#EXTM3U则格式错误
            String line=br.readLine();
            if(!line.startsWith("#EXTM3U")){
                System.err.println("file not start with #EXTM3U");
                return;
            }
            while ((line = br.readLine()) != null){
                //System.out.println(line);
            /*说明是一级m3u8文件，第二行就是二级m3u8文件URI，
            可以包含多个#EXT-X-STREAM-INF:标签，每个后面一行都跟一个可选的二级m3u8流URI*/
                if(line.startsWith("#EXT-X-STREAM-INF:")){
                    System.err.println("file is not second level index file");
                    return;
                }
                /*说明视频有加密*/
                else if(line.startsWith("#EXT-X-KEY:")){
                    System.out.println("file has key");
                    //去除所有空格
                    line=line.substring("#EXT-X-KEY:".length()).replaceAll("[\\s|\t|\r|\n]*", "");
                    pls.key.url=null;
                    pls.key.method=KeyType.KEY_NONE;
                    pls.key.iv="";
                    while(!line.isEmpty()){
                        if(line.startsWith(",")){
                            line=line.substring(",".length());
                        }
                        else if(line.startsWith("METHOD=")){
                            line=line.substring("METHOD=".length());
                            if(line.startsWith("AES-128")){
                                line=line.substring("AES-128".length());
                                System.out.println("key AES 128");
                                pls.key.method=KeyType.KEY_AES_128;
                            }
                            else if(line.startsWith("SAMPLE-AES")){
                                System.out.println("SAMPLE-AES");
                                line=line.substring("SAMPLE-AES".length());
                                pls.key.method=KeyType.KEY_SAMPLE_AES;
                            }
                            else{
                                pls.key.method=KeyType.KEY_NONE;
                                break;
                            }
                        }
                        else if(line.startsWith("URI=")){
                            line=line.substring("URI=".length());
                            if(line.startsWith("\"")){
                                line=line.substring(1);
                                pls.key.url=new URL(line.substring(0,line.indexOf('\"')));
                                System.out.println("key url = "+pls.key.url);
                                line=line.substring(line.indexOf('\"')+1);

                                //get key value
                                File fkey=new File(pls.key.url.toURI());
                                FileReader reader1;
                                if(fkey.canRead()){
                                    reader1=new FileReader(fkey.getAbsoluteFile());
                                }
                                else{
                                    System.err.println("key file "+fkey.getAbsoluteFile()+" don't exist");
                                    return;
                                }
                                BufferedReader br1=new BufferedReader(reader1);
                                pls.key.value=br1.readLine();
                                br1.close();
                                reader1.close();

                                if(pls.key.value.length()!=16){
                                    System.err.println("key length is not 16");
                                    return;
                                }
                                System.out.println("key value is: "+pls.key.value);
                            }
                            else{
                                System.err.println("wrong key uri format");
                                return;
                            }
                        }
                        else if(line.startsWith("IV=")){
                            line=line.substring("IV=".length());
                            if(line.indexOf(',')>0){
                                pls.key.iv=line.substring(0, line.indexOf(','));
                                System.out.println("key iv = "+pls.key.iv);
                                line=line.substring(line.indexOf(',')+1);
                            }
                            else{
                                pls.key.iv=line;
                                System.out.println("key iv = "+pls.key.iv);
                                line="";
                            }
                        }
                    }
                }
                /*分片视频允许的最大时间长度*/
                else if(line.startsWith("#EXT-X-TARGETDURATION:")){
                    pls.target_duration=Integer.parseInt(line.substring("#EXT-X-TARGETDURATION:".length()));
                    System.out.println("target_duration = "+String.valueOf(pls.target_duration));
                }
                /*playList中有相同内容的不同语种/译文的版本*/
                else if(line.startsWith("#EXT-X-MEDIA:")){
                    continue;
                }
                /*指明了出现在播放列表文件中的第一个URI的序列号，可选的，没有则为0*/
                else if(line.startsWith("#EXT-X-MEDIA-SEQUENCE:")){
                    pls.start_seq_no=Integer.parseInt(line.substring("#EXT-X-MEDIA-SEQUENCE:".length()));
                    System.out.println("start media sequence number: "+pls.start_seq_no);
                }
            /*提供关于PlayList的可变性的信息， 这个对整个PlayList文件有效，是可选的，格式如下：
            #EXT-X-PLAYLIST-TYPE:<EVENT|VOD> ：
            如果是VOD，则服务器不能改变PlayList 文件；如果是EVENT，则
            服务器不能改变或是删除PlayList文件中的任何部分，但是可以向该文件中增加新的一行内容。*/
                else if(line.startsWith("#EXT-X-PLAYLIST-TYPE:")){
                    continue;
                }
            /*这个字段是视频的初始化片段, 包含视频头部信息，简而言之,有了这个字段,
            说明后续的每一个分片文件必须和通过这个初始化片段才能完整解读,
            缺少这个初始化片段, M3U8视频根本播放不了，是可选的*/
                else if(line.startsWith("#EXT-X-MAP:")){
                    System.err.println("unable to parse label #EXT-X-MAP");
                    return;
                }
                /*m3u8文件结束符*/
                else if(line.startsWith("#EXT-X-ENDLIST")){
                    break;
                }
            /*指定每个媒体段(ts)的持续时间，第二行跟着ts文件的URI
            EXTINF是一个记录标记，该标记描述了后边URI所指定的媒体文件。每个媒体文件URI前边必须有EXTINF标签。格式如下：
            #EXTINF:<持续时间>,[<标题>]
            DURATION是一个整数，如果版本在3以上可以是浮点数，它指定了媒体文件以秒为单位的持续时间，时间应四舍五入到最接近的整数。
            行内逗号后边的剩余部分是媒体文件的名字，是可选的，该名字是媒体分片的人眼可读的信息标题。*/
                else if(line.startsWith("#EXTINF:")){
                    pls.n_segment++;
                }
            /*EXT-X-BYTERANGE 标签表示一个媒体段是由其 URI 标识的资源的一个子范围。它只适用于下一个
            在播放列表中跟在它后面的 URI 行。其格式为：
            #EXT-X-BYTERANGE:<n>[@<o>]
            其中 n 是一个十进制整数，表示以字节为单位的子范围的长度。如果存在，o 是一个十进制整数，表示从资源开头的字节偏移量。
            如果 o 不存在，则子范围从前一个媒体细分的子范围的下一个字节开始。*/
                else if(line.startsWith("#EXT-X-BYTERANGE:")){
                    System.err.println("unable to parse label #EXT-X-BYTERANGE");
                    return;
                }
                /*其他标签全部跳过不处理*/
                else if(line.startsWith("#")){
                    System.out.println("skip label : "+line);
                    continue;
                }
                /*不是标签，则是媒体文件*/
                else if(!line.isEmpty()){
                    //System.out.println("find media segment : "+line);
                    URL ts_url=new URL(line);
                    File ts_file=new File(ts_url.toURI());
                    FileInputStream fileInputStream=new FileInputStream(ts_file);
                    BufferedInputStream bufferedInputStream=new BufferedInputStream(fileInputStream);
                    int len = bufferedInputStream.available();
                    byte[] ts_content=new byte[len];
                    bufferedInputStream.read(ts_content,0,len);
                    bufferedInputStream.close();
                    fileInputStream.close();

                    if(pls.key.method==KeyType.KEY_NONE){
                        dataOutputStream.write(ts_content);
                    }
                    else if(pls.key.method==KeyType.KEY_AES_128){
                        byte[] ivByte;
                        if(pls.key.iv.isEmpty()){
                            ivByte=new byte[16];
                        }else if(pls.key.iv.startsWith("0x")) {
                            ivByte=hexStringToByteArray(pls.key.iv.substring(2));
                        }else{
                            ivByte=pls.key.iv.getBytes();
                        }
                        if(ivByte.length!=16){
                            System.err.println("iv length is not 16");
                            return;
                        }
                        byte[] de_content=decrypt(ts_content,pls.key.method,pls.key.value,ivByte);
                        dataOutputStream.write(de_content);
                    }
                }
            }
        }finally {
            pls.is_working=false;
            if(reader!=null)reader.close();
            if(br!=null)br.close();
            if(dataOutputStream!=null)dataOutputStream.close();
            if(fileOutputStream!=null)fileOutputStream.close();
        }
    }

    public static byte[] decrypt(byte[] ts_content,KeyType type,String val,byte[] ivByte) throws Exception{
        if(type==KeyType.KEY_AES_128){
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            SecretKeySpec keySpec = new SecretKeySpec(val.getBytes(StandardCharsets.UTF_8), "AES");
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(ivByte);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            return cipher.doFinal(ts_content, 0, ts_content.length);
        }
        else if(type==KeyType.KEY_SAMPLE_AES){
            System.err.println("can't resolve KEY_SAMPLE_AES encrypt");
            return null;
        }
        return null;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        if ((len & 1) == 1) {
            s = "0" + s;
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}

class playlist{
    public long target_duration;
    public long start_seq_no;
    //从1开始编号
    public long n_segment;
    public boolean is_working;
    public Key key;

    public playlist(){
        target_duration=0;
        start_seq_no=0;
        n_segment=0;
        is_working=false;
        key=new Key();
        key.method=KeyType.KEY_NONE;
        key.url=null;
        key.value="";
        key.iv="";
    }
}

enum KeyType {
    KEY_NONE,
    KEY_AES_128,
    KEY_SAMPLE_AES
};

class Key{
    public KeyType method;
    public URL url;
    public String value;
    public String iv;
}
