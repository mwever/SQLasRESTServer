package ai.libs.sqlrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.net.Socket;
import java.util.*;

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

    private void createCommand() {
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows");

        String gradleScript = isWindows ? "gradlew.bat" : "./gradlew";
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
        applicationProperties.put("server.tomcat.max-threads", maxThreads);
    }

    public void setNumAdapters(int numAdapters) {
        if(numAdapters < 1 ) {
            throw new IllegalArgumentException("Number of adapters must be positive. Given: " + numAdapters);
        }
        serverProperties.put(IServerConfig.K_NUM_ADAPTER_INSTANCES, numAdapters);
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
            throw new IllegalStateException("There is already a service running on port 8080.");
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
            if(!process.isAlive()) {
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

    public void stopService() {
        if(process == null) {
            throw new IllegalStateException("Process hasn't started yet. cannot be stopped.");
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
