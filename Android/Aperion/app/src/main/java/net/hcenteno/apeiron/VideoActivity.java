/*
 * Apeiron by Hector Centeno
 * www.hcenteno.net
 *
 * This application is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This application is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this application; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 */

package net.hcenteno.apeiron;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class VideoActivity extends Activity {

    private final int FIRST_LIMIT = 120; // IR distance sensor threshold to start affecting lights
    private final int SECOND_LIMIT = 170; // IR distance sensor threshold to trigger vibration motors

    private final String TAG = VideoActivity.class.getSimpleName();

    private String remaining = "";

    private VideoView mVideoView;
    ImageView imageView;
    TextView textView;
    Bitmap[] bitmaps;

    private static UsbSerialPort sPort = null;

    private final ExecutorService mExecutorSerial = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {

                    VideoActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VideoActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make it full screen and without title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Screen locked on Landscape and do not sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Hide soft keys
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);


        setContentView(R.layout.video_view);

        textView = (TextView) findViewById(R.id.textview);
        textView.setVisibility(View.GONE); // set to View.Visible for calibrating the distance thresholds

        mVideoView = (VideoView) findViewById(R.id.video_view);
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
            }
        });

        // Load video
        mVideoView.setVideoPath(Environment.getExternalStorageDirectory().getPath() + "/Movies/Apeiron/video.mp4");
        mVideoView.start();

        // Load images
        bitmaps = new Bitmap[4];
        for (int i=0; i < bitmaps.length; i++) {
            bitmaps[i] = BitmapFactory.decodeFile(Environment.getExternalStorageDirectory().getPath() + "/Movies/Apeiron/img" + i + ".png");
        }

        imageView = (ImageView) findViewById(R.id.image1);
        imageView.setImageBitmap(bitmaps[0]);
        imageView.setAlpha(0f);

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {

            }
            sPort = null;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            Log.e(TAG, "No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                Log.e(TAG, "Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                Log.e(TAG, "Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            Log.i(TAG, "Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutorSerial.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void controlVideo(int distance) {


        textView.setText(String.valueOf(distance));

        if (distance > FIRST_LIMIT) {

            imageView.setAlpha(linLin(distance, FIRST_LIMIT, SECOND_LIMIT, 0f, 1f));
            imageView.setScaleX(linExp(distance, FIRST_LIMIT, SECOND_LIMIT, 1f, 2f));
            imageView.setScaleY(linExp(distance, FIRST_LIMIT, SECOND_LIMIT, 1f, 2f));

            int random1 = getRandom(1,10);
            int random2 = getRandom(1, 4) - 1;

            if (random1 > 6) {
                imageView.setImageBitmap(bitmaps[random2]);
            }

        } else {
            imageView.setAlpha(0f);
        }

    }

    // Generate random number from low to high, inclusive
    private int getRandom(int low, int high) {
        return (int)(Math.random() * high + low);
    }

    // Linear range mapping
    private float linLin(float x, float a, float b, float c, float d)
    {
        if (x <= a) return c;
        if (x >= b) return d;
        return (x-a)/(b-a) * (d-c) + c;
    }

    // Linear to exponential range mapping
    private float linExp(float x, float a, float b, float c, float d) {
        if (x <= a) return c;
        if (x >= b) return d;
        return (float) Math.pow(d / c, (x - a) / (b - a)) * c;
    }

    // Here is where the serial data from Arduino is parsed
    private void updateReceivedData(byte[] data) {

        int position;
        String message;
        String value;

        try {
            message = remaining + new String(data, "UTF-8");

            while ((position = message.indexOf("#")) != -1) {
                if (message.length() >= 4) {
                    if (message.length() > 4) {
                        value = message.substring(position + 1, 4);
                    } else {
                        value = message.substring(position + 1);
                    }
                    if (value.length() > 0) {
                        controlVideo(Integer.valueOf(value));
                    }
                    message = message.substring(position + 1);
                    remaining = "";
                } else {
                    remaining = message.substring(position);
                    message = "";
                }
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }


    }

    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, VideoActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

}
