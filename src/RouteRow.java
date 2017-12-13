import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;

public class RouteRow {

	private final int metric;
	private final Interface inter;
	private final InetAddress network;
	private final int netMask;
	private final long timestamp = System.currentTimeMillis();

    /**
     * contructor for router row
     * @param network - the network to advertise
     * @param netMask - the netmask of the network to advertise
     * @param inter - the interface to send as the next hope
     * @param cost - the cost of the route
     */
	RouteRow(InetAddress network, int netMask, Interface inter, int cost){
		this.network = network;
		this.netMask = netMask;
		this.inter = inter;
		this.metric = cost;
	}

    /**
     * gets the interface which is the route's next hop
     * @return
     */
	Interface getInter(){
		return inter;
	}

    /**
     * the metric of the routerow
     * @return
     */
	int getMetric(){
		return metric;
	}

    /**
     * the timestamp of the route row's creation
     * @return
     */
	long getTimestamp(){
		return timestamp;
	}

    /**
     * creates a string representation of the routerow
     * @return string of the route row
     */
	public String toString(){
		return String.format("%s/%d \t %s:%d \t\t %d",
                network.getHostAddress(),
                netMask,
                inter.getLocalAddress(),
                inter.getLocalPort(),
                metric);
	}

    /**
     * creates the RIP response entry for the row
     * @param msgHeader - the message header of the RIP header
     * @param inter - the interface it will be sent through, needed for poison reverse
     * @return the byte stream with the appended entry
     */
	ByteArrayOutputStream advertisement(ByteArrayOutputStream msgHeader, Interface inter){
		try {
			msgHeader.write(new byte[]{0,2}); // family address identifier, 2 for IPv4 no other protocols supported
			msgHeader.write(toBytes((short)this.inter.getLocalPort())); // Route Tag, to be used as the Port address of the remote hop
			msgHeader.write(network.getAddress()); // internet address, 4 bytes
			msgHeader.write(toBytes(CIDRToSubmask(netMask))); // netMask, 4 bytes
			msgHeader.write(this.inter.getLocalAddressBytes()); // Next Hop 4 bytes, since we don't have enough room the port is in the Route tag field
			if(inter == this.inter){
			    msgHeader.write(toBytes(16));
            } else {
                msgHeader.write(toBytes(metric)); // Metric 4 bytes
            }
		} catch (IOException e) {
			e.printStackTrace();
		}
		return msgHeader;
	}

    /**
     * converts a int to byte array
     * @param i the int to convert
     * @return a byte array of the int
     */
	private byte[] toBytes(int i)
	{
		byte[] result = new byte[4];
		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}

    /**
     * converts a short to byte array
     * @param i the short
     * @return a byte array of the short
     */
	private byte[] toBytes(short i) {
		byte[] result = new byte[2];
		result[0] = (byte) (i >> 8);
		result[1] = (byte) (i);

		return result;
	}

    /**
     * converts CIDR int to subnet mask int
     * @param cidr - integer representing the CIDR class
     * @return int that is a subnet mask
     */
	private static int CIDRToSubmask(int cidr){
		return 0xffffffff << (32 - cidr);
	}
}
