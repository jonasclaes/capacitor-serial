package be.jonasclaes.capacitorserial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class Serial implements SerialInputOutputManager.Listener {

    // PluginCall that contains the data needed to open a serial connection.
    // We store this in the event that the app gets put in the background so
    // that we can reopen the connection afterwards with the same parameters.
    private PluginCall openSerialCall;

    // PluginCall that serves as a callback to our Capacitor app.
    // This call pushes data that is being read from the serial
    // connection to our Capacitor enabled app.
    private PluginCall readDataCall;

    // PluginCall that serves as a callback to our Capacitor app.
    // This call pushes data that is being received from the
    // event bus about the connection and removal of USB devices.
    private PluginCall usbAttachedDetachedCall;

    private AppCompatActivity appCompatActivity;

    private enum UsbPermission {
        Unknown,
        Requested,
        Granted,
        Denied
    }

    private static final String INTENT_ACTION_GRANT_USB = "be.jonasclaes.capacitorserial.GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private boolean sleepOnPause;

    // Serial I/O manager to handle new incoming serial data.
    private SerialInputOutputManager serialInputOutputManager;

    // Android USB Permission state.
    // Defaults to unknown.
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private UsbSerialPort usbSerialPort;

    // Serial port connection state.
    private boolean connected = false;

    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;

    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }

    public Serial() {
        broadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    if (INTENT_ACTION_GRANT_USB.equals(action)) {
                        usbPermission =
                            intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                                ? UsbPermission.Granted
                                : UsbPermission.Denied;

                        if (appCompatActivity != null && openSerialCall != null) {
                            openSerial(appCompatActivity, openSerialCall);
                            appCompatActivity.unregisterReceiver(this);
                        }
                    } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                        if (usbAttachedDetachedCall != null) {
                            JSObject jsObject = new JSObject();
                            usbAttachedDetachedCall.setKeepAlive(true);
                            jsObject.put("success", true);
                            jsObject.put("data", "USB_DEVICE_ATTACHED");
                            usbAttachedDetachedCall.resolve(jsObject);
                        }
                    } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                        if (usbAttachedDetachedCall != null) {
                            JSObject jsObject = new JSObject();
                            usbAttachedDetachedCall.setKeepAlive(true);
                            jsObject.put("success", true);
                            jsObject.put("data", "USB_DEVICE_DETACHED");
                            usbAttachedDetachedCall.resolve(jsObject);
                        }
                    }
                }
            };

        mainLooper = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(
            () -> {
                updateReceivedData(data);
            }
        );
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(
            () -> {
                updateReadDataError(e);
                disconnect();
            }
        );
    }

    private void disconnect() {
        connected = false;

        if (serialInputOutputManager != null) {
            serialInputOutputManager.setListener(null);
            serialInputOutputManager.stop();
        }

        serialInputOutputManager = null;

        usbPermission = UsbPermission.Unknown;

        try {
            if (usbSerialPort != null) {
                usbSerialPort.close();
            }
        } catch (IOException ignored) {}

        usbSerialPort = null;
    }

    public JSObject devices(AppCompatActivity activity) {
        JSObject object = new JSObject();
        try {
            List<Utils.DeviceItem> listItems = new ArrayList();
            UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
            UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
                if (driver != null) {
                    for (int port = 0; port < driver.getPorts().size(); port++) listItems.add(new Utils.DeviceItem(device, port, driver));
                } else {
                    listItems.add(new Utils.DeviceItem(device, 0, null));
                }
            }
            JSONArray jsonArray = Utils.deviceListToJsonConvert(listItems);
            JSONObject data = new JSONObject();
            data.put("devices", jsonArray);
            object.put("data", data);
            object.put("success", true);
        } catch (Exception exception) {
            object.put("error", new Error(exception.getMessage(), exception.getCause()));
        }
        return object;
    }

    public void openSerial(AppCompatActivity activity, PluginCall pluginCall) {
        JSObject object = new JSObject();
        this.appCompatActivity = activity;
        this.openSerialCall = pluginCall;

        try {
            int deviceId = openSerialCall.hasOption("deviceId") ? openSerialCall.getInt("deviceId") : 0;
            int portNum = openSerialCall.hasOption("portNum") ? openSerialCall.getInt("portNum") : 0;
            int baudRate = openSerialCall.hasOption("baudRate") ? openSerialCall.getInt("baudRate") : 9600;
            int dataBits = openSerialCall.hasOption("dataBits") ? openSerialCall.getInt("dataBits") : UsbSerialPort.DATABITS_8;
            int stopBits = openSerialCall.hasOption("stopBits") ? openSerialCall.getInt("stopBits") : UsbSerialPort.STOPBITS_1;
            int parity = openSerialCall.hasOption("parity") ? openSerialCall.getInt("parity") : UsbSerialPort.PARITY_NONE;
            boolean setDTR = openSerialCall.hasOption("dtr") && openSerialCall.getBoolean("dtr");
            boolean setRTS = openSerialCall.hasOption("rts") && openSerialCall.getBoolean("rts");

            this.sleepOnPause = openSerialCall.hasOption("sleepOnPause") ? openSerialCall.getBoolean("sleepOnPause") : true;

            UsbDevice device = null;
            UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
            for (UsbDevice v : usbManager.getDeviceList().values()) {
                if (v.getDeviceId() == deviceId) device = v;
            }
            if (device == null) {
                object.put("success", false);
                object.put("error", new Error("connection failed: device not found", new Throwable("connectionFailed:DeviceNotFound")));
                this.openSerialCall.resolve(object);
                return;
            }
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            if (driver == null) {
                object.put("success", false);
                object.put(
                    "error",
                    new Error("connection failed: no driver for device", new Throwable("connectionFailed:NoDriverForDevice"))
                );
                this.openSerialCall.resolve(object);
                return;
            }
            if (driver.getPorts().size() < portNum) {
                object.put("success", false);
                object.put(
                    "error",
                    new Error("connection failed: not enough ports at device", new Throwable("connectionFailed:NoAvailablePorts"))
                );
                this.openSerialCall.resolve(object);
                return;
            }
            usbSerialPort = driver.getPorts().get(portNum);
            UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
            if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
                usbPermission = UsbPermission.Requested;
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(
                    this.appCompatActivity,
                    0,
                    new Intent(INTENT_ACTION_GRANT_USB),
                    PendingIntent.FLAG_MUTABLE
                );
                this.appCompatActivity.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
                usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
                return;
            }
            if (usbConnection == null) {
                if (!usbManager.hasPermission(driver.getDevice())) {
                    object.put("success", false);
                    object.put(
                        "error",
                        new Error("connection failed: permission denied", new Throwable("connectionFailed:UsbConnectionPermissionDenied"))
                    );
                } else {
                    object.put("success", false);
                    object.put(
                        "error",
                        new Error("connection failed: Serial open failed", new Throwable("connectionFailed:SerialOpenFailed"))
                    );
                }
                this.openSerialCall.resolve(object);
                return;
            }
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, dataBits, stopBits, parity);
            if (setDTR) usbSerialPort.setDTR(true);
            if (setRTS) usbSerialPort.setRTS(true);
            object.put("success", true);
            object.put("data", "connection succeeded: Connection open");
            serialInputOutputManager = new SerialInputOutputManager(usbSerialPort, this);
            serialInputOutputManager.start();
            connected = true;
        } catch (Exception exception) {
            object.put("success", false);
            object.put("error", new Error(exception.getMessage(), exception.getCause()));
            disconnect();
        }

        this.openSerialCall.resolve(object);
    }

    public JSObject closeSerial() {
        JSObject object = new JSObject();
        if (readDataCall != null) {
            object.put("success", false);
            readDataCall.resolve();
        }
        // Make sure we don't die if we try to close an non-existing port!
        disconnect();
        object.put("success", true);
        object.put("data", "Connection Closed");
        return object;
    }

    public JSObject readSerial() {
        JSObject object = new JSObject();
        if (!connected) {
            object.put("error", new Error("not connected", new Throwable("NOT_CONNECTED")));
            object.put("success", false);
            return object;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            String str = HexDump.toHexString(Arrays.copyOf(buffer, len));
            str.concat("\n");
            object.put("data", str);
            object.put("success", true);
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            object.put("success", false);
            object.put("error", new Error("connection lost: " + e.getMessage(), e.getCause()));
            disconnect();
        }
        return object;
    }

    public JSObject writeSerial(String str) {
        JSObject object = new JSObject();
        if (!connected) {
            object.put("error", new Error("not connected", new Throwable("NOT_CONNECTED")));
            object.put("success", false);
            return object;
        }
        if (str.length() == 0) {
            object.put("error", new Error("can't send empty string to device", new Throwable("EMPTY_STRING")));
            object.put("success", false);
            return object;
        }
        try {
            byte[] data = (str + "\r\n").getBytes();
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
            object.put("data", str);
            object.put("success", true);
            return object;
        } catch (Exception e) {
            object.put("success", false);
            object.put("error", new Error("connection lost: " + e.getMessage(), e.getCause()));
            disconnect();
            return object;
        }
    }

    public void onResume() {
        if (sleepOnPause) {
            if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted) mainLooper.post(
                () -> {
                    openSerial(this.appCompatActivity, this.openSerialCall);
                }
            );
        }
    }

    public void onPause() {
        if (connected && sleepOnPause) {
            disconnect();
        }
    }

    public JSObject registerUsbAttachedDetachedCallback(AppCompatActivity activity, PluginCall pluginCall) {
        this.appCompatActivity = activity;
        JSObject jsObject = new JSObject();
        usbAttachedDetachedCall = pluginCall;
        pluginCall.setKeepAlive(true);
        this.appCompatActivity.registerReceiver(broadcastReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        this.appCompatActivity.registerReceiver(broadcastReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
        jsObject.put("success", true);
        jsObject.put("data", "REGISTERED");
        return jsObject;
    }

    public JSObject registerReadCallback(PluginCall pluginCall) {
        JSObject object = new JSObject();
        this.readDataCall = pluginCall;
        pluginCall.setKeepAlive(true);
        object.put("success", true);
        object.put("data", "REGISTERED".getBytes(Charset.defaultCharset()));
        return object;
    }

    private void updateReceivedData(byte[] data) {
        if (this.readDataCall != null) {
            JSObject jsObject = new JSObject();
            this.readDataCall.setKeepAlive(true);
            try {
                String str = HexDump.toHexString(data);
                str.concat("\n");
                jsObject.put("data", str);
                jsObject.put("success", true);
            } catch (Exception exception) {
                jsObject.put("error", new Error(exception.getMessage(), exception.getCause()));
                jsObject.put("success", false);
            }
            readDataCall.resolve(jsObject);
        }
    }

    private void updateReadDataError(Exception exception) {
        if (readDataCall != null) {
            JSObject jsObject = new JSObject();
            jsObject.put("error", new Error(exception.getMessage(), exception.getCause()));
            jsObject.put("success", false);
            readDataCall.resolve(jsObject);
        }
    }
}
