package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class PackageQueue {
    public final String TAG = "PackageQueue";
    private Queue<MessageBundle> processingQueue= new LinkedBlockingQueue<>();;

    public void addPackage(MessageBundle mb) {
        Log.d(TAG, "Adding package to processing queue: " + mb);
        processingQueue.add(mb);
    }

    public MessageBundle getPackage(){
        return processingQueue.poll();
    }

}
