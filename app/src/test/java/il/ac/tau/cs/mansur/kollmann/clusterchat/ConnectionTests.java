package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
public class ConnectionTests {
    @Before
    public void setup() {
        PowerMockito.mockStatic(Log.class);
    }

    @Test
    public void connectDevices(){
        MockDevice a = new MockDevice("a", "10");
        MockDevice b = new MockDevice("b", "9");
        a.initiateConnection(b);
        a.sendMessage(b, "Hello", MessageTypes.TEXT);

    }
}
