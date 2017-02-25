package kr.ac.skku.nyx.selectiveconnection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;


import selective.connection.*;



public class MainActivity extends AppCompatActivity {
    private Communicator cm;
    private BTClientAdapter ca;
    private BTClientAdapter na3;
    private TCPClientAdapter na;
    private WFDClientAdapter na2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        cm = Communicator.get_instance();

        ca = new BTClientAdapter((short)1234, "B8:27:EB:37:0C:BD","150e8400-e29b-41d4-a716-446655440000");
        na = new TCPClientAdapter((short)2345, "192.168.0.48", 2345);
        na2 = new WFDClientAdapter((short)3456, 3456, this);
        na3 = new BTClientAdapter((short)3333, "B8:27:EB:37:0C:BD", "150e8400-1234-41d4-a716-446655440000");

        ca.set_control_adapter();
        na3.set_data_adapter();
        na.set_data_adapter();
        na2.set_data_adapter();

        new Thread() {
            private String tag = "Recved";
            public void run() {
                byte[] buf = new byte[8192];

                while (true) {
                    int res = cm.recv_data(buf, 8192);
                    Log.d(tag, Integer.toString(res) + "data received");
                }
            }
        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        na2.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        na2.onPause(this);
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.CHANGE_NETWORK_STATE}, 0);
        }
    }

}
