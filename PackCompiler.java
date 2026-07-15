import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackCompiler {
    private static final String BASE_COMPILED_FILE = "package packID;\r\n" + "import net.minecraftforge.fml.common.Mod;\r\n" + "@Mod(\"packID\")\r\n" + "public class ForgePackLoader {public ForgePackLoader() {}}";

    //NeoForge (Minecraft 26.1+) equivalent of the compiled loader file.  Same dummy-mod trick, new annotation package.
    private static final String BASE_NEO_COMPILED_FILE = "package packID;\r\n" + "import net.neoforged.fml.common.Mod;\r\n" + "@Mod(\"packID\")\r\n" + "public class NeoForgePackLoader {public NeoForgePackLoader() {}}";
    //FML version to compile the NeoForge loader class against.  This is the FML that ships with NeoForge 26.1.2.x.
    private static final String NEOFORGE_FML_VERSION = "11.0.15";
    private static final String NEOFORGE_FML_MAVEN_URL = "https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/" + NEOFORGE_FML_VERSION + "/loader-" + NEOFORGE_FML_VERSION + ".jar";
    //FML class files are Java 25 format, so javac needs to be at least that version to read them.
    private static final int NEOFORGE_JAVAC_VERSION = 25;
    private static int killedDSStores = 0;

    public static void main(String[] args) {
        try {
            // Parse mode flags
            boolean build116 = true;  // Build Forge 1.16.5 jar via Gradle
            boolean build112 = true;  // Build legacy assets-only jar for 1.12.2
            boolean buildNeo = false; // Build NeoForge jar for MC 26.1+ (requires a Java 25+ JDK, so not on by default)
            if (args != null && args.length > 0) {
                String mode = String.join(" ", args).toLowerCase();
                // If a specific target is requested, disable the others
                if (mode.contains("116")) {
                    build116 = true;
                    build112 = false;
                } else if (mode.contains("112")) {
                    build116 = false;
                    build112 = true;
                } else if (mode.contains("neo") || mode.contains("26")) {
                    build116 = false;
                    build112 = false;
                    buildNeo = true;
                }
            }

            File currentDir = new File(PackCompiler.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            //Enable for IDE where path isn't where file lies.
            //currentDir = new File("D:\\MinecraftDev\\code_ocp");
            boolean onWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

            //Make sure the user has the java compiler installed.
            String result = runCommand("javac -version");
            if (!result.startsWith("javac")) {
                System.out.println(result);
                System.out.println("No Java compiler found.  Please install a Java JDK compiler.");
                return;
            } else {
                System.out.println("Found Java compipler, looking for packs.");
            }

            //Kill off any DS_Store files as they'll foul Forge.
            killDSStores(new File(currentDir, "src/main"));
            if (killedDSStores > 0) {
                System.out.println("Compile-inator Packing Systems V2.0 found " + killedDSStores + " DS_Stores in your files.  These have been sent to the shadow realm.");
            }

            //Remove any old packs only if we're about to build the 1.16.5 jar
            //so that a subsequent 1.12.2-only run doesn't delete prior outputs.
            File libsDir = new File(currentDir, "build/libs");
            if (build116 && libsDir.exists()) {
                for (File file : libsDir.listFiles()) {
                    file.delete();
                }
            }

            //Make sure ForgePackLoader.java is proper to all included mods.
            Set<String> packIDs = new HashSet<>();
            File packAssetRootDir = new File(currentDir, "src/main/resources/assets");
            for (File file : packAssetRootDir.listFiles()) {
                if (file.isDirectory()) {
                    String packID = file.getName();
                    File packCompiledFileDir = new File(currentDir, "src/main/java/" + packID);
                    File packCompiledFile = new File(packCompiledFileDir, "ForgePackLoader.java");
                    if (packCompiledFile.exists()) {
                        packIDs.add(packID);
                        System.out.println("Found pack with ID: " + packID);
                        String data = BASE_COMPILED_FILE.replace("packID", packID);
                        BufferedWriter fileOutput = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(packCompiledFile)));
                        fileOutput.write(data);
                        fileOutput.close();
                    }
                }
            }

            //Check for pngs in the texture folder and make any item JSON models as required.
            for (String packID : packIDs) {
                Set<String> requiredJSONs = new HashSet<>();
                File packAssetItemJSONDir = new File(packAssetRootDir, "mts/models/item");
                File packAssetItemPNGDir = new File(packAssetRootDir, packID + "/textures/item");
                if (packAssetItemPNGDir.exists()) {
                    for (File pngFile : packAssetItemPNGDir.listFiles()) {
                        String rawFileName = pngFile.getName().substring(0, pngFile.getName().lastIndexOf("."));
                        File jsonFile = new File(packAssetItemJSONDir, packID + "." + rawFileName + ".json");
                        requiredJSONs.add(jsonFile.getName());
                        if (!jsonFile.exists()) {
                            jsonFile.getParentFile().mkdirs();
                            String createdJSON = "{\"parent\":\"mts:item/basic\",\"textures\":{\"layer0\": \"" + packID + ":item/" + rawFileName + "\"}}";
                            BufferedWriter fileOutput = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jsonFile)));
                            fileOutput.write(createdJSON);
                            fileOutput.close();
                            System.out.println("Auto-created missing item JSON for " + packID + ":" + rawFileName);
                        }
                    }
                    for (File jsonFile : packAssetItemJSONDir.listFiles()) {
                        if (!requiredJSONs.contains(jsonFile.getName())) {
                            jsonFile.delete();
                            System.out.println("Removed un-needed JSON file " + packID + ":" + jsonFile.getName());
                        }
                    }
                }
            }

            // Determine naming components
            String packVersion = readGradleString(new File(currentDir, "build.gradle"), "version");
            if (packVersion == null || packVersion.isEmpty()) {
                packVersion = "unknown";
            }
            String baseName = readGradleString(new File(currentDir, "build.gradle"), "archivesBaseName");
            if (baseName == null || baseName.isEmpty()) {
                baseName = "MTS Official Pack";
            }

            if (build116) {
                System.out.println("All files checked, compiling 1.16.5.");
                //Run the wrapper through cmd with an explicit .\ prefix on Windows: modern Java versions
                //and systems with NoDefaultCurrentDirectoryInExePath set no longer resolve bare .bat
                //names against the working directory, so a direct call can't find the wrapper.
                result = runCommand(onWindows ? "cmd /c .\\gradlew.bat build" : "./gradlew build");

                //Re-name compiled file, we know there will only be one, but not what it's called.
                if (libsDir.exists()) {
                    File[] files = libsDir.listFiles();
                    if (files != null) {
                        File target = null;
                        for (File file : files) {
                            String name = file.getName().toLowerCase();
                            if (name.endsWith(".jar") && !name.contains("-sources")) {
                                target = file;
                                break;
                            }
                        }
                        if (target != null) {
                            File newName = new File(libsDir, baseName + "-1.16.5-" + packVersion + ".jar");
                            target.renameTo(newName);
                            System.out.println("Named 1.16.5 jar: " + newName.getName());
                        } else {
                            //No jar means the Gradle build failed, so show its output to say why.
                            System.out.println(result);
                            System.out.println("The 1.16.5 Gradle build did not produce a jar, see output above.");
                        }
                    }
                }
            }

            if (build112) {
                System.out.println("Creating 1.12.2 assets pack jar.");
                if (!libsDir.exists()) {
                    libsDir.mkdirs();
                }
                ZipOutputStream pack = new ZipOutputStream(new FileOutputStream(new File(libsDir, baseName + "-1.12.2-" + packVersion + ".jar")));
                addToZip(packAssetRootDir, pack, packAssetRootDir.getAbsolutePath().length() - "assets".length());
                pack.close();
            }

            if (buildNeo) {
                System.out.println("Creating NeoForge (Minecraft 26.1+) pack jar.");
                if (packIDs.isEmpty()) {
                    System.out.println("No packs found to compile, aborting NeoForge build.");
                    return;
                }

                //The dummy @Mod class has to be compiled against the real NeoForge FML jar, and that
                //jar's class files are Java 25 format, so javac must be at least that version to read them.
                String javacCommand = findJavacCommand();
                int javacVersion = getJavacMajorVersion(javacCommand);
                if (javacVersion < NEOFORGE_JAVAC_VERSION) {
                    System.out.println("The NeoForge build needs a JDK " + NEOFORGE_JAVAC_VERSION + " or newer compiler, but " + (javacVersion > 0 ? "only found version " + javacVersion : "none was found") + ".");
                    System.out.println("Install a Java " + NEOFORGE_JAVAC_VERSION + "+ JDK and put it first on your PATH (or point JAVA_HOME at it), then re-run this script.");
                    return;
                }

                //Get the FML jar to compile against.  Download and cache it on first use.
                File fmlJar = new File(currentDir, "build/neoforge/loader-" + NEOFORGE_FML_VERSION + ".jar");
                if (!fmlJar.exists()) {
                    System.out.println("Downloading NeoForge FML " + NEOFORGE_FML_VERSION + " to compile against.");
                    downloadFile(NEOFORGE_FML_MAVEN_URL, fmlJar);
                }

                //Write out and compile the NeoForge loader class for every pack.
                //These go to the build folder rather than src as they aren't part of the Forge source set.
                File neoSourceDir = new File(currentDir, "build/neoforge/java");
                File neoClassesDir = new File(currentDir, "build/neoforge/classes");
                List<String> javacArguments = new ArrayList<>();
                javacArguments.add(javacCommand);
                javacArguments.add("--release");
                javacArguments.add("8");
                javacArguments.add("-Xlint:-options");
                javacArguments.add("-cp");
                javacArguments.add(fmlJar.getAbsolutePath());
                javacArguments.add("-d");
                javacArguments.add(neoClassesDir.getAbsolutePath());
                for (String packID : packIDs) {
                    File neoSourceFile = new File(neoSourceDir, packID + "/NeoForgePackLoader.java");
                    neoSourceFile.getParentFile().mkdirs();
                    String data = BASE_NEO_COMPILED_FILE.replace("packID", packID);
                    BufferedWriter fileOutput = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(neoSourceFile)));
                    fileOutput.write(data);
                    fileOutput.close();
                    javacArguments.add(neoSourceFile.getAbsolutePath());
                }
                String javacOutput = runProcess(javacArguments);
                if (!javacOutput.trim().isEmpty()) {
                    System.out.println(javacOutput);
                }
                for (String packID : packIDs) {
                    if (!new File(neoClassesDir, packID + "/NeoForgePackLoader.class").exists()) {
                        System.out.println("Compilation of the NeoForge loader class for pack " + packID + " failed, aborting NeoForge build.");
                        return;
                    }
                }

                //Now assemble the jar: manifest, NeoForge metadata, shared assets/data, and the compiled loader classes.
                if (!libsDir.exists()) {
                    libsDir.mkdirs();
                }
                //Strip any leading non-digits from the version, as FML requires versions to start with a number.
                String neoPackVersion = packVersion.replaceFirst("^[^0-9]+", "");
                if (neoPackVersion.isEmpty()) {
                    neoPackVersion = "0";
                }
                File neoJarFile = new File(libsDir, baseName + "-neoforge-" + packVersion + ".jar");
                ZipOutputStream pack = new ZipOutputStream(new FileOutputStream(neoJarFile));
                //A stable module name stops JPMS from deriving one from the spaced jar file name.
                String moduleID = new TreeSet<>(packIDs).first();
                addBytesToZip(("Manifest-Version: 1.0\r\nAutomatic-Module-Name: " + moduleID + "\r\n\r\n").getBytes(StandardCharsets.UTF_8), "META-INF/MANIFEST.MF", pack);
                String neoModsToml = new String(Files.readAllBytes(new File(currentDir, "neoforge/neoforge.mods.toml").toPath()), StandardCharsets.UTF_8);
                addBytesToZip(neoModsToml.replace("${packVersion}", neoPackVersion).getBytes(StandardCharsets.UTF_8), "META-INF/neoforge.mods.toml", pack);
                addBytesToZip(Files.readAllBytes(new File(currentDir, "neoforge/pack.mcmeta").toPath()), "pack.mcmeta", pack);
                addBytesToZip(Files.readAllBytes(new File(currentDir, "src/main/resources/vingette.png").toPath()), "vingette.png", pack);
                addToZip(packAssetRootDir, pack, packAssetRootDir.getAbsolutePath().length() - "assets".length());
                File packDataRootDir = new File(currentDir, "src/main/resources/data");
                if (packDataRootDir.exists()) {
                    addToZip(packDataRootDir, pack, packDataRootDir.getAbsolutePath().length() - "data".length());
                }
                for (String packID : packIDs) {
                    addBytesToZip(Files.readAllBytes(new File(neoClassesDir, packID + "/NeoForgePackLoader.class").toPath()), packID + "/NeoForgePackLoader.class", pack);
                }
                pack.close();
                System.out.println("Named NeoForge jar: " + neoJarFile.getName());
            }

            System.out.println("Your pack is located in " + libsDir.getAbsolutePath().toString());
            System.out.println("If you haven't already, please edit the mods.toml file, located in " + (new File(currentDir, "src/main/resources/META-INF").getAbsolutePath()));
            System.out.println("For NeoForge builds the equivalent file is neoforge.mods.toml, located in " + (new File(currentDir, "neoforge").getAbsolutePath()));
            System.out.println("This parser cannot edit or know all the various things in those files, so you must edit them before running this script.");
        } catch (Exception e) {
            System.out.println("Build failed!");
            e.printStackTrace();
        }
    }

    private static String readGradleString(File buildGradle, String key) {
        try {
            if (!buildGradle.exists()) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(buildGradle), "UTF-8"));
            String line;
            Pattern p = Pattern.compile("^" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"");
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line.trim());
                if (m.find()) {
                    reader.close();
                    return m.group(1);
                }
            }
            reader.close();
        } catch (Exception ignored) {}
        return null;
    }

    private static void killDSStores(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                killDSStores(file);
            } else if (file.getName().contains("DS_Store")) {
                file.delete();
                ++killedDSStores;
            }
        }
    }

    private static String runCommand(String command) throws Exception {
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));
            String result = "";
            while (output.ready()) {
                result += output.readLine() + "\n";
            }
            while (error.ready()) {
                result += error.readLine() + "\n";
            }
            return result;
        } catch (Exception e) {
            System.out.println("Error running command: " + command);
            throw e;
        }
    }

    /**Runs a command given as an argument list rather than a single string.  Unlike
     * {@link #runCommand(String)} this handles paths with spaces in them, and it
     * waits for all output rather than only what is buffered when the process exits.*/
    private static String runProcess(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = output.readLine()) != null) {
            result.append(line).append("\n");
        }
        process.waitFor();
        return result.toString();
    }

    /**Returns the javac to use: the one in JAVA_HOME if that's set and has one, otherwise the one on the PATH.*/
    private static String findJavacCommand() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            boolean onWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            File javacFile = new File(javaHome, onWindows ? "bin/javac.exe" : "bin/javac");
            if (javacFile.exists()) {
                return javacFile.getAbsolutePath();
            }
        }
        return "javac";
    }

    /**Returns the major version of the passed-in javac, or -1 if it couldn't be run.
     * Handles both old (javac 1.8.0_402) and new (javac 25.0.3) version formats.*/
    private static int getJavacMajorVersion(String javacCommand) {
        try {
            List<String> command = new ArrayList<>();
            command.add(javacCommand);
            command.add("-version");
            String result = runProcess(command).trim();
            Matcher matcher = Pattern.compile("javac\\s+(\\d+)(?:\\.(\\d+))?").matcher(result);
            if (matcher.find()) {
                int major = Integer.parseInt(matcher.group(1));
                if (major == 1 && matcher.group(2) != null) {
                    return Integer.parseInt(matcher.group(2));
                }
                return major;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static void downloadFile(String url, File destinationFile) throws Exception {
        destinationFile.getParentFile().mkdirs();
        //Download to a temp file first so an interrupted download can't leave a
        //partial file behind that later runs would mistake for the real thing.
        File tempFile = new File(destinationFile.getParentFile(), destinationFile.getName() + ".part");
        InputStream stream = new URL(url).openStream();
        try {
            Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            stream.close();
        }
        Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void addBytesToZip(byte[] bytes, String entryName, ZipOutputStream pack) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        pack.putNextEntry(entry);
        pack.write(bytes);
        pack.closeEntry();
    }

    private static void addToZip(File directory, ZipOutputStream pack, int directoryLength) throws Exception {
        for (File file : directory.listFiles()) {
            try {
                if (file.isDirectory()) {
                    addToZip(file, pack, directoryLength);
                } else {
                    //Need to replace \ with / since parser expects URI format, not Windows.
                    ZipEntry entry = new ZipEntry(file.getAbsolutePath().substring(directoryLength).replace('\\', '/'));
                    pack.putNextEntry(entry);
                    FileInputStream stream = new FileInputStream(file);
                    byte[] bytes = new byte[1024];
                    int bytesRead;
                    while((bytesRead = stream.read(bytes)) > 0) {
                        pack.write(bytes, 0, bytesRead);
                    }
                    pack.closeEntry();
                }
            }catch (Exception e) {
                System.out.println("Error zipping file: " + file);
                throw e;
            }
        }
    }
}
