package be.jonasclaes.capacitorserial;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Serial")
public class SerialPlugin extends Plugin {

    private Serial implementation = new Serial();

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void registerUsbAttachedDetachedCallback(PluginCall call) {
        call.resolve(implementation.registerUsbAttachedDetachedCallback(getActivity(), call));
    }

    @PluginMethod
    public void devices(PluginCall call) {
        call.resolve(implementation.devices(getActivity()));
    }

    @PluginMethod
    public void open(PluginCall call) {
        implementation.openSerial(getActivity(), call);
    }

    @PluginMethod
    public void close(PluginCall call) {
        call.resolve(implementation.closeSerial());
    }

    @PluginMethod
    public void read(PluginCall call) {
        call.resolve(implementation.readSerial());
    }

    @PluginMethod
    public void write(PluginCall call) {
        String data = call.hasOption("data") ? call.getString("data") : "";
        call.resolve(implementation.writeSerial(data));
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        implementation.onResume();
    }

    @Override
    protected void handleOnPause() {
        implementation.onPause();
        super.handleOnPause();
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void registerReadCallback(PluginCall call) {
        call.resolve(implementation.registerReadCallback(call));
    }
}
