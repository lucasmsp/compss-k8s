package br.ufmg.dcc.clients.k8s.framework.log;

import es.bsc.conn.exceptions.NonInstantiableException;

/**
 * Loggers' names for Yarn Connector Components
 *
 */


public final class Loggers {

    // K8S Framework
    public static final String K8S_CONN = "br.ufmg.dcc.conn.loggers.Loggers.K8S_CONN";

    private Loggers() {
        throw new NonInstantiableException("Loggers should not be instantiated");
    }

}
