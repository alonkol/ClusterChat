package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
public class ConnectionTests {
    @Before
    public void setup() {
        PowerMockito.mockStatic(Log.class);
    }

    public MockNetwork createStarNetwork(int n){
        MockNetwork network = new MockNetwork();
        MockDevice a = network.addDevice("1");
        for (int i=2; i<=n; i++){
            MockDevice b = network.addDevice(Integer.toString(i));
            network.connect(a, b);
        }
        return network;
    }

    public MockNetwork createLineNetwork(int n){
        MockNetwork network = new MockNetwork();
        MockDevice a = network.addDevice("1");
        for (int i=2; i<=n; i++){
            MockDevice b = network.addDevice(Integer.toString(i));
            network.connect(a, b);
            a = b;
        }
        return network;
    }

    @Test
    public void testBroadcastStarNetwork(){
        MockNetwork network = createStarNetwork(7);
        network.getDevice("1").broadcast(MessageTypes.TEXT, "Hi");
        network.getDevice("2").broadcast(MessageTypes.TEXT, "Hi");
        ArrayList<String> messages = new ArrayList<>();
        messages.add("Hi");
        assertEquals(network.getDevice("1").getMessages(), messages);
        assertEquals(network.getDevice("2").getMessages(), messages);
        assertEquals(network.getDevice("3").getMessages(), messages);
        assertEquals(network.getDevice("4").getMessages(), messages);
        assertEquals(network.getDevice("5").getMessages(), messages);
        assertEquals(network.getDevice("6").getMessages(), messages);
        assertEquals(network.getDevice("7").getMessages(), messages);
    }

    @Test
    public void testBroadcastLineNetwork(){
        MockNetwork network = createLineNetwork(7);
        network.getDevice("1").broadcast(MessageTypes.TEXT, "Hi");
        network.getDevice("2").broadcast(MessageTypes.TEXT, "Hi");
        ArrayList<String> messages = new ArrayList<>();
        messages.add("Hi");
        assertEquals(network.getDevice("1").getMessages(), messages);
        assertEquals(network.getDevice("2").getMessages(), messages);
        assertEquals(network.getDevice("3").getMessages(), messages);
    }

    @Test
    public void testMultiHopsStar(){
        MockNetwork network = createStarNetwork(7);
        MockDevice one = network.getDevice("1");
        MockDevice two = network.getDevice("2");
        MockDevice seven = network.getDevice("7");
        network.sendMessage(
                one, seven,
                MessageTypes.TEXT, "Hi");
        network.sendMessage(
                two, seven,
                MessageTypes.TEXT, "Hello");
        ArrayList<String> messages = new ArrayList<>();
        messages.add("Hi");
        messages.add("Hello");
        assertEquals(network.getDevice("7").getMessages(), messages);
    }

    @Test
    public void testMultiHopsLine(){
        MockNetwork network = createLineNetwork(7);
        MockDevice one = network.getDevice("1");
        MockDevice two = network.getDevice("2");
        MockDevice seven = network.getDevice("7");
        network.sendMessage(
                one, seven,
                MessageTypes.TEXT, "Hi");
        network.sendMessage(
                two, seven,
                MessageTypes.TEXT, "Hello");
        ArrayList<String> messages = new ArrayList<>();
        messages.add("Hi");
        messages.add("Hello");
        assertEquals(network.getDevice("7").getMessages(), messages);
    }

    @Test
    public void testDisconnectMiddle(){
        MockNetwork network = createStarNetwork(7);
        ArrayList<String> result = new ArrayList<>();
        result.add("1");
        assertEquals(network.getDevice("2").showRouting(), result);
        network.disconnect(network.getDevice("1"), network.getDevice("2"));
        result.remove(0);
        assertEquals(network.getDevice("2").showRouting(), result);
    }
}
