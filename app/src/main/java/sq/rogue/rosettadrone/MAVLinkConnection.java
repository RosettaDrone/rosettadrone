package sq.rogue.rosettadrone;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class MAVLinkConnection {
    DatagramSocket socket;
    MainActivity.GCSCommunicatorAsyncTask.Listener listener;
    private final String TAG = this.getClass().getSimpleName();

    public MAVLinkConnection(String host, int port) {
        try {
            Log.e(TAG, "Connecting to " + host + ":" + port);
            socket = new DatagramSocket();
            socket.connect(InetAddress.getByName(host), port);
            //socket.setSoTimeout(10);

        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void send(byte[] bytes) throws IOException {
        socket.send(new DatagramPacket(bytes, bytes.length, socket.getInetAddress(), socket.getPort()));
    }

    public void close() {
        listener.close = true;
        listener.interrupt();
        try {
            Log.i(TAG, "Waiting to close listener...");
            listener.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        socket.disconnect();
        socket.close();
    }

    public void listen(MainActivity.GCSCommunicatorAsyncTask.Listener listener) {
        this.listener = listener;
        listener.start();
    }
}
