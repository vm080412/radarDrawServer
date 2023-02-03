package com.example.servicetest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    MyService myService = null;
    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myService = ((MyService.LocalBinder)service).getService();
            Log.d("testS","con ok");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("testS","con bye");
        }
    };

    Button btS,btF,btE;
    EditText ed1,ed2;
    TextView tv;
    boolean serviceGo = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this,MyService.class);
        btS = findViewById(R.id.button_start);
        btF = findViewById(R.id.button_function);
        btE = findViewById(R.id.button_stop);
        btE.setEnabled(false);
        btF.setEnabled(false);
        tv = findViewById(R.id.textView);

        ed1 = findViewById(R.id.editTextNumber);
        ed2 = findViewById(R.id.editTextNumber2);

        startService(intent);
        bindService(intent,serviceConnection,BIND_AUTO_CREATE);
        tv.setText("service start");

        /*while (serviceGo){
            tv.setText("000");
            if (myService != null){
                Log.d("testS","fun go");
                myService.connetRadar();
                tv.setText("service fun go");
            }else {
                try {
                    Thread.sleep(1000);
                    tv.setText("service wait");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }*/

        /*btS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(intent);
                bindService(intent,serviceConnection,BIND_AUTO_CREATE);
                btS.setEnabled(false);
                btF.setEnabled(true);
                btE.setEnabled(true);
            }
        });
        btE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myService != null){
                    unbindService(serviceConnection);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    stopService(intent);
                    btE.setEnabled(false);
                    btF.setEnabled(false);
                    btS.setEnabled(true);
                }
            }
        });
        btF.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myService != null){
                    Log.d("testS","fun go");
                    myService.connetRadar();
                }
            }
        });*/
    }
}