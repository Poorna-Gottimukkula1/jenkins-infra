#!/bin/bash


# Added 4hrs to 24hrs if any delay in imagestream creation
compare_time=$(date -d  "1128 hour ago" +%s)
public_repo=$(oc get is release-ppc64le -n ocp-ppc64le -o=json | jq -r -c '.status.publicDockerImageRepository')
target_repo="${DOCKER_REGISTRY}/ocp-ppc64le/release-ppc64le"

#Check if the image tag is availble in the target repository.
checkImage(){ 
    curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${ARTIFACTORY_TOKEN}" "https://na.artifactory.swg-devops.com/artifactory/sys-powercloud-docker-local/ocp-ppc64le/release-ppc64le/${1}/"
}

echo here is the public repo: $public_repo
for annotation in $(oc get is release-ppc64le -n ocp-ppc64le -o=json | jq -c '.spec.tags[]'); do
    _jq() {
     echo ${annotation} | jq -r ${1} 2> /dev/null
    }

    creation_time=$(_jq '.annotations."release.openshift.io/creationTimestamp"')
    if [ "$creation_time" == "null" ] || [ -z "$creation_time" ]; then
       continue
    fi
    creation_timestamp=$(date -d ${creation_time} +%s)
    if [ ${creation_timestamp} -gt ${compare_time} ]; then
        tag=$(_jq '.name')
        if [ ! $(checkImage ${tag}) == "200" ]; then
            echo "Image Tag: ${tag} not available in target repository: ${target_repo}:${tag}"
            nerdctl pull ${public_repo}:${tag}
            nerdctl tag ${public_repo}:${tag} ${target_repo}:${tag}
            nerdctl push ${target_repo}:${tag}
            nerdctl rmi ${public_repo}:${tag} ${target_repo}:${tag} ${target_repo}:${tag}-tmp-single
        fi
    fi
done

# Pulling EC builds
curl https://ppc64le.ocp.releases.ci.openshift.org/api/v1/releasestream/4-dev-preview-ppc64le/latest > build.txt
ec_build=$(jq ".pullSpec"  build.txt |tr -d '"')
ec_tag=$(jq ".name"  build.txt |tr -d '"')
if [ ! $(checkImage ${ec_tag}) == "200" ]; then
    nerdctl pull $ec_build
    nerdctl tag $ec_build ${target_repo}:$ec_tag
    nerdctl push ${target_repo}:$ec_tag
    nerdctl rmi $ec_build ${target_repo}:$ec_tag ${target_repo}:$ec_tag-tmp-single
fi