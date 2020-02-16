package com.juju.m3u8converter;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class About extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        TextView textViewAbout=findViewById(R.id.aboutBlog);
        TextView textViewUpdate=findViewById(R.id.aboutUpdate);
        textViewAbout.setOnClickListener(this);
        textViewUpdate.setOnClickListener(this);
        }

    @Override
    public void onClick(View v)
    {
        if(v.getId()==R.id.aboutBlog) {
            Uri uri = Uri.parse("https://blog.mxslly.com");    //设置跳转的网站
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
        else if(v.getId()==R.id.aboutUpdate) {
            Uri uri = Uri.parse("https://blog.mxslly.com");    //设置跳转的网站
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
    }
}
