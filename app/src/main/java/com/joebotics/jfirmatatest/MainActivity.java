package com.joebotics.jfirmatatest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.firmata4j.IODevice;
import org.firmata4j.IODeviceEventListener;
import org.firmata4j.IOEvent;
import org.firmata4j.Pin;
import org.firmata4j.PinEventListener;
import org.firmata4j.firmata.FirmataDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public final static String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = TAG + ".USB_PERMISSION";

    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private BroadcastReceiver usbReceiver;
    UsbSerialPort port;
    IODevice device;
    LcdPcf8574 display;

    private List<UsbSerialPort> getDeviceList() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        final List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            Log.d(TAG, String.format("+ %s: %s port%s",
                    driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            result.addAll(ports);
        }
        return result;
    }

    private IODevice connect() throws IOException, InterruptedException {
        if (usbReceiver == null) {
            usbReceiver = new UsbBroadcastReceiver();
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            this.registerReceiver(usbReceiver, filter);
        }

        if (usbManager.hasPermission(port.getDriver().getDevice())) {
            this.device = new FirmataDevice(usbManager, port);
            device.start();
            device.ensureInitializationIsDone();
            Pin pin = device.getPin(5);
            pin.addEventListener(new PinEventListener() {
                @Override
                public void onModeChange(IOEvent event) {
                    System.out.println("Mode of the pin has been changed");
                }

                @Override
                public void onValueChange(IOEvent event) {
                    System.out.println("Value of the pin " + event.getPin().getIndex() + " has been changed to " + event.getValue());
                }
            });
            pin.setMode(Pin.Mode.INPUT);
            //display = new LcdPcf8574(device, 0x27);
            //display.begin(16, 2);

            return device;
        } else {
            usbManager.requestPermission(port.getDriver().getDevice(), permissionIntent);
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        this.permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        final ToggleButton connectButton = (ToggleButton) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            List<UsbSerialPort> ports = getDeviceList();
            if (ports.size() > 0) {
                try {
                    if (device != null) {
                        device.stop();
                        device = null;
                    } else {
                        MainActivity.this.port = ports.get(0);
                        connect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            }
        });

        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (device != null) {
                    try {
                        Pin pin = device.getPin(2);
                        pin.setMode(Pin.Mode.OUTPUT);
                        pin.setValue(toggleButton.isChecked() ? 1 : 0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final Button sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TextView displayText = (TextView) findViewById(R.id.displayText);
                try {
                    display.clear();
                    display.print(displayText.getText().toString());
                    display.setBacklight(true);
                    display.display();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


        // subscribe to events using device.addEventListener(...);
        // and/or device.getPin(n).addEventListener(...);
        //device.start(); // initiate communication to the device
        //device.ensureInitializationIsDone(); // wait for initialization is done
        // sending commands to the board
        //device.stop(); // stop communication to the device
    }

    private class UsbBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            try {
                                connect();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    }
}
