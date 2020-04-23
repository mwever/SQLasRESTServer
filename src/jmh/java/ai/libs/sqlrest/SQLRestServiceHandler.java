package ai.libs.sqlrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public enum SQLRestServiceHandler {

    INSTANCE(new File("benchmark-workingdir"));

    private final static Logger logger = LoggerFactory.getLogger(SQLRestServiceHandler.class);

    private File workingDir;

    private ProcessBuilder builder;

    private Process process;

    private List<String> command;

    public static final String GRADLE_PROP_BOOT_RUN_WD = "bootRun.workingDir";

    private Properties applicationProperties = new Properties();

    {
        applicationProperties.put("ai.libs.sqlrest.benchmark.BenchmarkMode", "true");
    }

    private Properties serverProperties = new Properties();

    {
        serverProperties.put("server.tomcat.max-threads", "10000");
    }

    SQLRestServiceHandler() {
        this(new File(System.getProperty("user.dir")));
    }

    SQLRestServiceHandler(File workingDir) {
        this.workingDir = workingDir;
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            if(process != null && process.isAlive()) {
                stopService();
            }
        }));
    }

    private boolean isWindows() {
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows");
        return isWindows;
    }

    private void createCommand() {
        String gradleScript =  isWindows()  ? "gradlew.bat" : "./gradlew";
        String taskName = "bootRun";
        String gradleProp = String.format("-P%s=%s", GRADLE_PROP_BOOT_RUN_WD, workingDir.getPath());

        this.command = new ArrayList<>();
        command.add(gradleScript);
        command.add(taskName);
//        command.add("--no-daemon");
        command.add(gradleProp);
    }

    private List<String> getCommand() {
        if(command == null) {
            createCommand();
        }
        return command;
    }

    private void createProcessBuilder() {
        File out = new File(workingDir, "out.txt");
        if(out.exists()) {
            out.delete();
        } else if(!workingDir.exists()) {
            boolean created = workingDir.mkdirs();
            if (!created || !workingDir.isDirectory()) {
                throw new RuntimeException("Cannot create the working directory: " + workingDir);
            }
        }
        logger.info("Starting service: {} > {}", String.join(" ", getCommand()), out);
        this.builder = new ProcessBuilder(getCommand());
        builder.redirectError(out);
        builder.redirectOutput(out);
    }

    private boolean isPortInUse() {
        boolean result;
        try {

            Socket s = new Socket("localhost", 8080);
            s.close();
            result = true;
        }
        catch(Exception e) {
            result = false;
        }
        return(result);
    }

    public Properties getApplicationProperties() {
        return applicationProperties;
    }

    public Properties getServerProperties() {
        return serverProperties;
    }

    public void setTomcatMaxThreads(int maxThreads) {
        applicationProperties.put("server.tomcat.max-threads", String.valueOf(maxThreads));
    }

    public void setNumAdapters(int numAdapters) {
        if(numAdapters < 1 ) {
            throw new IllegalArgumentException("Number of adapters must be positive. Given: " + numAdapters);
        }
        if(process != null && process.isAlive()) {
            throw new IllegalStateException("Cannot change property after startup.");
        }
        serverProperties.put(IServerConfig.K_NUM_ADAPTER_INSTANCES, String.valueOf(numAdapters));
    }

    public void setAccessLimit(int accessLimit) {
        if(accessLimit < 0)
        {
            throw new IllegalArgumentException("Access limit must be positive: " + accessLimit);
        }
        if(accessLimit == 0) {

            serverProperties.put(IServerConfig.K_ADAPTER_ACCESS_RANDOM, String.valueOf(false));
            serverProperties.put(IServerConfig.K_ADAPTER_LIMIT_ACCESS, String.valueOf(false));
            serverProperties.put(IServerConfig.K_NUM_ADAPTER_ACCESS_LIMIT, String.valueOf(accessLimit));
        } else {
            serverProperties.put(IServerConfig.K_ADAPTER_ACCESS_RANDOM, String.valueOf(false));
            serverProperties.put(IServerConfig.K_ADAPTER_LIMIT_ACCESS, String.valueOf(true));
            serverProperties.put(IServerConfig.K_NUM_ADAPTER_ACCESS_LIMIT, String.valueOf(accessLimit));
        }
    }

    private void loadExistingProperties(File propFile, Properties target) {
        Properties alreadyExistingProps = new Properties();
        try(InputStream propFileIn = new FileInputStream(propFile)) {
            alreadyExistingProps.load(propFileIn);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read properties from : " + propFile, e);
        }
        alreadyExistingProps.forEach(target::putIfAbsent);
    }

    private void saveProperties(File propFile, Properties source) {
        try(OutputStream propFileOut = new FileOutputStream(propFile)) {
            source.store(propFileOut, null);
        } catch(IOException e) {
            throw new RuntimeException("Couldn't write properties to: " + propFile, e);
        }
    }

    private void writeAppProperties() {
        File appPropFile = new File(workingDir, "application.properties");
        if(appPropFile.exists()) {
            if(appPropFile.isDirectory()) {
                throw new IllegalArgumentException(String.format("%s is a directory?", appPropFile));
            }
//            loadExistingProperties(appPropFile, applicationProperties);
            appPropFile.delete();
        }
        saveProperties(appPropFile, applicationProperties);
    }

    private void writeServerProperties() {
        File serverPropFile = new File(workingDir, "conf/server.properties");
        if(!serverPropFile.getParentFile().isDirectory()) {
            boolean success = serverPropFile.getParentFile().mkdirs();
            if(!success) {
                throw new RuntimeException("Couldn't create the directory for: " + serverPropFile);
            }
        }
        if(serverPropFile.exists()) {
            serverPropFile.delete();
        }
        File oldServerProperties = new File("conf/server.properties");
        if(oldServerProperties.exists()) {
            loadExistingProperties(oldServerProperties, serverProperties);
        }
        saveProperties(serverPropFile, serverProperties);
    }

    public void startService() {
        if(isPortInUse()) {
//            throw new IllegalStateException("There is already a service running on port 8080.");
            logger.warn("There is already a service running on port 8080. Killing it first..");
            stopService();
            if(isPortInUse()) {
                logger.error("There is still a service running on port 8080. Exiting..");
                System.exit(1);
            }
        }
        createProcessBuilder();
        writeAppProperties();
        writeServerProperties();
        logger.info("Wrote property files.");
        try {
            process = builder.start();
            logger.info("Service started: {}", processName());
        } catch (IOException e) {
            throw new RuntimeException("Couldn't start process: " + getCommand());
        }
        logger.debug("Waiting 2 seconds for gradle to start the service.");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void waitForServiceAvailability(WebClient webClient) {
        int tries = 0;
        int maxTries = 50;
        while(tries < maxTries) {
            if(process != null && !process.isAlive()) {
                throw new IllegalStateException("Process is not alive: " + processName());
            }
            WebClient.RequestHeadersUriSpec<?> getRequest = webClient.get();
            getRequest.uri("isAlive");
            logger.debug("Waiting until service is available.");
            try {
                ResponseEntity<Boolean> response = getRequest.retrieve().toEntity(Boolean.class).block();
                if (response == null || response.getStatusCode().isError()) {
                    throw new IllegalStateException("Service cannot be reached.");
                }
                Boolean isAlive = response.getBody();
                if (isAlive == null || !isAlive) {
                    throw new RuntimeException("Service didn't return `true` on isAlive request.");
                }
                logger.info("Service is ready: {}", processName());
                break;
            } catch(Exception ex) {
                logger.debug("Couldn't connect to running service;");
                if(tries >= maxTries-1) {
                    throw new RuntimeException("Couldn't connect to service although it is still running..", ex);
                }
            }
            try {
                tries ++;
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String processName() {
        if(process == null) {
            return "[Process: Not started]";
        } else if(process.isAlive()) {
            return "[Process: Running]";
        } else {
            return String.format("[Process: Terminated, %d]", process.exitValue());
        }
    }

    private void killOtherService() {
        if(!isPortInUse()) {
            throw new IllegalStateException("Cannot kill other service because there are none listening to 8080.");
        }
        if(isWindows()) {
            throw new RuntimeException("No implementation for windows kill command present.");
        }
        try {
            Process getPidOtherService = Runtime.getRuntime().exec("lsof -t -i :8080");
            getPidOtherService.waitFor();
            if(getPidOtherService.exitValue() != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(getPidOtherService.getErrorStream()));
                String errLines = reader.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException("Couldn't get pid of the other service: " + errLines);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(getPidOtherService.getInputStream()));
            String otherProcessIDs = reader.lines().collect(Collectors.joining(" "));
            logger.info("Killing other processes with id: " + otherProcessIDs);
            Process killOtherService = Runtime.getRuntime().exec("kill " + otherProcessIDs);
            killOtherService.waitFor();
            if(killOtherService.exitValue() != 0) {
                BufferedReader errReader = new BufferedReader(new InputStreamReader(killOtherService.getErrorStream()));
                String errLines = errReader.lines().collect(Collectors.joining("\n"));
                BufferedReader outReader = new BufferedReader(new InputStreamReader(killOtherService.getInputStream()));
                String outLines = outReader.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException(String.format("Cannot destroy other process listening on port .\nOut: %s\nErr: %s", outLines, errLines));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error trying to destroy other process listening on port 8080.", e);
        }
    }

    public void stopService() {
        if(process == null) {
//            throw new IllegalStateException("Process hasn't started yet. cannot be stopped.");
            try {
                killOtherService();
            } catch(Exception ex) {
                logger.error("Couldn't kill other service running on port 8080.", ex);
                System.exit(1);
            }
        }
        if(!process.isAlive()) {
            logger.warn("Process {} was already done.", processName());
            return;
        }
        process.destroy();
        if(!process.isAlive()) {
            return;
        }
        int tries = 0;
        try {
            while (tries < 3 && process.isAlive()) {
                Thread.sleep(1000);
                if (!process.isAlive()) {
                    break;
                }
                logger.warn("Process {} couldn't be destroyed. Executing `destroyForcibly`. Try number: {}", processName(), tries);
                process.destroyForcibly();
                tries++;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Process shutdown process has been interrupted. \nProcess may be alive: " + process.toString(), e);
        }
        if(process.isAlive()) {
            throw new IllegalArgumentException("Couldn't destroy process: " + process.toString());
        }
        logger.info("Process {} was destroyed.", processName());
    }

}
