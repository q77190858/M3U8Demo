package com.juju.m3u8converter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    String externalStoragePath;

    Toolbar toolbar;
    Switch switchPanel;
    LinearLayout panel;
    ProgressBar progressBar;

    TextView textViewScanPath;
    RadioButton checkBoxUC;
    RadioButton checkBoxQQ;
    RadioButton checkBoxOther;
    RadioButton radioButtonUseFFmpeg;
    RadioButton radioButtonUseJava;
    CheckBox checkBoxDelOld;
    Button buttonScan;
    Button buttonConvert;
    ArrayList<M3u8File> waitingList;
    static boolean working;

    ListView listView;
    M3u8Adapter m3u8adapter;
    ArrayList<M3u8File> m3u8List;
    int count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //判断是否有读写外部存储的权限
        checkStoragePermissions();

        toolbar=findViewById(R.id.toolbar);
        switchPanel=findViewById(R.id.switchPanel);
        panel=findViewById(R.id.panel);
        progressBar=findViewById(R.id.progressBar);
        switchPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    panel.setVisibility(View.VISIBLE);
                    textViewScanPath.setVisibility(View.VISIBLE);
                }
                else {panel.setVisibility(View.GONE);textViewScanPath.setVisibility(View.GONE);}
            }
        });

        waitingList=new ArrayList<>();
        textViewScanPath=findViewById(R.id.textViewScanPath);
        checkBoxUC=findViewById(R.id.checkBoxScanUC);
        checkBoxQQ=findViewById(R.id.checkBoxScanQQ);
        checkBoxOther=findViewById(R.id.checkBoxScanOther);
        checkBoxDelOld=findViewById(R.id.checkBoxDeleteOld);
        radioButtonUseFFmpeg=findViewById(R.id.radioButtonUseFFmpeg);
        radioButtonUseJava=findViewById(R.id.radioButtonUseJava);
        buttonScan=findViewById(R.id.buttonStartScan);
        buttonConvert=findViewById(R.id.buttonStartConvert);

        listView=findViewById(R.id.listView);
        m3u8List=new ArrayList<>();
        m3u8adapter=new M3u8Adapter(MainActivity.this,R.layout.m3u8_item,m3u8List);
        listView.setAdapter(m3u8adapter);//绑定适配器和listview

        checkBoxUC.setOnClickListener(this);
        checkBoxQQ.setOnClickListener(this);
        checkBoxOther.setOnClickListener(this);
        buttonScan.setOnClickListener(this);
        buttonConvert.setOnClickListener(this);
        checkBoxQQ.performClick();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode!= Activity.RESULT_OK)
        {
            checkBoxQQ.performClick();
            return;
        }
        switch(requestCode) {
            case 999:
                //Toast toast=Toast.makeText(MainActivity.this,"返回结果："+data.getData().getPath(),Toast.LENGTH_SHORT);
                //toast.show();
                //Log.d("Uri", "onActivityResult: "+data.getData().getPath());
                String scanPath="/"+data.getData().getPath().substring(data.getData().getPath().indexOf(":")+1);
                textViewScanPath.setText("扫描文件夹:"+externalStoragePath+scanPath);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId())
        {
            case R.id.checkBoxScanUC:
                textViewScanPath.setText("扫描文件夹:"+externalStoragePath+"/UCDownloads/VideoData");
                break;
            case R.id.checkBoxScanQQ:
                textViewScanPath.setText("扫描文件夹:"+externalStoragePath+"/QQBrowser/视频");
                break;
            case R.id.checkBoxScanOther:
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                //i.setDataAndType(Uri.fromFile(new File(externalStoragePath)),DocumentsContract.EXTRA_INITIAL_URI);
                Log.d("externalStoragePath", "onClick: "+externalStoragePath);
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(new File(externalStoragePath)));
                startActivityForResult(Intent.createChooser(i, "请选择一个文件夹"),999);
                break;
            case R.id.buttonStartScan:
                if(buttonScan.getText().equals("开始扫描"))
                {
                    if(!checkBoxUC.isChecked()&&!checkBoxQQ.isChecked()&&!checkBoxOther.isChecked())
                    {
                        Toast toast=Toast.makeText(MainActivity.this,"请至少选择一个搜索选项",Toast.LENGTH_SHORT);
                        toast.show();
                        return;
                    }
                    if(buttonConvert.getText().equals("停止转换"))
                    {
                        Toast toast=Toast.makeText(MainActivity.this,"转换期间无法扫描",Toast.LENGTH_SHORT);
                        toast.show();
                        return;
                    }
                    checkBoxUC.setEnabled(false);
                    checkBoxQQ.setEnabled(false);
                    checkBoxOther.setEnabled(false);
                    buttonScan.setText("停止扫描");
                    progressBar.setIndeterminate(true);
                    progressBar.setVisibility(View.VISIBLE);
                    String s=textViewScanPath.getText().toString();
                    startScan(s.substring(s.indexOf(":")+1),checkBoxOther.isChecked());
                }
                else
                {
                    checkBoxUC.setEnabled(true);
                    checkBoxQQ.setEnabled(true);
                    checkBoxOther.setEnabled(true);
                    buttonScan.setText("开始扫描");
                    progressBar.setVisibility(View.INVISIBLE);
                    stopScan();
                }
                break;
            case R.id.buttonStartConvert:
                if(buttonConvert.getText().equals("开始转换"))
                {
                    if(buttonScan.getText().equals("停止扫描"))
                    {
                        Toast toast=Toast.makeText(MainActivity.this,"扫描期间无法转换",Toast.LENGTH_SHORT);
                        toast.show();
                        return;
                    }
                    checkBoxDelOld.setEnabled(false);
                    buttonConvert.setText("停止转换");
                    //progressBar.setIndeterminate(false);
                    progressBar.setVisibility(View.VISIBLE);
                    startConvert();
                }
                else
                {
                    if(m3u8List.size()==0)
                    {
                        Toast toast=Toast.makeText(MainActivity.this,"请先扫描文件",Toast.LENGTH_SHORT);
                        toast.show();
                        return;
                    }
                    checkBoxDelOld.setEnabled(true);
                    buttonConvert.setText("开始转换");
                    progressBar.setVisibility(View.INVISIBLE);
                    stopConvert();
                }
                break;
            default:
                    break;
        }
    }

    //开始扫描
    private void startScan(final String path,final boolean Re)
    {
        m3u8List.clear();
        working=true;
        new Thread()
        {
            @Override
            public void run() {
                super.run();
                getAllFiles(path,".m3u8",Re);
                if(m3u8List.size()==0)
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast toast=Toast.makeText(MainActivity.this,"文件不存在或无权限访问",Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    });
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //扫描结束
                        checkBoxUC.setEnabled(true);
                        checkBoxQQ.setEnabled(true);
                        checkBoxOther.setEnabled(true);
                        buttonScan.setText("开始扫描");
                        progressBar.setVisibility(View.INVISIBLE);
                        listView.setAdapter(m3u8adapter);
                        //Log.d("",FFmpegCmd.retrieveInfo(m3u8List.get(0).videoList.get(0)));
                        //Log.d("m3u8List.size()", Integer.toString(m3u8List.size()));
                    }
                });
                working=false;
            }
        }.start();
    }
    private void stopScan()
    {
        working=false;
        //扫描结束
        checkBoxUC.setEnabled(true);
        checkBoxQQ.setEnabled(true);
        checkBoxOther.setEnabled(true);
        buttonScan.setText("开始扫描");
        progressBar.setVisibility(View.INVISIBLE);
        listView.setAdapter(m3u8adapter);
    }
    private void startConvert()
    {
        int maxCount=1;
        Log.d("startConvert", "maxCount: "+String.valueOf(maxCount));
        waitingList.clear();
        count=0;
        for(int i=0;i<m3u8List.size();i++)
        {
            //Log.d("m3u8List["+String.valueOf(i)+"]", String.valueOf(m3u8List.get(i).getIsChecked()));
            if(m3u8List.get(i).isChecked==true&&m3u8List.get(i).status.equals("就绪"))
            {
                if(count<maxCount)
                {
                    m3u8List.get(i).startConvert(MainActivity.this);
                    count++;
                    Log.d("count", "startConvert: count:"+String.valueOf(count));
                }else{
                    waitingList.add(m3u8List.get(i));
                }
            }
        }
        if(waitingList.size()==0&&count==0)
        {
            checkBoxDelOld.setEnabled(true);
            buttonConvert.setText("开始转换");
            progressBar.setVisibility(View.INVISIBLE);
            Toast toast=Toast.makeText(MainActivity.this,"请先选择要转换的文件",Toast.LENGTH_SHORT);
            toast.show();
        }
    }
    public void onConvertFineshed()
    {
        int maxCount=1;
        if(!waitingList.isEmpty())
        {
            waitingList.remove(0).startConvert(MainActivity.this);
            Log.d("count", "onConvertFineshed: count:"+String.valueOf(count));
        }
        else
        {
            count--;
            Log.d("count", "onConvertFineshed: count:"+String.valueOf(count));
            if(count==0)
            {
                checkBoxDelOld.setEnabled(true);
                buttonConvert.setText("开始转换");
                progressBar.setVisibility(View.INVISIBLE);
            }
        }
    }
    private void stopConvert()
    {
        waitingList.clear();
    }

    /**
     * 获取指定目录内所有文件路径
     * @param dirPath 需要查询的文件目录
     * @param _type 查询类型，比如mp3什么的
     */
    public void getAllFiles(String dirPath, String _type, boolean Re)
    {
        //如果线程标志为假，则线程退出
        if(!working)return;
        //Log.d("LOGCAT","dirPath:"+dirPath);
        File f = new File(dirPath);
        if (!f.exists()) {//判断路径是否存在
            return;
        }

        File[] files = f.listFiles();

        if(files==null){//判断权限
            return;
        }

        for (File _file : files) {//遍历目录
            if(_file.isFile() && _file.getName().endsWith(_type)){
                String filePath = _file.getAbsolutePath();//获取文件路径
                String fileName = _file.getName();//获取文件名
                fileName=fileName.substring(0,fileName.length()-_type.length());//去除扩展名
//                Log.d("LOGCAT","fileName:"+fileName);
//                Log.d("LOGCAT","filePath:"+filePath);
                try {
                    M3u8File _fInfo = new M3u8File(filePath);
                    _fInfo.isChecked=true;
                    m3u8List.add(_fInfo);
                }catch (Exception e){
                }
            } else if(_file.isDirectory()&&Re){//查询子目录
                getAllFiles(_file.getAbsolutePath(), _type,Re);
            } else{
            }
        }
        return;
    }

    private void checkStoragePermissions()
    {
        //动态申请存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            int storageWritePermission = this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int storageReadPermission = this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            //检测是否有权限，如果没有权限，就需要申请
            if (storageWritePermission != PackageManager.PERMISSION_GRANTED ||
                    storageReadPermission != PackageManager.PERMISSION_GRANTED) {
                //申请权限
                this.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        }
        //File f=Environment.getExternalStorageDirectory();
        File f=new File("/sdcard/");
        if(!f.exists()||!f.canRead()) {
            //Toast toast = Toast.makeText(MainActivity.this, "内部存储路径不存在或无法访问f.canRead()"+String.valueOf(f.canRead()), Toast.LENGTH_SHORT);
            //toast.show();
            externalStoragePath="/sdcard";
        }
        else {
            //Toast toast = Toast.makeText(MainActivity.this, "内部存储路径存在且可读"+f.getAbsolutePath(), Toast.LENGTH_SHORT);
            //toast.show();
            externalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent=new Intent();
            intent.setClass(this,About.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
