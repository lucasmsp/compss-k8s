# Application test

This dummy test application is a produce/consumer case where the consumer must sleep for a value of time passed by a parameter in each *n* task. The producer generates a list of time to wait based on each partition based on a random number between *min_time* and *max_time*, also passed as parameter. Example to run in a default COMPSs environment:

```bash
runcompss --python_interpreter=python3 test.py <num_fragments> <min_time> <max_time>
```


## Project and resources files

In order to submit a COMPSs application in Kubernetes, first, you need to prepare *XML* project and resources files. 

On the project file, update the `test_p_k8s.xml` by specifying:

* The minimum and maximum containers to be created by COMPSs; 
* The *k8s-namespace* with the namespace in K8s to be used by containers (default is `default`); 
* The *image name* to be used by COMPSs, one can use more than one image;
* The package with the application, this package will be sent to all containers based on the source and target folder specifications.

On the resource file, update the `test_r_k8s.xml` by specifying:

* The *ConnectorJar* to `k8s-conn.jar`;
* The *ConnectorClass* to `br.ufmg.dcc.conn.k8s.Kubernetes`;
* The *Endpoint Server* of the Kubernetes Cluster, which must have a format of `<master_compss_node_ip>`;
* All *Image Name* to be used by COMPSs;
* The list of possible instances resources (Processor, Memory, etc). Currently, storage size is not take in count.


## Quickstart

Submit a COMPSs application in a Kubernetes Cluster is similar to the official Mesos's COMPSs connector:

1. Create a package `tar.gz` with `tar -czvf test.tar.gz test.py`;
2. Copy the package and the application to the folder specified in the COMPSs project *XML* file the you created;
3. To simplify, copy the `k8s-conn.jar` to the cloud connector COMPSs folder, the default location is `/opt/COMPSs/Runtime/cloud-conn/`;
4. Submit with `runcompss` command:

```bash
runcompss --summary\
          --lang=python\
		  --project=./test_p_k8s.xml\
		  --resources=./test_r_k8s.xml\
	      --python_interpreter=python3\
          --pythonpath=/tmp/\
		  test.py 16 5 20
```
