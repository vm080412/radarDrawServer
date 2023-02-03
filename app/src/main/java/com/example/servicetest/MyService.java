package com.example.servicetest;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MyService extends Service {

    private UsbManager manager;
    private UsbSerialPort uartPort, dataPort;
    private UsbSerialDriver driver;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private enum UsbPermission { Unknown, Requested, Granted, Denied };
    private UsbPermission usbPermission = UsbPermission.Unknown;
    //
    private static final String ACTION_USB_PERMISSION = BuildConfig.APPLICATION_ID+".USB_PERMISSION";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    PendingIntent permissionIntent;

    int [][] heatMapCatch = new int[64][48];
    int secPerData,dataPerPic, avg_counter = 0, save_counter = 0;
    int waitTime = 30000; //wait 30s to next data
    int recordTimes = 6; // how many data save
    Intent it ;
    Thread t1,t2,t3;
    boolean drawDone = true,
            stopR = false,
            timerSwitch = true;

    Handler handler = new Handler(new HDCB());
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hhmmssSS");
    mmWave m1843=new mmWave();

    public class LocalBinder extends Binder{
        MyService getService(){
            return MyService.this;
        }
    }
    private LocalBinder mLocBin = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        it = intent;
        connetRadar();
        return mLocBin;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        m1843.setPackEndLoop(true);
        stopR = true;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        m1843.setPackEndLoop(true);
        stopR = true;
        super.onDestroy();
    }

    public void serTest(){
        for (int i = 0;i < 10;i++){
            Log.d("testS","  === test"+i);
        }
    }

    public void connetRadar(){
        secPerData = (int) it.getDoubleExtra("ms",200);
        dataPerPic = (int) it.getDoubleExtra("data",10);

        //--- 1. Register the USB permission receiver with USB PERMISSION
        manager = (UsbManager) getSystemService(USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        //--- 2. Find the devices available and request permission
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            manager.requestPermission(device, permissionIntent);
        }
        //--- 3. Find the corresponding device driver
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
        driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());
        //--- 4. Deal with the USB Permission
        if(connection == null && usbPermission == UsbPermission.Unknown && !manager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            manager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(connection == null) {
            if (!manager.hasPermission(driver.getDevice()))
                Log.d("Kan:","connection failed: permission denied");
            else
                Log.d("Kan:","connection failed: open failed");
            return;
        }

        dataPort = driver.getPorts().get(1); //--- Data Port
        while (!dataPort.isOpen()){
            try {
                dataPort.open(connection);
                dataPort.setParameters(921600,8, UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.d("Kan:Data port open error: ",e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }

        Runnable runnableDecoder = new Runnable() {
            @Override
            public void run() {
                m1843.parseODPacket(dataPort, getApplicationContext());
            }
        };

        Runnable runnableRecordData = new Runnable() {
            @Override
            public void run() {
                int i = 0;
                while (timerSwitch){
                    while (!stopR){
                        Log.d("kanRa",m1843.recordGo+"");
                        if (m1843.recordGo/* && drawDone*/){ // i = record data number
                            //drawDone = false;
                            handler.obtainMessage(3,m1843.getODHeatMapRecord())
                                    .sendToTarget();

                        }
                    }
                }
            }
        };

        Runnable runnableTimer = new Runnable() { //----wait time
            @Override
            public void run() {
                timerSwitch = true;
                while (timerSwitch){
                    try {
                        if (save_counter < recordTimes){
                            stopR = false;
                            Log.d("testSer","stopR change");
                        }else{
                            timerSwitch = false;
                            Log.d("testSer","data finish");
                        }
                        Thread.sleep(waitTime); //----to change wait time
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        t1 = new Thread(runnableDecoder);
        t2 = new Thread(runnableRecordData);
        t3 = new Thread(runnableTimer);

        t1.start();
        t2.start();
        t3.start();
    }

    class HDCB implements Handler.Callback{
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (avg_counter == 0){
                heatMapInit(heatMapCatch);
                heatMapPlus(heatMapCatch,(int[][]) msg.obj);
                avg_counter++;
            }else if(avg_counter == dataPerPic-1){
                heatMapPlus(heatMapCatch,(int[][]) msg.obj);
                heatMapDev(heatMapCatch);

                //here to save
                recordData(heatMapCatch);
                stopR = true;
                avg_counter = 0;
                save_counter++;
            }else {
                heatMapPlus(heatMapCatch,(int[][]) msg.obj);
                avg_counter++;
            }

            try {
                Thread.sleep(secPerData);
                drawDone = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return true;
        }
    }

    private void heatMapInit(int[][] array){
        for (int a = 0;a < 64;a++){
            for (int i = 0;i < 48;i++){
                array[a][i] = 0;
            }
        }
    }

    private void heatMapPlus(int[][] array,int[][] plusArray){
        for (int a = 0;a < 64;a++){
            for (int i = 0;i < 48;i++){
                array[a][i] += plusArray[a][i];
            }
        }
    }

    private void heatMapDev(int[][] array){
        for (int a = 0;a < 64;a++){
            for (int i = 0;i < 48;i++){
                array[a][i] = array[a][i]/dataPerPic;
            }
        }
    }

    private void recordData(int[][] array){  // to check data
        Date date = new Date();
        String strD = sdf.format(date);
        String dataPath = getApplicationContext().getFilesDir()+"/"+strD+"+"+ avg_counter +".csv";
        File newIFile=new File(dataPath);
        try {
            newIFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(newIFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);

            for (int a = 0;a < 64;a++){
                String str = "";
                for (int b =0;b < 48;b++){
                    if (b == 47){
                        str = str+array[a][b];
                    }else {
                        str = str+array[a][b]+",";
                    }
                }
                osw.write(str);
                osw.write("\r\n");
            }
            osw.flush();
            osw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

