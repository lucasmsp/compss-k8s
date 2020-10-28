# COMPSs connector to Kubernetes cluster

A COMPSs connector to submit tasks in a Kubernetes Cluster.

## Quickstart

This connector uses the COMPSs's official cloud connector interface. Its usage is similar to the official Mesos COMPSs connector. However, an [example](./test) is available as quickstart.

1. Build the `k8s-conn.jar`. A compiled jar is also [available](./k8s-conn/target/). It's built using COMPSs 2.6 and [Kubernetes Java client](https://github.com/kubernetes-client/java) 10.0.0;
2. Copy the *jar* to the COMPSs cloud connector folder (e.g., /opt/COMPSs/Runtime/cloud-conn/);
3. Create a package with the application `tar -czvf test.tar.gz test.py`;
4. Create a *resource.xml* and a *project.xml* similar to the example;
5. Run a COMPSs application
```bash
runcompss -d --summary --lang=python\
          --project=./test_p_k8s.xml\
          --resources=./test_r_k8s.xml\
          --python_interpreter=python3\
          --pythonpath=/tmp/\
          test.py 16 3 10
```

### NOTE: 

 * The built connector has full support to Kubernetes engine 1.18 but the official documentation suggests that is also compatible with 1.13+;
