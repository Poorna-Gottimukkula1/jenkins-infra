#!/bin/bash -x

declare -a ENV_VARS
ENV_VARS=( "CLUSTER_DIR" "IBMCLOUD_API_KEY" "IBMID" "POWERVS_REGION" "POWERVS_ZONE" "RESOURCE_GROUP" "SERVICE_INSTANCE_GUID")

for VAR in ${ENV_VARS[@]}
do
	if [[ ! -v ${VAR} ]]
	then
		echo "${VAR} must be set!"
		exit 1
	fi
	VALUE=$(eval "echo \"\${${VAR}}\"")
	if [[ -z "${VALUE}" ]]
	then
		echo "${VAR} must be set!"
		exit 1
	fi
done

rm -rf ${CLUSTER_DIR}
rm -rf ~/.powervs
pwd

mkdir ${CLUSTER_DIR}
mkdir ~/.powervs

# cat << ___EOF___ > ${CLUSTER_DIR}/metadata.json
# {
#   "clusterName": "${CLUSTER_NAME}",
#   "clusterID": "d5f1079c-8b18-4fc7-ba2e-d8661ef2007d",
#   "infraID": "${CLUSTER_NAME}-${CLUSTER_PREFIX}",
#   "powervs": {
#     "BaseDomain": "${BASEDOMAIN}",
#     "cisInstanceCRN": "${CIS_CRN}",
#     "dnsInstanceCRN": "",
#     "powerVSResourceGroup": "${RESOURCE_GROUP}",
#     "region": "${POWERVS_REGION}",
#     "vpcRegion": "",
#     "zone": "${POWERVS_ZONE}",
#     "serviceInstanceGUID": "${SERVICE_INSTANCE_GUID}"
#   },
#   "featureSet": "",
#   "customFeatureSet": null
# }
# ___EOF___

cat << ___EOF___ > ${HOME}/.powervs/config.json
{
  "id": "${IBMID}",
  "apikey": "${IBMCLOUD_API_KEY}",
  "region": "${POWERVS_REGION}",
  "zone": "${POWERVS_ZONE}",
  "resourcegroup": "${RESOURCE_GROUP}"
}
___EOF___
cp -rp ${WORKSPACE}/deploy/metadata.json ${CLUSTER_DIR}/metadata.json
cat ${HOME}/.powervs/config.json
cat ${CLUSTER_DIR}/metadata.json

retries=0
until [ "$retries" -ge 2 ]
do
  openshift-install destroy cluster  --log-level=debug --dir ${CLUSTER_DIR} || true
  retries=$((retries+1))
done
openshift-install destroy cluster  --log-level=debug --dir ${CLUSTER_DIR}

#TO DO
#Purge networks
#pvsadm purge networks -i ${SERVICE_INSTANCE_ID}  --regexp "$cluster_id*" --no-prompt --ignore-errors
