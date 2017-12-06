import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class ReceiveQueue implements Runnable {
	
	private final DatagramSocket socket;
	private boolean newData = false;
	private final Set<String> received = new ConcurrentSkipListSet<>();
	private volatile boolean run = true;
	private static final int BUF_SIZE = 512;
	
	ReceiveQueue(DatagramSocket sock){
	    socket = sock;
	}

	public void run() {
		byte[] buf = new byte[BUF_SIZE];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		while(run) {
            try {
                socket.receive(packet);
                newData = true;
                received.add(new String(buf, 0, packet.getLength()));
            } catch (IOException e) {
                e.printStackTrace();
            }
		}
        socket.close();
	}
	
    boolean newData(){
		return newData;
	}
	
	HashSet<String> getData(){
		HashSet<String> ret = new HashSet<>(received);
        received.clear();
        newData = false;
		return ret;
	}


	
	void kill() {
		run = false;
	}
}
