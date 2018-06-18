import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public class Router implements Runnable {
    private boolean threadSuspended = false;
    private boolean run = true;
    private final ConcurrentHashMap<String, RouteRow> routingTable = new ConcurrentHashMap<>();
    private final ArrayList<Interface> interfaces = new ArrayList<>();
    private static final int TABLE_MAX_SIZE = 25;
    private static final int TIMEOUT_SIZE = 1000; // normally 30 seconds
    private static final long GARBAGE_COLLECTOR_TIME = 3000; // normally 120 seconds
    private static final long ROUTE_TTL = 5000; // normally 120 seconds
    private static final PrintStream out = System.out;


    /**
     * create a new interface wrapper function
     *
     * @param inter    - the interface address and port
     * @param neighbor - the remote address and port
     */
    void newInterface(String inter, String neighbor) {
        newInterface(inter, neighbor, 1);
    }

    /**
     * creates a new interface/connection/edge between 2 routers
     *
     * @param inter    - the interface address and port
     * @param neighbor - the remote interface and port it should connect to
     * @param cost     - the cost of the port
     */
    private void newInterface(String inter, String neighbor, int cost) {
        try {
            String[] in = inter.split(":");
            String[] nei = neighbor.split(":");
            DatagramSocket sock = new DatagramSocket(Integer.parseInt(in[1]), InetAddress.getByName(in[0]));
            interfaces.add(new Interface(sock, InetAddress.getByName(nei[0]), Integer.parseInt(nei[1])));
        } catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * a subnet entry creator
     *
     * @param network - the network address it is advertising
     * @param netMask - the subnet mask for the network
     * @param cost    - the cost, which is always 0
     */
    void newSubnetEntry(InetAddress network, int netMask, int cost) {
        RouteRow row;
        try {
            row = new RouteRow(
                    network, netMask,
                    new Interface(
                            new DatagramSocket(null),
                            InetAddress.getByName("0.0.0.0"),
                            0),
                    cost + 1);
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        if (routingTable.size() >= TABLE_MAX_SIZE) return;
        routingTable.put(network.toString().replace("/", "") + "/" + netMask, row);
    }

    /**
     * synchronized print function for the router table
     */
    public void print() {
        synchronized (out) {
            out.printf("\n\n%s \t\t %s \t\t %s\n", "Address", "Next Hop", "Cost");
            out.println("====================================================");
            routingTable.values()
                    .stream()
                    .sorted(Comparator.comparingInt(RouteRow::getMetric))
                    .forEach(row -> out.println(row.toString()));
        }
    }

    /**
     * gets string
     *
     * @return the string
     */
    public String toString() {
        StringBuilder x = new StringBuilder();
        x.append("\n\nAddress \t\t Next Hop \t\t Cost\n");
        x.append("====================================================\n");
        routingTable.forEach((i, row) -> x.append(row.toString()));
        return x.toString();
    }

    /**
     * main loop for router, constantly scans the interfaces for new packets
     */
    public void run() {
        interfaces.forEach(runner -> new Thread(runner).start());
        GarbageCollector gar = new GarbageCollector();
        Broadcast br = new Broadcast();
        new Thread(br).start();
        new Thread(gar).start();

        boolean changes = false;

        //TODO broadcast request
        while (run) {
            if (threadSuspended) continue;

            // get incoming messages
            for (Interface inter : interfaces) {
                if (!inter.newData()) continue;
                for (DatagramPacket mess : inter.getData()) {
                    if (mess.getData()[0] == 2) { // a response, for now don't worry about unsolicited responses
                        changes = handleRouteResponse(mess, inter);
                    } else if (mess.getData()[0] == 1) { // request, need to send a correctly formatted response
                        //TODO respond to a RIP request
                    }
                }
            }

            // print table is changes made
            if (changes) {
                print();
                changes = false;
            }
        }
        interfaces.forEach(Interface::kill);
        gar.kill();
        br.kill();
    }

    /**
     * the method to handle a route response
     *
     * @param packet - response packet to take
     * @param inter  - the interface it came from
     * @return whether changes were made to the routing table
     */
    private boolean handleRouteResponse(DatagramPacket packet, Interface inter) {
        boolean changes = false;
        byte[] resp = packet.getData();
        // Get data appears to include the udp header
        for (int i = 4; i < packet.getLength(); i += 20) {
            //grab the fields

            InetAddress remoteAddress;
            InetAddress nextHop;
            try {
                remoteAddress = InetAddress.getByAddress(Arrays.copyOfRange(resp, i + 4, i + 8));
                nextHop = InetAddress.getByAddress(Arrays.copyOfRange(resp, i + 12, i + 16));
            } catch (UnknownHostException e) {
                e.printStackTrace();
                continue;
            }
            int nextHopPort = bytesToShort(resp, i + 2) & 0xFFFF; // hijacking the the route tag to make this work on UDP
            int subnetMask = SubmaskToCIDR(bytesTo32Int(resp, i + 8));
            int metric = bytesTo32Int(resp, i + 16);

            if (interfaces.parallelStream().anyMatch(in -> in.getLocalAddress().equals(nextHop.toString()) &&
                    in.getLocalPort() == nextHopPort)) {
                metric = 16; // infinity
            }

            String address = remoteAddress.toString().replace("/", "") + "/" + subnetMask;
            if (metric > 15) continue;
            if (!routingTable.containsKey(address)) {
                routingTable.put(address, new RouteRow(remoteAddress, subnetMask, inter, metric + 1));
                broadcastResponse();
                changes = true;
            } else if (routingTable.get(address).getMetric() > metric + 1) { // if metric is less update
                routingTable.put(address, new RouteRow(remoteAddress, subnetMask, inter, metric + 1));
                broadcastResponse();
                changes = true;
                //Check if the current row is from the same router, if so update
            } else if (routingTable.get(address).getInter().getRemoteAddress().equals(packet.getAddress().toString()) &&
                    routingTable.get(address).getInter().getRemotePort() == packet.getPort()) {
                if (metric + 1 != routingTable.get(address).getMetric()) changes = true;
                routingTable.put(address, new RouteRow(remoteAddress, subnetMask, inter, metric + 1));
            }

        }
        return changes;
    }

    /**
     * converts byte array to int
     *
     * @param arr   byte array to pull from
     * @param start the start of the array
     * @return int equivalent to the byte array inserted
     */
    private static int bytesTo32Int(byte[] arr, int start) {
        //assumes little endianness might need to and to get around signing
        return (arr[start] << 24) | ((arr[start + 1] & 0xFF) << 16) | ((arr[start + 2] & 0xFF) << 8) | (arr[start + 3] & 0xFF);
    }

    /**
     * converts by to short
     *
     * @param arr   the array to pull from
     * @param start start index of the array to pull from
     * @return short equivalent to the byte array inserted
     */
    private static short bytesToShort(byte[] arr, int start) {
        return (short) ((arr[start] >> 8) | arr[start + 1]);
    }

    /**
     * broadcasts the route table using rip to all interfaces
     */
    private void broadcastResponse() {
        interfaces.forEach(inter -> inter.send(ripResponse(inter)));
    }

    /**
     * builds the rip response message
     *
     * @param inter the interface that it will use to send out the rip response message
     * @return the m to byte array
     */
    private byte[] ripResponse(Interface inter) {
        ByteArrayOutputStream msg = responseHeader();
        routingTable.forEach((i, row) -> row.advertisement(msg, inter));
        return msg.toByteArray();
    }

    /**
     * builds the rip response header
     *
     * @return the msg as a ByteArrayOutputStream
     */
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

    /**
     * the garbage collector for the router
     */
    private class GarbageCollector implements Runnable {
        private boolean running = true;

        @Override
        public void run() {

            //TODO: implement RIP requests
            while (running) {
                boolean change = false;
                for (String key : routingTable.keySet()) {
                    if (System.currentTimeMillis() - routingTable.get(key).getTimestamp() > ROUTE_TTL &&
                            !routingTable.get(key).getInter().getLocalAddress().equals("0.0.0.0")) {
                        routingTable.remove(key);
                        change = true;
                    }
                }
                if (change) print();
                try {
                    Thread.sleep(GARBAGE_COLLECTOR_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void kill() {
            running = false;
        }
    }

    /**
     * thread to broadcast a rip response every so often
     */
    private class Broadcast implements Runnable {
        private boolean running = true;

        @Override
        public void run() {
            while (running) {
                broadcastResponse();
                try {
                    Thread.sleep(TIMEOUT_SIZE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void kill() {
            running = false;
        }
    }

    /**
     * this was taken from Hacker's delight section 5.1 after being pointed to it from a stack overflow post that
     * recommended it as the fastest way to convert subnet mask to cidr.
     * How do you properly cite in code
     */
    private static int SubmaskToCIDR(int x) {
        x = x - ((x >>> 1) & 0x55555555);
        x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
        x = (x + (x >>> 4)) & 0x0F0F0F0F;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        return x & 0x0000003F;
    }

    void suspend() {
        threadSuspended = true;
    }

    void resume() {
        threadSuspended = false;
    }

    void kill() {
        run = false;
    }
}
