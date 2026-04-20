package cfds;

import cfds.core.Engine;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void printUsage() {
        System.out.println("CFDS - Criminal Face Detection System v1.0.0 (Java Edition)");
        System.out.println("Usage: cfds [OPTIONS] <MODE>\n");
        System.out.println("Modes:");
        System.out.println("  live      Run real-time detection from webcam");
        System.out.println("  image     Run detection on a single image file");
        System.out.println("  enroll    Add a new criminal to the database");
        System.out.println("  list      List all enrolled criminals");
        System.out.println("  delete    Remove a criminal from the database\n");
        System.out.println("Options:");
        System.out.println("  -c, --config <path>    Config file (default: config/default_config.json)");
        System.out.println("  -d, --device <id>      Camera device ID (default: 0)");
        System.out.println("  -i, --input <path>     Input image path (for image/enroll mode)");
        System.out.println("  -o, --output <path>    Output annotated image path (for image mode)");
        System.out.println("  --name <string>        Criminal name for enrollment");
        System.out.println("  --id <string>          Criminal ID (e.g. C001) for enrollment/deletion");
        System.out.println("  --crime <string>       Crime description for enrollment");
        System.out.println("  -h, --help             Show this help message");
    }

    public static void main(String[] args) {
        System.out.println("DEBUG: Java Executable launched successfully. args=" + args.length);
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        List<String> argList = new ArrayList<>();
        for (String arg : args) argList.add(arg);

        String mode = "";
        String configPath = "config/default_config.json";
        int deviceId = 0;
        String inputPath = "";
        String outputPath = "";
        String name = "";
        String crim_id = "";
        String crime = "";

        for (int i = 0; i < argList.size(); ++i) {
            String arg = argList.get(i);
            if (arg.equals("-h") || arg.equals("--help")) {
                printUsage();
                return;
            } else if (arg.equals("-c") || arg.equals("--config")) {
                if (i + 1 < argList.size()) configPath = argList.get(++i);
            } else if (arg.equals("-d") || arg.equals("--device")) {
                if (i + 1 < argList.size()) deviceId = Integer.parseInt(argList.get(++i));
            } else if (arg.equals("-i") || arg.equals("--input")) {
                if (i + 1 < argList.size()) inputPath = argList.get(++i);
            } else if (arg.equals("-o") || arg.equals("--output")) {
                if (i + 1 < argList.size()) outputPath = argList.get(++i);
            } else if (arg.equals("--name")) {
                if (i + 1 < argList.size()) name = argList.get(++i);
            } else if (arg.equals("--id")) {
                if (i + 1 < argList.size()) crim_id = argList.get(++i);
            } else if (arg.equals("--crime")) {
                if (i + 1 < argList.size()) crime = argList.get(++i);
            } else if (!arg.startsWith("-")) {
                mode = arg;
            }
        }

        Engine engine = new Engine();
        if (!engine.initialize(configPath)) {
            System.err.println("Initialization failed. Check logs.");
            System.exit(1);
        }

        switch (mode) {
            case "live":
                System.out.println("Starting live detection on device " + deviceId + "...");
                engine.runLiveDetection(deviceId);
                break;
            case "image":
                if (inputPath.isEmpty()) {
                    System.err.println("Error: --input path is required for image mode.");
                    System.exit(1);
                }
                engine.processSingleImage(inputPath, outputPath);
                break;
            case "enroll":
                if (name.isEmpty() || crim_id.isEmpty() || crime.isEmpty() || inputPath.isEmpty()) {
                    System.err.println("Error: --name, --id, --crime, and --input are required for enroll mode.");
                    System.exit(1);
                }
                if (engine.enrollCriminal(name, crim_id, crime, inputPath)) {
                    System.out.println("Successfully enrolled: " + name);
                } else {
                    System.err.println("Failed to enroll criminal.");
                    System.exit(1);
                }
                break;
            case "list":
                engine.listCriminals();
                break;
            case "delete":
                if (crim_id.isEmpty()) {
                    System.err.println("Error: --id is required for delete mode.");
                    System.exit(1);
                }
                if (engine.deleteCriminal(crim_id)) {
                    System.out.println("Successfully deleted record ID: " + crim_id);
                } else {
                    System.err.println("Failed to delete record.");
                    System.exit(1);
                }
                break;
            default:
                System.err.println("Unknown mode: " + mode);
                printUsage();
                System.exit(1);
        }
    }
}
