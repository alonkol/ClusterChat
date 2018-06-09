package il.ac.tau.cs.mansur.kollmann.clusterchat;

import java.util.HashMap;

public class MockNetwork {
    private final HashMap<String, MockDevice> devices;

    public MockNetwork(){
        devices = new HashMap<>();
    }

    public MockDevice addDevice(String id){
        MockDevice d = new MockDevice(id, this);
        devices.put(id, d);
        return d;
    }

    public MockDevice getDevice(String id){
        return devices.get(id);
    }

    public void connect(MockDevice A, MockDevice B){
        A.connect(B);
        B.connect(A);
    }

    public void disconnect(MockDevice A, MockDevice B){
        A.disconnect(B);
        B.disconnect(A);
    }

    public void showRouting(MockDevice A){
        A.showRouting();
    }

    public void sendMessage(MockDevice A, MockDevice B, MessageTypes type, String msg){
        MessageBundle msgBundle = new MessageBundle(msg, type, A.deviceContact, B.deviceContact);
        A.sendMessage(B, msgBundle);
    }
}
