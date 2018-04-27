package il.ac.tau.cs.mansur.kollmann.clusterchat;

public class Link {
    private MockDevice a;
    private MockDevice b;

    public Link(MockDevice a, MockDevice b){
        this.a=a;
        this.b=b;
    }

    public MockDevice getDevice(DeviceContact dc){
        if (a.deviceContact == dc){
            return a;
        }else if(b.deviceContact == dc){
            return b;
        }
        return null;
    }
}
