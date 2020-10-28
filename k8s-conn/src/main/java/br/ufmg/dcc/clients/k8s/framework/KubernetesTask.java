package br.ufmg.dcc.clients.k8s.framework;

import br.ufmg.dcc.clients.k8s.framework.log.Loggers;

import io.kubernetes.client.openapi.models.V1Pod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Implementation of a K8s Task
 *
 */
public class KubernetesTask {

    private static final Logger LOGGER = LogManager.getLogger(Loggers.K8S_CONN);

    // Properties
    private String id;
    private String ip;

    private V1Pod pod;


    /**
     * Represents a Task to execute in Kubernetes.
     *
     * @param id           Identifier.
     */
    public KubernetesTask(String id, V1Pod pod) {
        this.id = id;
        this.pod = pod;
    }

    /**
     * @return KubernetesTask identifier.
     */
    public String getId() {
        return id;
    }

    public V1Pod getPod() {return pod;}

    public void setPod(V1Pod pod) {this.pod = pod;}

    //public String getStatus() {return this.pod.getStatus().;}



    /**
     * @return Docker k8s container IP.
     */
    public String getIp() {
        return ip;
    }

    /**
     * @param ip New IP to assign.
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * @return MesosTask string.
     */
    @Override
    public String toString() {
        return String.format("[Task %s] is running in pod", id);
    }


}
