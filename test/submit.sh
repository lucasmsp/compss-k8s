#!/bin/bash

# create the package source
tar -czvf test.tar.gz test.py 
cp ./test.py /tmp/
mv ./test.tar.gz /tmp/

# copy `k8s-conn.jar` to cloud connector COMPSs folder `/opt/COMPSs/Runtime/cloud-conn/`
cp ../k8s-conn/target/k8s-conn.jar /opt/COMPSs/Runtime/cloud-conn/
chmod 755 /opt/COMPSs/Runtime/cloud-conn/k8s-conn.jar

runcompss  --summary -d  --lang=python\
		 --project=./test_p_k8s.xml\
		 --resources=./test_r_k8s.xml\
	     --python_interpreter=python3\
         --pythonpath=/tmp/\
		 test.py 12 3 10


