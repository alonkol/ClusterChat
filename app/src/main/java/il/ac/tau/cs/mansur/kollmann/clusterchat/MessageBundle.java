package il.ac.tau.cs.mansur.kollmann.clusterchat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageBundle {
    private DeviceContact contact;
    private byte[] messageBuffer;

    public MessageBundle(DeviceContact contact, byte[] buffer){
        this.contact = contact;
        this.messageBuffer = buffer;
    }

    public DeviceContact getContact() {
        return contact;
    }

    public byte[] getMessageBuffer() {
        return messageBuffer;
    }

    public static MessageBundle fromBytes(Object byteBundle) {
        byte[] bytes = (byte[]) byteBundle;
        byte[] deviceInfo = new byte[50];
        byte[] messageBuffer = new byte[bytes.length - 50];
        System.arraycopy(bytes, 0, deviceInfo, 0, 50);
        System.arraycopy(bytes, 50, messageBuffer, 0, bytes.length - 50);
        String info = new String(deviceInfo);
        info = info.substring(0, info.indexOf('~'));
        String[] nameAddress = info.split("\n");
        return new MessageBundle(new DeviceContact(nameAddress[1], nameAddress[2]), messageBuffer);
    }

    public byte[] getBytes() {
        String contactInfo = contact.toString();
        byte[] byteInfo = contactInfo.getBytes();
        byte[] byteInfoPadded = new byte[50];
        for (int i=0; i<byteInfoPadded.length; i++){
            if (i < byteInfo.length){
                byteInfoPadded[i] = byteInfo[i];
            }else {
                byteInfoPadded[i] = '~';
            }
        }
        byte[] combined = new byte[byteInfoPadded.length + messageBuffer.length];

        System.arraycopy(byteInfoPadded,0,combined,0 ,byteInfoPadded.length);
        System.arraycopy(messageBuffer,0,combined,byteInfoPadded.length, messageBuffer.length);
        return combined;
    }
}
