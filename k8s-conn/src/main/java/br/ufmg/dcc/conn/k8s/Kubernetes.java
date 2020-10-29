package br.ufmg.dcc.conn.k8s;

import br.ufmg.dcc.clients.k8s.framework.KubernetesFramework;
import br.ufmg.dcc.clients.k8s.framework.exceptions.FrameworkException;
import br.ufmg.dcc.clients.k8s.framework.log.Loggers;

import es.bsc.conn.Connector;
import es.bsc.conn.exceptions.ConnException;
import es.bsc.conn.types.HardwareDescription;
import es.bsc.conn.types.SoftwareDescription;
import es.bsc.conn.types.VirtualResource;
import es.bsc.conn.types.StarterCommand;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Implementation of Kubernetes connector
 *
 */
public class Kubernetes extends Connector {

    // Properties' names
    private static final String PROP_PRICE = "price";

    // Conversion constants
    private static final int GIGAS_TO_BYTES = 1000*1000*1000;
    private static final String UNDEFINED_IP = "-1.-1.-1.-1";

    // Logger
    private static final Logger logger = LogManager.getLogger(Loggers.K8S_CONN);

    // K8s Framework Client
    private KubernetesFramework framework;

    // Information about resources
    private final Map<String, VirtualResource> resources;


    /**
     * Initializes the Kubernetes connector with the given properties.
     * A K8S client is started, it will do the communication with K8S.
     *
     * @param props
     * @throws ConnException
     */
    public Kubernetes(Map<String, String> props) throws ConnException {
        super(props);
        logger.info("Starting K8s Connector");
        resources = new HashMap<>();
        try {
            this.framework = new KubernetesFramework(props);

        } catch (FrameworkException e) {
            System.out.println(e.getMessage());
            throw new ConnException(e);
        }

    }

    /**
     * Creates a K8s pod/container.
     *
     * @param   hd Information about cpus, mem and price.
     * @param   sd Information about operating system.
     * @param   prop Properties inherited from Resources.
     * @return  Object k8s pod identifier generated.
     */
    @Override
    public Object create(String requestName, HardwareDescription hd, SoftwareDescription sd, Map<String,
            String> prop, StarterCommand starterCMD) throws ConnException {
        logger.debug("Creating a k8s pod");

        // memory must be a int because rpc xml do not support a long type.
        float memoryGb = (hd.getMemorySize() == -1.0F) ?  hd.getMemorySize() : 0.25F;
        long memoryMb = Math.round(memoryGb * GIGAS_TO_BYTES);
        String newId = null;
        try {
            newId = framework.requestWorker(hd.getImageName(), hd.getTotalCPUComputingUnits(), memoryMb);
        } catch (FrameworkException e) {
            throw new ConnException(e);
        }
        resources.put(newId, new VirtualResource(newId, hd, sd, prop));
        logger.debug("K8s pod created");
        return newId;
    }

    @Override
    public Object[] createMultiple(int replicas, String requestName, HardwareDescription hd, SoftwareDescription sd,
                                   Map<String, String> prop, StarterCommand starterCMD) throws ConnException {
        String[] envIds = new String[replicas];

        for(int i = 0; i < replicas; ++i) {
            envIds[i] = (String) this.create(requestName, hd, sd, prop, starterCMD);
        }

        return envIds;
    }

    /**
     * Waits K8s pod with identifier id to be ready.
     * @param  id K8s pod identifier.
     * @return VirtualResource assigned to that container.
     */
    @Override
    public VirtualResource waitUntilCreation(Object id) throws ConnException {
        logger.debug("Waiting until pod creation");
        String identifier = (String) id;
        if (!resources.containsKey(identifier)) {
            throw new ConnException("This identifier does not exist " + identifier);
        }
        VirtualResource vr = resources.get(identifier);

        String ip = framework.waitWorkerUntilRunning(identifier);
        if (UNDEFINED_IP.equals(ip)) {
            throw new ConnException("Could not wait until creation of worker " + id);
        }
        logger.debug("Pod is created with IP "+ip);
        vr.setIp(ip);
        return vr;
    }

    /**
     * @param  vr Corresponding machine to get the price from.
     * @return Price
     */
    @Override
    public float getPriceSlot(VirtualResource vr) {
        if (vr.getProperties().containsKey(PROP_PRICE)) {
            return Float.parseFloat(vr.getProperties().get(PROP_PRICE));
        }
        return 0.0f;
    }

    /**
     * Kills the K8s pod with identifier id.
     * @param id
     */
    @Override
    public void destroy(Object id) {
        String identifier = (String) id;
        resources.remove(identifier);
        framework.removeWorker(identifier);
    }

    /**
     * Shutdowns the K8s connector.
     */
    @Override
    public void close() {
        logger.debug("Stopping K8s connector");
    }

}
