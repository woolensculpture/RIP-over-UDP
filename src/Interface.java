import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Interface implements Runnable {
	
	private final DatagramSocket socket;
	private boolean newData = false;
	private final ConcurrentLinkedQueue<DatagramPacket> received = new ConcurrentLinkedQueue<>();
	private volatile boolean run = true;
	private static final int BUF_SIZE = 512;
	private final InetAddress remoteAddress;
	private final int remotePort;

    /**
     * constructor for an interface
     * @param sock - the datagramsocket to use
     * @param remoteAddr - the remote address to send and recieve from
     * @param remotePrt - the remote port to send and recieve from
     */
	Interface(DatagramSocket sock, InetAddress remoteAddr, int remotePrt){
	    socket = sock;
	    remoteAddress = remoteAddr;
	    remotePort = remotePrt;
	}

    /**
     * the main receive thread
     */
	public void run() {
	    if(socket == null || remoteAddress.toString().equals("0.0.0.0")) return;
		while(run) {
            byte[] buf = new byte[BUF_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                if(packet.getAddress().equals(remoteAddress) && packet.getPort() == remotePort && packet.getData()[0] == 2) {
                    newData = true;
                    received.add(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

		}
        socket.close();
	}

    /**
     * send a message to the remote port and address
     * @param data - the data to be sent, ususally a rip response
     */
	public void send(byte[] data){

	    if(socket == null || remoteAddress.toString().equals("0.0.0.0")) return;
	    try {
            socket.send(new DatagramPacket(data, data.length, remoteAddress, remotePort));
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    /**
     * get the remote port
     * @return remote port
     */
    int getRemotePort() {
	    return socket == null ? 0 : remotePort;
    }

    /**
     * get the remote address
     * @return the remote address in octal string format
     */
    String getRemoteAddress() {
	    return socket == null ? "0.0.0.0" : remoteAddress.toString();
	}

    /**
     * get the local address
     * @return the local address in octal string format
     */
    public String getLocalAddress() {
	    return socket == null ? "0.0.0.0" : socket.getLocalAddress().getHostAddress();
    }

    /**
     * get teh local address in bytes
     * @return byte array of the local address
     */
    public byte[] getLocalAddressBytes(){
	    return socket.getLocalAddress().getAddress();
    }

    /**
     * get the local port
     * @return
     */
    public int getLocalPort(){
	    return socket == null ? 0 : socket.getLocalPort();
    }

    /**
     * checks if there is new data on the receive queue
     * @return
     */
    boolean newData(){
		return newData;
	}

    /**
     * gets the datagram packets from the recieve queue
     * @return
     */
	List<DatagramPacket> getData() {
		List<DatagramPacket> packets = new ArrayList<>(received);
        received.clear();
        newData = false;
		return packets;
	}

    /**
     * kill the thread
     */
	void kill() {
		run = false;
	}
}
