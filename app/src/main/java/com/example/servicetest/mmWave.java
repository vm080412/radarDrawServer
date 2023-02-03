package com.example.servicetest;

import android.content.Context;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


public class mmWave {
    //
    private ArrayList<String> cfgArrList;
    private  int[][] ODHeatMap;
    private  int[][] ODHeatMapRecord;
    boolean recordGo = false;
    boolean packEndLoop = false;
    //
    mmWave(){
        cfgArrList = new ArrayList<String>();
        ODHeatMap=new int[64][48];
        ODHeatMapRecord = new int[64][48];
    }
    // --
    // -- This function parses the mmWave config file and store the effective configuration
    // -- into the cfgArrList.
    // --
    public boolean loadCfg(InputStream inCfg){
        //
        boolean status=false;
        //
        InputStreamReader isrCfg = new InputStreamReader(inCfg);
        BufferedReader br = new BufferedReader(isrCfg);
        String strTmp = "";
        try{
            int cnt=0;
            do {
                strTmp="";
                strTmp = br.readLine();

                if( strTmp.length() != 0 ) {
                    if( strTmp.charAt(0) != '%'  ){
                        cfgArrList.add(strTmp);
                        //Log.d("KanCfg", cfgArrList.get(cnt)+"-"+String.valueOf(cnt));
                        cnt++;
                    }
                }
            } while ( !strTmp.equals("sensorStart" )  );
            //
            br.close();
            isrCfg.close();
            status=true;

        }
        catch(Exception e){
            Log.d("Kan_mmWave_LodCfg", e.getMessage());
            status=false;
        }
        //Log.d("Kan:", "Here");
        return status;
    }
    // --
    // -- This function write the effective configuration into the TI mmWave radar through serial port
    // --
    public void writeCfgToRadar(UsbSerialPort port){
        byte buffer[] = new byte[1024];
        String text="", tmpStr="";
        String[] strArr=null;
        int number = 1025, nPrompt=0;
        boolean prompt=false, wait4Done=true;

        //
        try {
            //--Need to send a "\n" first to wake up the serial port
            port.write(("\n").getBytes(), 1000);
            TimeUnit.MILLISECONDS.sleep(200);
            do {
                Arrays.fill(buffer, (byte) 0); // Clear the buffer
                number = port.read(buffer, 1000);
                Log.d("Kan2", "Inside writeCfg"+String.valueOf(number));
                text = new String(buffer, "UTF-8");
                tmpStr = tmpStr + text.substring(0, number) ;
                //--
                //if (tmpStr.contains("\n\r")) {    ****
                    strArr = tmpStr.split("(\\n\\r)");
                    for (String tkn : strArr) { // Each line
                        if (tkn.contains("VODDemo:/>")) {
                            prompt=true;
                        }
                        if (tkn.contains("Done")) {
                            wait4Done=false;
                            //Log.d("Kan2", "Inside");
                        }
                        //Log.d("Kan1", tkn);
                    }
                    if(prompt) {
                        port.write((cfgArrList.get(nPrompt) + "\n").getBytes(), 1000);
                        Log.d("Kan3", cfgArrList.get(nPrompt) + "--" + nPrompt);
                        TimeUnit.MILLISECONDS.sleep(200);
                        prompt=false;
                        wait4Done=true;
                        tmpStr="";
                        nPrompt++;
                    }
                    if(!wait4Done) {
                        wait4Done=true;
                    }
                //}   ****
                //--
                if (nPrompt == cfgArrList.size()) {
                    port.close();
                    Log.d("Kan4",String.valueOf(nPrompt));
                    break;
                }
            }while (number != 0);
            //--- CLose the port
            port.close();
        } catch (Exception e) {
            Log.d("Kan_mmWave_writeCfg2Radar", e.getMessage());
        }

    }
    // --
    // -- This function check the beginning of a frame by counting the sync characters sequentially
    // --   1. Return -1, if the sync characters are not found or not in sequence
    // --   2. Return the cntIdx++, the begin position of the TotalPacketLen field in a frame,
    // --      if sync chars are matched in the buffer
    // --
    private short chkSyncChars(byte[] buffer, short cntIdx){
        //
        byte syncFlag=(byte) 0x00; //The flag to check if all the sync characters are met in sequence
        //short cntIdx;
        //
        //Log.d("Kan:inside chkSyncChars", cntIdx + "--" + buffer.length);
        do {
            //
            switch (buffer[cntIdx]) {
                case (byte) 0x02:
                    if (syncFlag == (byte) 0x00) {
                        syncFlag = (byte) (syncFlag | (byte) 0x80);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x01:
                    if (syncFlag == (byte) 0x80) {
                        syncFlag = (byte) (syncFlag | (byte) 0x40);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x04:
                    if (syncFlag == (byte) 0xc0) {
                        syncFlag = (byte) (syncFlag | (byte) 0x20);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x03:
                    if (syncFlag == (byte) 0xe0) {
                        syncFlag = (byte) (syncFlag | (byte) 0x10);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x06:
                    if (syncFlag == (byte) 0xf0) {
                        syncFlag = (byte) (syncFlag | (byte) 0x08);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x05:
                    if (syncFlag == (byte) 0xf8) {
                        syncFlag = (byte) (syncFlag | (byte) 0x04);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x08:
                    if (syncFlag == (byte) 0xfc) {
                        syncFlag = (byte) (syncFlag | (byte) 0x02);
                        //Log.d("Kan:byte", cntIdx + "--" + buff[cntIdx] + "--(" + syncFlag+")");
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                case (byte) 0x07:
                    if (syncFlag == (byte) 0xfe) {
                        syncFlag = (byte) (syncFlag | (byte) 0x01);
                        //Log.d("Kan:byte", cntIdx + "--" + buffer[cntIdx] + "--(" + syncFlag + ")" );
                    } else
                        syncFlag = (byte) 0x00;
                    break;
                default:
                    syncFlag = (byte) 0x00;//*******
                    // Do nothing
            }
            //
            cntIdx++; // The begin position of the TotalPacketLen field in a frame
            // If the sync chars are matched in the middle of buffer
            if(syncFlag == (byte) 0xff){
               break;
            }
        } while (cntIdx < buffer.length);
        // If the sync chars are not matched for all elements in the buffer
        if(syncFlag != (byte) 0xff){
            cntIdx=-1;
        }
        return cntIdx;
    }
    //
    //--- Occupancy  Detection Packet Parsing, parseODPacket()
    //---  This function is in a loop and will block the calling process.
    //---  Therefore, employ the thread in calling process while call this function
    //---
    public void parseODPacket(UsbSerialPort port, Context ct ){
        //--- Psudeo code
        byte buff[]=new byte[1024];
        int numRead=0;
        short cntIdx=0, sIdx=0;
        boolean EOB=true;
        //boolean RHL=false;
        int idxBuf=0;

       /* for (int ii = 0; ii < 64; ii++)
            for (int jj = 0; jj < 48; jj++)
                ODHeatMapRecord[ii][jj] = 1;*/

        //-------------------------------->>>> Need loop
        while(!packEndLoop) {
            recordGo = false;
            if (EOB) {
                Arrays.fill(buff, (byte) 0); // Clear the buffer
                try {
                    numRead = port.read(buff, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                sIdx=0;
            } else {
                sIdx=(short) idxBuf;
                EOB=true;
            }
            //
            //if(numRead==0) continue;
            //
            cntIdx = chkSyncChars(buff, sIdx);
            while (cntIdx == -1) {
                Arrays.fill(buff, (byte) 0); // Clear the buffer
                try {
                    numRead = port.read(buff, 0);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                //
                //Log.d("Kan:V8", "Read: " + numRead);
                //Log.d("KanC:SyncCharNotFound",cntIdx+"");
                cntIdx = chkSyncChars(buff,(short) 0);

            }
            //--
            // B. Get frame length and number
            //Log.d("Kan:SyncCharFound", "The frame starts from " + cntIdx); //The current cntIdx is the first byte of Total Packet length
            //--- Frame header other than Sync processing
            int lenFrame = (buff[cntIdx + 3] << 24) | (buff[cntIdx + 2] << 16) | (buff[cntIdx + 1] << 8) | (buff[cntIdx]);
            long nFrame = ((buff[cntIdx + 11] << 24) | (buff[cntIdx + 10] << 16) | (buff[cntIdx + 9] << 8) | (buff[cntIdx + 8])) & 0x0ffffffffL;
            //Log.d("Kan:Frame", "Len: " + lenFrame + "," + "num Frame: " + nFrame);
            // C. Get heatmap length
            int lenV = (buff[cntIdx + 31] << 31) | (buff[cntIdx + 30] << 16) | (buff[cntIdx + 29] << 8) | (buff[cntIdx + 28]);
            //Log.d("Kan:TLVHeader", "V Len: " + lenV);
            // D. 1st Parse two-bytes heatmap in buffer and fill 2d heatmap array
            int bytesLeft = lenV - (numRead - (cntIdx + 32));
            // Reset the heatmap to zeros
            for (int ii = 0; ii < 64; ii++)
                for (int jj = 0; jj < 48; jj++)
                    ODHeatMap[ii][jj] = 0;
            //
            int hmIdx = 0;
            for (int idx = cntIdx + 32; idx < numRead; idx += 2) { // The begin position of the values of the first TLV is (cntIdx+32)
                //Log.d("Kan:HM ",""+ (((buff[idx+1]<<8)|(buff[idx])) & 0x0ffff) );
                ODHeatMap[(int) (hmIdx / 48)][hmIdx % 48] = (((buff[idx + 1] << 8) | (buff[idx])) & 0x0ffff);
                hmIdx++;
            }
            //Log.d("Kan:V8", "Number of Read: " + numRead+", HM index: " + hmIdx);
            // Log.d("Kan:V8", "Bytes left: " + bytesLeft);
            // E. Read data from dtaport to buffer
            // F. Parse two-bytes heatmap in buffer and Fill 2d heatmap array
            //int idxBuf=0;
            do {
                //
                Arrays.fill(buff, (byte) 0); // Clear the buffer
                try {
                    numRead = port.read(buff, 0);
                    //Log.d("Kan:V8", "Number of Read: " + numRead);
                    //
                    bytesLeft = bytesLeft - numRead;
                    //Log.d("Kan:V8", "Read: " + numRead);
                    //
                    for (idxBuf = 0; idxBuf < numRead; idxBuf += 2) {
                        //Log.d("Kan:HM ",""+ (((buff[idx+1]<<8)|(buff[idx])) & 0x0ffff) );
                        if (hmIdx < 3072) {
                            int a =(((buff[idxBuf + 1] << 8) | (buff[idxBuf])) & 0x0ffff);
                            ODHeatMap[(int) (hmIdx / 48)][hmIdx % 48] = (((buff[idxBuf + 1] << 8) | (buff[idxBuf])) & 0x0ffff);

                            hmIdx++;
                        } else {
                            //Log.d("Kan:V8", "HM index: " + hmIdx + "--" + "Buffer index: " + idxBuf);
                            EOB = false;
                            break;
                        }

                    }
                    //
                    //Log.d("Kan:V8", "Number of Read: " + numRead+", HM index: " + hmIdx + "--" + "Buffer index: " + idxBuf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (hmIdx < 3072);
            //--- At this stage, the heatmap length is reached.
            //--- Ff the end of buffer is not reached, set EOB=false
            //if (idxBuf < buff.length) EOB = false;
            //--- One 2D HM array is saved using the private variable ODHeatMap
            //--- Need to delay a little bit, the radar output rate is 6Hz=167 ms

            //updata recorder after while
            //ODHeatMapRecord = (int[][]) ODHeatMap.clone();
            ODHeatMapRecord = Arrays.copyOf(ODHeatMap, 3072) ;
            recordGo = true;
        }
        //-------------------------------->>>> End of loop
    }
    //
    public int[][] getODHeatmap(){
        return ODHeatMap;
    }

    public int[][] getODHeatMapRecord() {
        return ODHeatMapRecord;
    }

    public void setPackEndLoop(boolean packEndLoop) {
        this.packEndLoop = packEndLoop;
    }
}
