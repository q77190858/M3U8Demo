package com.juju.m3u8converter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;

public class M3u8Adapter extends ArrayAdapter<M3u8File>
        implements CompoundButton.OnCheckedChangeListener
{
    private int resourceId;
    List<M3u8File> m3u8List;
    public M3u8Adapter(Context context, int textViewResourceId, List<M3u8File> obj)
    {
        super(context, textViewResourceId, obj);
        resourceId = textViewResourceId;
        m3u8List=obj;
    }
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        M3u8File m3u8 = getItem(position);//获取当前项的Weather实例
        m3u8.position=position;
        //LayoutInflater的inflate()方法接收3个参数：需要实例化布局资源的id、ViewGroup类型视图组对象、false
        //false表示只让父布局中声明的layout属性生效，但不会为这个view添加父布局
        View view = LayoutInflater.from(getContext()).inflate(resourceId, parent, false);
        //更新view的路径
        TextView textViewFullPath = (TextView) view.findViewById(R.id.fullpath);
        textViewFullPath.setText(m3u8.fullPath);
        //更新view的序号
        TextView textViewNum=(TextView)view.findViewById(R.id.num);
        textViewNum.setText(String.valueOf(position+1));
        //更新复选框
        CheckBox checkBox=(CheckBox)view.findViewById(R.id.checkBox);
        checkBox.setChecked(m3u8.isChecked);
        checkBox.setOnCheckedChangeListener(this);
        //更新状态
        TextView textViewStatus=(TextView)view.findViewById(R.id.status);
        textViewStatus.setText(m3u8.status);
        //时间长度
        TextView textViewTimeLenth=(TextView)view.findViewById(R.id.timeLength);
        textViewTimeLenth.setText(formatTime(m3u8.timeLength/1000));
        //画面宽高
        TextView textViewWidthHeight=(TextView)view.findViewById(R.id.widthHeight);
        textViewWidthHeight.setText(String.valueOf(m3u8.width)+"x"+String.valueOf(m3u8.height));
        //编码器
        TextView textViewCodec=(TextView)view.findViewById(R.id.codec);
        textViewCodec.setText(m3u8.videoCodec);
        //分片文件总数
        TextView textViewFileNum=(TextView)view.findViewById(R.id.fileNum);
        textViewFileNum.setText(String.valueOf(m3u8.fileNum)+"个文件");
        return view;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        LinearLayout layout= (LinearLayout) buttonView.getParent();
        TextView textViewNum=(TextView) layout.getChildAt(0);
        M3u8File f= m3u8List.get(Integer.parseInt(textViewNum.getText().toString())-1);
        if(isChecked)
        {
            f.isChecked=true;
            //Log.d(textViewNum.getText().toString(), String.valueOf(isChecked));
        }
        else
        {
            f.isChecked=false;
            //Log.d(textViewNum.getText().toString(), String.valueOf(isChecked));
        }
    }

    /**
     * 将秒转化为 HH:mm:ss 的格式
     *
     * @param time 秒
     * @return
     */
    DecimalFormat decimalFormat;
    private String formatTime(int time) {
        if(decimalFormat == null){
            decimalFormat = new DecimalFormat("00");
        }
        String hh = decimalFormat.format(time / 3600);
        String mm = decimalFormat.format(time % 3600 / 60);
        String ss = decimalFormat.format(time % 60);
        return hh + ":" + mm + ":" + ss;
    }
}
