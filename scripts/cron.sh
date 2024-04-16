#!/bin/bash

export TZ='Asia/Kolkata'

printf "Login to the cluster "
/usr/local/bin/oc login -u kubeadmin -p PASSWORD

printf "Output of oc version at " && date
/usr/local/bin/oc version

printf "Output of oc get nodes on bastion at " && date
/usr/local/bin/oc get nodes

printf "Output of oc get co on bastion at " && date
/usr/local/bin/oc get co

printf "\nOutput of oc adm top nodes on bastion at " && date
/usr/local/bin/oc adm top nodes

printf "\nOutput of oc get pods --all-namespaces -o=wide | grep -vi running | grep -vi completed on bastion at " && date
/usr/local/bin/oc get pods --all-namespaces -o=wide | grep -vi running | grep -vi completed

printf "\nOutput of cat /proc/meminfo on bastion at " && date
/usr/bin/cat /proc/meminfo

printf "\nOutput of systemd-cgtop on bastion at  " && date
ssh -oStrictHostKeyChecking=no root@$(/usr/sbin/ip route get 1 | cut -d" " -f7) /usr/bin/systemd-cgtop

printf "\nOutput of oc adm top pod --all-namespaces on bastion at " && date
/usr/local/bin/oc adm top pod --all-namespaces

mapfile -t array < <(/usr/local/bin/oc get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}')

arraylength=$(( ${#array[@]} ))

for (( i=0; i<${arraylength}; i++ ));
do
   if [ -z "${array[i]}" ]
     then
       arraylength=$(( ${#array[@]} - 1 ))
   elif [ "${array[i]}" == ' ' ]
   then
       arraylength=$(( ${#array[@]} - 1 ))
   fi
done


for (( i=0; i<${arraylength}; i++ ));
do
 printf "\n***************************************************************\n"
 printf "\n Executing commands on ${array[i]}\n"

 printf "\n Output of systemctl status kubelet on ${array[i]} at " && date
 ssh -oStrictHostKeyChecking=no core@${array[i]} /bin/systemctl status kubelet

 printf "\n Output of cat /etc/os-release on ${array[i]} at " && date
 ssh core@${array[i]} /etc/os-release

 printf "\n Output of systemctl status crio on ${array[i]} at " && date
 ssh core@${array[i]} /bin/systemctl status crio

 printf "\n Output of free -g on ${array[i]} at " && date
 ssh core@${array[i]} /usr/bin/free -g

 printf "\n Output of /proc/meminfo on ${array[i]} at " && date
 ssh core@${array[i]} /bin/cat /proc/meminfo

 printf "\n Output of systemd-cgtop on ${array[i]} at " && date
 ssh core@${array[i]} systemd-cgtop

 printf "\n Output of /proc/slabinfo on ${array[i]} at " && date
 ssh core@${array[i]} sudo /bin/cat /proc/slabinfo

 printf "\n Output of /proc/pagetypeinfo on ${array[i]} at " && date
 ssh core@${array[i]} sudo /bin/cat /proc/pagetypeinfo

 printf "\n Output of /proc/zoneinfo on ${array[i]} at " && date
 ssh core@${array[i]} sudo /bin/cat /proc/zoneinfo

 printf "\n Output of top  -b -n 1 on ${array[i]} at " && date
 ssh core@${array[i]} sudo /bin/top  -b -n 1

 printf "\n Output of /bin/dmesg on ${array[i]} at " && date
 ssh core@${array[i]} sudo /bin/dmesg

 printf "\n***************************************************************\n"
done
