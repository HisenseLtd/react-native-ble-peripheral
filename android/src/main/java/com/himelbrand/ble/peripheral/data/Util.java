package com.himelbrand.ble.peripheral.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Util {
    public static byte[] packet_indicator_last ={0x5c, 0x6e};
    public static int packet_size = 20;

    public static byte[] appendData(byte[] firstObject,byte[] secondObject){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        try {
            if (firstObject!=null && firstObject.length!=0)
                outputStream.write(firstObject);
            if (secondObject!=null && secondObject.length!=0)
                outputStream.write(secondObject);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }


    public static List createPacketsToSend(byte[] message){
        List allPackets = new ArrayList();
        message = appendData(message, packet_indicator_last);

        int times = message.length/packet_size;

        for (short time = 0; time <= times; time++) {

            int startIndex = time*packet_size;
            int endIndex = Math.min((time+1)*packet_size, message.length-2);

            if(startIndex < endIndex) {
                byte[] packet =   Arrays.copyOfRange(message, startIndex, endIndex);
                allPackets.add(packet);
            }
        }
        return allPackets;
    }

}
