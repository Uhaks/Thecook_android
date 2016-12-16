package inha_ee_hanium.viewstate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {


    /*
     *  variable for bluetooth connection
     */
    static final int REQUEST_ENABLE_BT = 10;
    int mPairedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;

    BluetoothAdapter mBluetoothAdapter = null;
    BluetoothDevice mRemoteDevie;

    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;

    Thread mWorkerThread = null;
    byte[] readBuffer;
    int readBufferPosition;

    /*
     *  variable for BT Handler
     */

    private static final int MSG_RECIVE_MESSAGE = 1;
    private DataHandler mDataHandler = new DataHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkBluetooth();
    }

    BluetoothDevice getDeviceFromBondedList(String name) {
        BluetoothDevice selectedDevice = null;
        for(BluetoothDevice device : mDevices) {
              if(name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    void sendData(String msg) {
        try{
            mOutputStream.write(msg.getBytes());
        }catch(Exception e) {
            Toast.makeText(getApplicationContext(), "Error Occured during sending data", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    void connectToSelectedDevice(String selectedDeviceName) {
        mRemoteDevie = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        try {
            mSocket = mRemoteDevie.createRfcommSocketToServiceRecord(uuid);
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();
            Toast.makeText(getApplicationContext(), "Open socket", Toast.LENGTH_LONG).show();

            beginListenForData();

        }catch(Exception e) {
            Toast.makeText(getApplicationContext(), "Error Occured during connecting Bluetooth", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    void beginListenForData() {

        readBufferPosition = 0;                 // 버퍼 내 수신 문자 저장 위치.
        readBuffer = new byte[1024];            // 수신 버퍼.

        // 문자열 수신 쓰레드.
        mWorkerThread = new Thread(new Runnable()
        {
            @Override
            public void run() {
                while(true) {
                    try {
                        int byteAvailable = mInputStream.available();
                        if(byteAvailable > 0) {
                            SystemClock.sleep(200);
                            byteAvailable = mInputStream.available();
                            byte[] packetBytes = new byte[byteAvailable];
                            mInputStream.read(packetBytes);
                            for(int i=0; i<byteAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b > 0 && b < 127)
                                    readBuffer[readBufferPosition++] = b;
                            }

                            byte[] input_buffer = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, input_buffer, 0, readBufferPosition);

                            mDataHandler.obtainMessage(MSG_RECIVE_MESSAGE, readBufferPosition, -1, input_buffer).sendToTarget();
                            readBufferPosition = 0;

                        }

                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Error Occured during reciving data", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }
        });
        mWorkerThread.start();
    }

    void selectDevice() {
        mDevices = mBluetoothAdapter.getBondedDevices();
        mPairedDeviceCount = mDevices.size();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Device");

        List<String> listItems = new ArrayList<String>();
        for(BluetoothDevice device : mDevices) {
            listItems.add(device.getName());
        }
        listItems.add("Cancel");

        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);
        listItems.toArray(new CharSequence[listItems.size()]);

        builder.setItems(items, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int item) {
                // TODO Auto-generated method stub
                if(item == mPairedDeviceCount) {
                    Toast.makeText(getApplicationContext(), "You don't select device", Toast.LENGTH_LONG).show();
                    finish();
                }
                else {
                    connectToSelectedDevice(items[item].toString());
                }
            }

        });

        builder.setCancelable(false);
        AlertDialog alert = builder.create();
        alert.show();
    }


    void checkBluetooth() {

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null ) {
            Toast.makeText(getApplicationContext(), "This device don't support", Toast.LENGTH_LONG).show();
            finish();
        }
        else {
            if(!mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "Now Bluetooth is disable", Toast.LENGTH_LONG).show();
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            else
                selectDevice();
        }
    }

    @Override
    protected void onDestroy() {
        try{
            mWorkerThread.stop();
            mInputStream.close();
            mSocket.close();
        }catch(Exception e){}
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_OK) {
                    selectDevice();
                }
                else if(resultCode == RESULT_CANCELED) {
                    Toast.makeText(getApplicationContext(), "The device isn't support bluetooth, Kill the device",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /*
     *  Handler for Change View
     */
    private class DataHandler extends Handler{
        public void handleMessage(Message msg){
            switch(msg.what){
                case MSG_RECIVE_MESSAGE:
                    String string_buffer;
                    String temp_buffer = "Temp : ";
                    String time_buffer = "Time : ";
                    String power_buffer = "Power : Level ";
                    byte[] readBuffer = (byte[]) msg.obj;
                    int readcount = msg.arg1;
                    int readposition = 0;

                    //string_buffer = ASCII_Convert(readBuffer, readcount);
                    string_buffer = new String(readBuffer,0,readcount);

                    readposition = string_buffer.indexOf("@TP");
                    readposition += 3;
                    temp_buffer += string_buffer.substring(readposition, readposition+3);

                    readposition = string_buffer.indexOf("@TI");
                    readposition += 3;
                    time_buffer += string_buffer.substring(readposition, readposition+4);

                    readposition = string_buffer.indexOf("@P");
                    readposition += 2;
                    power_buffer += string_buffer.substring(readposition, readposition+1);

                    TextView timeView = (TextView)findViewById(R.id.viewstate_timedata);
                    timeView.setTextColor(Color.BLACK);
                    timeView.setText(time_buffer);
                    TextView tempView = (TextView)findViewById(R.id.viewstate_tempdata);
                    tempView.setTextColor(Color.BLACK);
                    tempView.setText(temp_buffer);
                    TextView powerView = (TextView)findViewById(R.id.viewstate_powerdata);
                    powerView.setTextColor(Color.BLACK);
                    powerView.setText(power_buffer);

                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }

    }

    public String ASCII_Convert(byte[] byte_input, int array_length){
        int byte_input_position = 0;
        int ASCII_number_buff;
        String string_buffer = "";
        while(byte_input_position<array_length){
            ASCII_number_buff = (byte_input[byte_input_position++]-48)*10;
            ASCII_number_buff += (byte_input[byte_input_position++]-48);
            string_buffer += (char)ASCII_number_buff;
        }

        return string_buffer;
    }
}

