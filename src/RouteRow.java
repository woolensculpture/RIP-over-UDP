import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;

public class RouteRow {

	private final short cost;
	private final InetAddress interfaceAddress;
	private final int ID;
	private final InetAddress network;
	private final int netMask;
	private final String[] arr = new String[5];
	private int forget = 0;
	
	RouteRow(InetAddress network, int netMask, InetAddress interfaceAddress, int ID, short cost){
		this.network = network;
		this.netMask = netMask;
		this.interfaceAddress = interfaceAddress;
		this.ID = ID;
		this.cost = cost;

	}
	
	InetAddress getInterfaceAddress(){
		return interfaceAddress;
	}
	
	int getCost(){
		return cost;
	}
	
	int getDeleteValue(){
		return forget;
	}
	
	void setDeleteValue(int value){
		forget = value;
	}

	public String toString(){
		return String.format("%s/%s \t %s:%d \t\t %d",
                network.getHostAddress(),
                netMask,
                interfaceAddress.getHostAddress(),
                ID,
                cost);
	}

	String advertisement(String dest, int Port){
		return String.format("%s/%s \t %s:%d \t %d", network.getHostAddress(), netMask, dest, Port, cost);
	}

	ByteArrayOutputStream advertisement(ByteArrayOutputStream msgHeader){
		try {
			msgHeader.write(new byte[]{0,1}); // family address identifier, 1 for IPv4 no other protocols supported
			msgHeader.write(new byte[]{0,0}); // Route Tag, to be used as the Port address of t
			msgHeader.write(network.getHostAddress().getBytes()); // internet address, 4 bytes
			msgHeader.write(toBytes(CIDRToSubmask(netMask))); // netMask, 4 bytes
			msgHeader.write(new byte[]{0, 0, 0, 0}); // Next Hop 4 bytes not implemented
			msgHeader.write(toBytes(cost)); // Metric 4 bytes
		} catch (IOException e) {
			e.printStackTrace();
		}
		return msgHeader;
	}

	String[] toArray(){
	    if(arr[0] != null) return arr;
		arr[0] = network.getHostAddress();
		arr[1] = Integer.toString(netMask);
		arr[2] = interfaceAddress.getHostAddress();
		arr[3] = Integer.toString(ID);
		arr[4] = Integer.toString(cost);
		return arr;
	}

	String getHostAddress(){
	    return network.getHostAddress();
    }
	
	boolean compareTo(String[] input){
		String[] l = toArray();
		return input[0].equals(l[0]) && input[1].equals(l[1]) && input[3].equals(l[3]) && input[4].equals(l[4]);
	}

	private byte[] toBytes(int i)
	{
		byte[] result = new byte[4];
		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}

	private byte[] toBytes(short i) {
		byte[] result = new byte[2];
		result[0] = (byte) (i >> 8);
		result[1] = (byte) (i);

		return result;
	}

	private static int CIDRToSubmask(int cidr){
		return 0xffffffff << (32 - cidr);
	}

	/**
	 * 	this was taken from Hacker's delight section 5.1 after being pointed to it from a stack overflow post that
	 * 	recommended it as the fastest way to convert subnet mask to cidr.
	 * 	How do you properly cite in code
	 */
	private static int SubmaskToCIDR(int x){
		x = x - ((x >>> 1) & 0x55555555);
		x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
		x = (x + (x >>> 4)) & 0x0F0F0F0F;
		x = x + (x >>> 8);
		x = x + (x >>> 16);
		return x & 0x0000003F;
	}
}
