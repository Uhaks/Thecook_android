package inha_ee_hanium.share_course;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    /*
     * variable for file system
     */

    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 100;

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
    private class DataHandler extends Handler {
        public void handleMessage(Message msg){
            switch(msg.what){
                case MSG_RECIVE_MESSAGE:
                    String string_buffer;
                    byte[] readBuffer = (byte[]) msg.obj;
                    int readcount = msg.arg1;
                    int readposition = 0;

                    //string_buffer = ASCII_Convert(readBuffer, readcount);
                    string_buffer = new String(readBuffer,0,readcount);

                    if(string_buffer.contains("LR")){
                        if(isExternalStorageReadable()) {
                            final List<String> RecipeList = new ArrayList<String>();
                            //final String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.pathSeparator+ "Recipe"+File.pathSeparator;
                            FilenameFilter fileFilter = new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.endsWith("txt");
                                }
                            };

                            if (ContextCompat.checkSelfPermission(MainActivity.this,
                                    Manifest.permission.READ_CONTACTS)
                                    != PackageManager.PERMISSION_GRANTED) {

                                // Should we show an explanation?
                                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                                        Manifest.permission.READ_CONTACTS)) {

                                    // Show an expanation to the user *asynchronously* -- don't block
                                    // this thread waiting for the user's response! After the user
                                    // sees the explanation, try again to request the permission.

                                } else {

                                    // No explanation needed, we can request the permission.

                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.READ_CONTACTS},
                                            MY_PERMISSIONS_REQUEST_READ_CONTACTS);

                                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                                    // app-defined int constant. The callback method gets the
                                    // result of the request.
                                }
                            }

                            File file = new File("/sdcard/Recipe");
                            File[] list = file.listFiles(fileFilter);
                            for (int i = 0; i < list.length; i++) {
                                RecipeList.add(list[i].getName());
                            }

                            ArrayAdapter<File> arrayAdapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_list_item_1, RecipeList);
                            ListView listView = (ListView) findViewById(R.id.sharerecipe_data_return);
                            listView.setAdapter(arrayAdapter);

                            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                                    String selected_path = "/sdcard/Recipe/" + RecipeList.get(position);
                                    try {
                                        FileInputStream fileInputStream = new FileInputStream(selected_path);
                                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
                                        String strbuffer;
                                        String recipe_str = "";
                                        while ((strbuffer = bufferedReader.readLine()) != null)
                                            recipe_str += strbuffer;
                                        sendData(recipe_str);
                                        onDestroy();
                                        finish();
                                    } catch (IOException e) {
                                    }

                                }
                            });
                        }
                    }

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

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}

