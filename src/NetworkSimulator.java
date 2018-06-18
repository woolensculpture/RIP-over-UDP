import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class NetworkSimulator {
    private static final String usage = "java -jar rip2sim.jar [-v] file";
    private static final String CMD = "Commands: list, info [ID], kill (ID), stop [ID], resume [ID], quit, help, ?";
    private static final ArrayList<Router> routers = new ArrayList<>();
    private static boolean verbose;

    public static void main(String[] args) {
        verbose = args[0].contains("-v");
        Arrays.stream(args)
                .map(File::new)
                .flatMap(file -> file.isDirectory() ? Arrays.stream(file.listFiles()) : Stream.of(file))
                .distinct()
                .filter(File::exists)
                .filter(File::isFile)
                .filter(file -> file.getName().endsWith(".txt"))
                .map(file -> {
                    try {
                        System.out.println("initializing node with file: " + file.getName());
                        return InitializeNode(new Scanner(file));
                    } catch (FileNotFoundException e) {
                        System.err.println(e.getMessage());
                        System.out.println("Ignoring File");
                        return null;
                    }
                }).forEach(routers::add);

        routers.forEach(router -> new Thread(router).start());

        Scanner input = new Scanner(System.in);
        boolean running = true;

        routers.forEach(Router::print);
        while (running) {
            String[] l = input.nextLine().split("\\s+");
            switch (l[0].toLowerCase()) {
                case "quit":
                case "kill":
                case "3":
                    if (l.length == 1) {
                        routers.forEach(Router::kill);
                        running = false;
                    } else if (l.length == 2) {
                        try {
                            getByID(Integer.parseInt(l[1])).kill();
                        } catch (NumberFormatException | NullPointerException y) {
                            System.err.println("Usage: kill (router ID)");
                        }
                    }
                    break;
                case "l":
                case "list":
                    IntStream.range(0, routers.size()).forEachOrdered(index -> {
                        System.out.println("router ID: " + index);
                        routers.get(index).print();
                    });
                    break;

                case "i":
                case "info":
                    try {
                        getByID(Integer.parseInt(l[1])).print();
                    } catch (NumberFormatException | NullPointerException y) {
                        System.err.println("invalid ID");
                    } catch (ArrayIndexOutOfBoundsException f) {
                        System.err.println("Usage: 'info [router ID]'");
                    }
                    break;
                case "s":
                case "stop":
                    try {
                        getByID(Integer.parseInt(l[1])).suspend();
                    } catch (NumberFormatException | NullPointerException e) {
                        System.err.println("invalid ID");
                    } catch (ArrayIndexOutOfBoundsException f) {
                        System.err.println("Usage: 'stop [router ID]'");
                    }
                    break;

                case "r":
                case "resume":
                    try {
                        getByID(Integer.parseInt(l[1])).resume();
                    } catch (NumberFormatException | NullPointerException e) {
                        System.err.println("invalid ID");
                    } catch (ArrayIndexOutOfBoundsException f) {
                        System.err.println("Usage: 'resume [router ID]'");
                    }
                    break;

                case "help":
                case "?":
                    System.out.println(CMD);
                    break;
                default:
                    System.out.println("Input invalid");
                    System.out.println(CMD);
                    break;
            }
        }
        input.close();
    }

    private static Router getByID(int ID) {
        if (0 > ID || ID >= routers.size()) return null;
        return routers.get(ID);
    }

    private static Router InitializeNode(Scanner config) {
        String reader;
        Router router = new Router();

        while (config.hasNext()) {
            reader = config.nextLine();
            String[] liner = reader.split("\\s+|/");
            switch (liner[0]) {
                case "LINK:":
                    router.newInterface(liner[1], liner[2]);
                    break;

                case "NETWORK:":
                    try {
                        router.newSubnetEntry(InetAddress.getByName(liner[1]),
                                Integer.parseInt(liner[2]), (short) -1);
                    } catch (NumberFormatException | UnknownHostException e) {
                        System.err.println(usage);
                        System.exit(1);
                    }
                    break;
            }
            if (verbose) System.out.println(reader);
        }
        config.close();
        return router;
    }
}
