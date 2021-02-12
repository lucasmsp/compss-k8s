package br.ufmg.dcc.clients.k8s.framework;

import br.ufmg.dcc.clients.k8s.framework.exceptions.FrameworkException;
import br.ufmg.dcc.clients.k8s.framework.log.Loggers;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Representation of a K8s Framework client
 *
 */
public class KubernetesFramework {

    private static final String SERVER_IP = "Server";

    private static final String K8S_NAMESPACE = "k8s-namespace";
    private static final String K8S_NAMESPACE_DEFAULT = "default";

    private static final String K8S_APPLICATION_NAME = "k8s-application-name";
    private static final String K8S_APPLICATION_NAME_DEFAULT = "compss";
    public static final Pattern VALID_POD_NAME_REGEX =
            Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*",
                    Pattern.CASE_INSENSITIVE);

    private static final String K8S_APPLICATION_TIMEOUT = "k8s-application-timeout";
    private static final String K8S_APPLICATION_TIMEOUT_DEFAULT = "7200"; // 2 hours

    private static final String VM_USER = "vm-user";
    private static final String VM_USER_DEFAULT = "root";
    private static final String KEYPAIR_NAME = "vm-keypair-name";
    private static final String KEYPAIR_NAME_DEFAULT = "id_rsa";
    private static final String KEYPAIR_LOCATION = "vm-keypair-location";
    private static final String KEYPAIR_LOCATION_DEFAULT = "/home/" + System.getProperty("user.name") + "/.ssh";

    private static final Logger LOGGER = LogManager.getLogger(Loggers.K8S_CONN);

    private static final String UNDEFINED_IP = "-1.-1.-1.-1";

    private CoreV1Api api;

    private final AtomicInteger taskIdGenerator = new AtomicInteger();

    private final String applicationName;
    private final String namespace;
    private final int applicationTimeout;
    private final String publicKey;
    private final String masterHostname;
    private final String masterIp;
    private final String defaultUser;


    /**
     * Creates a new K8sFramework client with the given properties
     *
     * @param props
     * @throws FrameworkException
     */
    public KubernetesFramework(Map<String, String> props) throws FrameworkException {

        if (props.containsKey(SERVER_IP)){
            masterIp = props.get(SERVER_IP);
        }else{
            throw new FrameworkException("Missing COMPSs master IP");
        }

        this.applicationTimeout = Integer.parseInt(
                props.getOrDefault(K8S_APPLICATION_TIMEOUT, K8S_APPLICATION_TIMEOUT_DEFAULT));

        this.namespace = props.getOrDefault(K8S_NAMESPACE, K8S_NAMESPACE_DEFAULT);
        LOGGER.info("Setting namespace: " + namespace);

        this.applicationName = props.getOrDefault(K8S_APPLICATION_NAME, K8S_APPLICATION_NAME_DEFAULT).toLowerCase();
        final Matcher matcher = VALID_POD_NAME_REGEX.matcher(applicationName);
        if (!matcher.matches()) {
            throw new FrameworkException("Kubernetes only admits lower case and numbers to Pod names.");
        }
        LOGGER.info("Setting applicationName: " + applicationName);

        this.defaultUser = props.getOrDefault(VM_USER, VM_USER_DEFAULT);
        LOGGER.info("Setting docker image user: " + this.defaultUser);

        this.publicKey = getPublicKey(props);
        try {
            this.masterHostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.error("ERROR - We could not determine the hostname address.");
            throw new FrameworkException(e.getMessage());
        }

        ApiClient client = null;
        try {
            client = Config.defaultClient();
        } catch (IOException | Error e) {
            LOGGER.error("ERROR - We could not find the kubernetes server.");
            throw new FrameworkException(e.getMessage());
        }

        Configuration.setDefaultApiClient(client);
        api = new CoreV1Api();

        V1PodList list = null;
        try {
            list = api.listNamespacedPod(namespace,null, null, null,
                    null, null, null, null, null, null);
        } catch (ApiException e) {
            LOGGER.error("ERROR - We could not find the namespace "+namespace);
            throw new FrameworkException(e.getMessage());
        }
        String pods_list = "";
        for (V1Pod item : list.getItems()) {
            pods_list += item.getMetadata().getName()+ " ";
        }
        LOGGER.debug("List of pods in same namespace: "+pods_list);

        LOGGER.info("Kubernetes connector started");

    }

    public String getPublicKey(Map<String, String> props) throws FrameworkException {

        String keyPairName = props.getOrDefault(KEYPAIR_NAME, KEYPAIR_NAME_DEFAULT);
        String keyPairLocation = props.getOrDefault(KEYPAIR_LOCATION, KEYPAIR_LOCATION_DEFAULT);

        String checkedKeyPairLocation = (keyPairLocation.equals("~/.ssh")) ? KEYPAIR_LOCATION_DEFAULT: keyPairLocation;
        String filepath = checkedKeyPairLocation + File.separator + keyPairName + ".pub";
        LOGGER.debug("Copying " + filepath + " to be used by Docker Containers.");

        try {
            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, StandardCharsets.UTF_8).replace("\n", "");

        } catch (IOException e) {
            throw new FrameworkException("Public key not found in " + filepath);
        }

    }

    /**
     * Request a worker to be run on a K8s pod.
     *
     * @return Identifier assigned to new worker
     */
    public String requestWorker(String imageName, int cpus, long memory) throws FrameworkException {

        LOGGER.info("Requested a worker");
        String workerId = generateWorkerId(applicationName);
        String keyPath = "/root/.ssh/authorized_keys";
        if (!this.defaultUser.equals("root"))
            keyPath = "/home/"+this.defaultUser+"/.ssh/authorized_keys";

        V1ResourceRequirements resourceRequirements = new V1ResourceRequirements();
        final Map<String, Quantity> requests = new HashMap<>();
        requests.put("memory", Quantity.fromString(Long.toString(memory)));
        requests.put("cpu", Quantity.fromString(Integer.toString(cpus)));
        resourceRequirements.setRequests(requests);

        V1Pod pod = new V1Pod();
        pod.setApiVersion("v1");
        pod.setKind("Pod");

        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(workerId);
        pod.setMetadata(metadata);

        V1PodSpec spec = new V1PodSpec();
        spec.setContainers(new ArrayList<>());
        V1Container container = new V1Container();
        container.setName(workerId);
        container.setImage(imageName);

        List<V1EnvVar> envVars = new ArrayList<>();

        V1EnvVar env1 = new V1EnvVar();
        env1.setName("HOST_KEY");
        env1.setValue(this.publicKey);
        envVars.add(env1);
        LOGGER.debug("Setting HOST_KEY as "+this.publicKey);

        V1EnvVar env2 = new V1EnvVar();
        env2.setName("MASTER_NODE");
        env2.setValue(masterIp + " "+ masterHostname);
        envVars.add(env2);
        LOGGER.debug("Setting $MASTER_NODE as "+masterIp + " "+ masterHostname);

        container.setEnv(envVars);

        container.addCommandItem("/bin/bash")
                .addCommandItem("-c")
                .addCommandItem("echo $HOST_KEY >> "+keyPath+"; " +
                        "echo $MASTER_NODE >> /etc/hosts; " +
                        "service ssh restart; " +
                        "sleep "+this.applicationTimeout);
        container.setImagePullPolicy("IfNotPresent");
        container.resources(resourceRequirements);

        spec.getContainers().add(container);
        pod.setSpec(spec);

        try {
            api.createNamespacedPod(namespace, pod, null, null, null);
        } catch (ApiException e) {
            throw new FrameworkException("Error while creating a pod :" + e.getResponseBody());
        }

        LOGGER.info("Worker created as a K8s pod " + workerId);
        return workerId;
    }

    /**
     * @param appName Application name
     * @return Unique identifier for a worker.
     */
    public synchronized String generateWorkerId(String appName) {
        return appName.replace(" ", "-") + "-" + taskIdGenerator.incrementAndGet();
    }


    /**
     * Wait for worker with identifier id.
     *
     * @param id Worker identifier.
     * @return Worker IP address.
     */
    public String waitWorkerUntilRunning(String id)  {
        LOGGER.info("Waiting worker with id " + id);
        String ip = UNDEFINED_IP;

        boolean pending = true;
        while (pending) {
            try {
                V1Pod pod = api.readNamespacedPodStatus(id, namespace, null);
                if (pod.getMetadata().getName().equals(id)) {
                    ip = pod.getStatus().getPodIP();
                    if (ip != null) {
                        pending = false;
                    }
                }
            } catch (ApiException e) {
            }

        }



        return ip;
    }

    /**
     * Stop worker running/staging in K8s pod.
     *
     * @param id Worker identifier
     */
    public void removeWorker(String id)  {
        LOGGER.debug("Remove worker with id " + id);

        try {
            api.deleteNamespacedPod(id, namespace, null, null, null,
                    true, null, null);
        } catch (ApiException e) {
        }

        LOGGER.debug("Pod " + id + " is marked as Terminating.");
    }


}
