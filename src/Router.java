import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Router implements Runnable {
	private boolean threadSuspended = false;
	private boolean run = true;
	private static final Pattern redox = Pattern.compile("\\s+|:|/");
	private final ArrayList<String> interfaces = new ArrayList<>();
	private ArrayList<RouteRow> routingTable = new ArrayList<>();
	private ArrayList<DatagramSocket> sockets = new ArrayList<>();
	private ArrayList<ReceiveQueue> receivers = new ArrayList<>();
	private static final int TABLE_MAX_SIZE = 25;
	private static final int TIMEOUT_SIZE = 7000;
	private static final PrintStream out = System.out;



	void addEdge(String inter, String neighbor) {
        addEdge(inter, neighbor, 1);
	}

	private void addEdge(String inter, String neighbor, int cost){
		try {
			String[] s = neighbor.split(":");
			DatagramSocket sock = new DatagramSocket(Integer.parseInt(s[1]), InetAddress.getByName(s[0]));
			sockets.add(sock);
			receivers.add(new ReceiveQueue(sock));
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
		interfaces.add(inter);
	}

    boolean newTableEntry(InetAddress netInterface, int netMask, InetAddress address, int ID, int cost){
		RouteRow e = new RouteRow(netInterface,netMask, address, ID, (short) (cost + 1));
		if(routingTable.size() < TABLE_MAX_SIZE){
			routingTable.add(e);
			return true;
		}
		return false;
	}
	
	private RouteRow newTableEntry(String netInterface, String netMask, String address, String ID, String cost){
		try {
			return new RouteRow(InetAddress.getByName(netInterface),Integer.parseInt(netMask),
					InetAddress.getByName(address), Integer.parseInt(ID), (short)(1 + Short.parseShort(cost)));
		} catch (NumberFormatException | UnknownHostException ignored) { }
		return null;
	}

	void print() {
	    synchronized (out){
            out.printf("\n\n%s \t\t %s \t\t %s\n", "Address","Next Hop","Cost");
            out.println("====================================================");
            routingTable.stream().map(RouteRow::toString).forEach(out::println);
        }
	}

	public void run() {
		receivers.forEach(runner -> new Thread(runner).start());
		while(run) {
            if (threadSuspended) continue;
            boolean changes = false;

            // first advertise to other nodes
            for(DatagramSocket socket: sockets) {
                for(RouteRow row: routingTable) {
                    String[] l = interfaces.get(sockets.indexOf(socket)).split(":");
                    String msg = row.advertisement(l[0], socket.getLocalPort());
                    try {
                        socket.send(new DatagramPacket(msg.getBytes(),
                                msg.length(), InetAddress.getByName(l[0]), Integer.parseInt(l[1])));
                    } catch (IOException ignored) {
                        ignored.printStackTrace();
                    }
                }
            }
            //then get incoming messages
            Set<String> incomingMessages = new ConcurrentSkipListSet<>();
            receivers.stream().filter(ReceiveQueue::newData).map(ReceiveQueue::getData).forEach(incomingMessages::addAll);

            ArrayList<String> newMessages = new ArrayList<>();
            Set<RouteRow> adders = new HashSet<>();

            //
            for(RouteRow row : routingTable) {
                row.setDeleteValue(row.getDeleteValue() + 1);
                for(String d : incomingMessages) {
                    String[] receivedData = redox.split(d);
                    if(row.compareTo(receivedData) || Integer.parseInt(receivedData[4]) == 15)
                        newMessages.add(d);
                }
            }
            incomingMessages.removeAll(newMessages);
            newMessages.clear();

            for(RouteRow row : routingTable){
                String[] table = row.toArray();
                for(String d : incomingMessages){
                    String[] receivedData = redox.split(d);
                    if(table[0].equals(receivedData[0]) &&
                            table[1].equals(receivedData[1]) && table[2].equals(receivedData[2])){
                        row.setDeleteValue(0);
                        newMessages.add(d);
                    }
                }
            }
            incomingMessages.removeAll(newMessages);
            newMessages.clear();

            for(String d : incomingMessages){
                Iterator<RouteRow> iterator = routingTable.iterator();
                while(iterator.hasNext()){
                    RouteRow next = iterator.next();
                    String[] row = next.toArray();
                    String[] input = redox.split(d);
                    if(input[0].equals(row[0])){
                        if((Integer.parseInt(input[4]) + 1) < next.getCost()) {
                            iterator.remove();
                            adders.add(newTableEntry(input[0], input[1], input[2], input[3], input[4]));
                            newMessages.add(d);
                            changes = true;
                        }
                        else if(input[2].equals(row[2])) next.setDeleteValue(0);
                        else newMessages.add(d);
                    }
                }
            }

            incomingMessages.removeAll(newMessages);
            newMessages.clear();

            Stream.concat(
                    adders.stream(),
                    incomingMessages.stream().map(redox::split)
                        .map(arr -> newTableEntry(arr[0], arr[1], arr[2], arr[3], arr[4]))
            ).collect(Collectors.toMap(RouteRow::getHostAddress, p -> p, (p, q) -> p)).values().forEach(a -> {
                    if(routingTable.size() < TABLE_MAX_SIZE) routingTable.add(a);
                });
            if(!incomingMessages.isEmpty()) changes = true;

            routingTable.removeAll(routingTable.stream()
                .filter(entry -> entry.getDeleteValue() >= 1 &&
                        !entry.getInterfaceAddress().getHostAddress().equals("0.0.0.0"))
                .collect(Collectors.toList())
            );

            // print table is changes made
            if(changes) print();
            try { Thread.sleep(TIMEOUT_SIZE); }
            catch (InterruptedException ignored) {}
            incomingMessages.clear();
        }
        receivers.forEach(ReceiveQueue::kill);
	}

	private boolean sendResponse(DatagramSocket socket, byte[] msg){
        return true;
    }

	private byte[] ripResponse() {
        // first advertise to other nodes
        ByteArrayOutputStream msg = responseHeader();
        routingTable.forEach(row -> row.advertisement(msg));
        return msg.toByteArray();
    }

    private static ByteArrayOutputStream responseHeader() {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        msg.write(2); // command response
        msg.write(2); // version
        msg.write(0); // zero padding
        msg.write(0); // zero padding
        // ones 2 bytes
        // auth type 2 bytes
        // authentication 16 bytes
        //TODO maybe add fields for auth
        return msg;
    }

    private class garbageCollector implements Runnable{
        @Override
        public void run() {

        }
    }

    private class broadcast implements Runnable{
        @Override
        public void run() {

        }
    }

    void suspend(){
		threadSuspended = true;
	}
    void resume(){
		threadSuspended = false;
	}
    void kill(){ run = false; }
}
