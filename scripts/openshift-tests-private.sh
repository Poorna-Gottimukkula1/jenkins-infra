#!/bin/bash
# select the cases per FILTERS
echo "========"
# create ICSP for connected env.
function create_icsp_connected () {
    cat <<EOF | oc create -f -
    apiVersion: operator.openshift.io/v1alpha1
    kind: ImageContentSourcePolicy
    metadata:
      name: brew-registry
    spec:
      repositoryDigestMirrors:
      - mirrors:
        - brew.registry.redhat.io
        source: registry.redhat.io
      - mirrors:
        - brew.registry.redhat.io
        source: registry.stage.redhat.io
      - mirrors:
        - brew.registry.redhat.io
        source: registry-proxy.engineering.redhat.com
EOF
    if [ $? == 0 ]; then
        echo "create the ICSP successfully"
    else
        echo "!!! fail to create the ICSP"
        return 1
    fi
}

function create_catalog_sources() {
    # get cluster Major.Minor version
    kube_major=$(oc version -o json |jq -r '.serverVersion.major')
    kube_minor=$(oc version -o json |jq -r '.serverVersion.minor')
    index_image="quay.io/openshift-qe-optional-operators/aosqe-index:v${kube_major}.${kube_minor}"

    echo "Create QE catalogsource: qe-app-registry"
    echo "Use $index_image in catalogsource/qe-app-registry"
    # since OCP 4.15, the official catalogsource use this way. OCP4.14=K8s1.27
    # details: https://issues.redhat.com/browse/OCPBUGS-31427
    if [[ ${kube_major} -gt 1 || ${kube_minor} -gt 27 ]]; then
        echo "the index image as the initContainer cache image)"
        cat <<EOF | oc create -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: qe-app-registry
  namespace: openshift-marketplace
  annotations:
    olm.catalogImageTemplate: "quay.io/openshift-qe-optional-operators/aosqe-index:v{kube_major_version}.{kube_minor_version}"
spec:
  displayName: Production Operators
  grpcPodConfig:
    extractContent:
      cacheDir: /tmp/cache
      catalogDir: /configs
    memoryTarget: 30Mi
  image: ${index_image}
  publisher: OpenShift QE
  sourceType: grpc
  updateStrategy:
    registryPoll:
      interval: 15m
EOF
    else
        echo "the index image as the server image"
        cat <<EOF | oc create -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: qe-app-registry
  namespace: openshift-marketplace
  annotations:
    olm.catalogImageTemplate: "quay.io/openshift-qe-optional-operators/aosqe-index:v{kube_major_version}.{kube_minor_version}"
spec:
  displayName: Production Operators
  image: ${index_image}
  publisher: OpenShift QE
  sourceType: grpc
  updateStrategy:
    registryPoll:
      interval: 15m
EOF
    fi

    set +e
    COUNTER=0
    while [ $COUNTER -lt 600 ]
    do
        sleep 20
        COUNTER=`expr $COUNTER + 20`
        echo "waiting ${COUNTER}s"
        STATUS=`oc -n openshift-marketplace get catalogsource qe-app-registry -o=jsonpath="{.status.connectionState.lastObservedState}"`
        if [[ $STATUS = "READY" ]]; then
            echo "create the QE CatalogSource successfully"
            break
        fi
    done
    if [[ $STATUS != "READY" ]]; then
        echo "!!! fail to create QE CatalogSource"
        # ImagePullBackOff nothing with the imagePullSecrets
        # run_command "oc get operatorgroup -n openshift-marketplace"
        # run_command "oc get sa qe-app-registry -n openshift-marketplace -o yaml"
        # run_command "oc -n openshift-marketplace get secret $(oc -n openshift-marketplace get sa qe-app-registry -o=jsonpath='{.secrets[0].name}') -o yaml"

        run_command "oc get pods -o wide -n openshift-marketplace"
        run_command "oc -n openshift-marketplace get catalogsource qe-app-registry -o yaml"
        run_command "oc -n openshift-marketplace get pods -l olm.catalogSource=qe-app-registry -o yaml"
        node_name=$(oc -n openshift-marketplace get pods -l olm.catalogSource=qe-app-registry -o=jsonpath='{.items[0].spec.nodeName}')
        run_command "oc create ns debug-qe -o yaml | oc label -f - security.openshift.io/scc.podSecurityLabelSync=false pod-security.kubernetes.io/enforce=privileged pod-security.kubernetes.io/audit=privileged pod-security.kubernetes.io/warn=privileged --overwrite"
        run_command "oc -n debug-qe debug node/${node_name} -- chroot /host podman pull --authfile /var/lib/kubelet/config.json ${index_image}"

        run_command "oc get mcp,node"
        run_command "oc get mcp worker -o yaml"
        run_command "oc get mc $(oc get mcp/worker --no-headers | awk '{print $2}') -o=jsonpath={.spec.config.storage.files}|jq '.[] | select(.path==\"/var/lib/kubelet/config.json\")'"

        return 1
    fi
    set -e
}

function run_command() {
    local CMD="$1"
    echo "Running Command: ${CMD}"
    eval "${CMD}"
}

create_icsp_connected
create_catalog_sources
export KUBECONFIG=/home/jenkins/.kube/config
TEST_PROVIDER='{"type":"skeleton"}'
TEST_SCENARIOS="MCO|ETCD|PSAP|SDN|STORAGE|API_Server|Authentication|Cluster_Operator|Cluster_Infrastructure|OLM|Network_Edge|Operator_SDK|Workloads|Image_Registry|Container_Engine_Tools|NODE|OTA|PerfScale|Cluster_Observability|Security_and_Compliance|LOGGING|CFE"
TEST_ADDITIONAL=""
TEST_IMPORTANCE=""
TEST_TIMEOUT="15"
TEST_FILTERS="~ChkUpgrade&;~NonPreRelease&;~Serial&;~Disruptive&;~DisconnectedOnly&;~HyperShiftMGMT&;~MicroShiftOnly&"
FILTERS_ADDITIONAL="~CPaasrunOnly&"
MODULE_FILTERS=""
TEST_PARALLEL="6"

function run {
    test_scenarios=""
    echo "TEST_SCENARIOS: \"${TEST_SCENARIOS:-}\""
    echo "TEST_ADDITIONAL: \"${TEST_ADDITIONAL:-}\""
    echo "TEST_IMPORTANCE: \"${TEST_IMPORTANCE}\""
    echo "TEST_TIMEOUT: \"${TEST_TIMEOUT}\""
    if [[ -n "${TEST_SCENARIOS:-}" ]]; then
        readarray -t scenarios <<< "${TEST_SCENARIOS}"
        for scenario in "${scenarios[@]}"; do
            if [ "W${scenario}W" != "WW" ]; then
                test_scenarios="${test_scenarios}|${scenario}"
            fi
        done
    else
        echo "there is no scenario"
        return
    fi

    if [ "W${test_scenarios}W" == "WW" ]; then
        echo "fail to parse ${TEST_SCENARIOS}"
        exit 1
    fi
    echo "test scenarios: ${test_scenarios:1}"
    test_scenarios="${test_scenarios:1}"

    test_additional=""
    if [[ -n "${TEST_ADDITIONAL:-}" ]]; then
        readarray -t additionals <<< "${TEST_ADDITIONAL}"
        for additional in "${additionals[@]}"; do
            test_additional="${test_additional}|${additional}"
        done
    else
        echo "there is no additional"
    fi

    if [ "W${test_additional}W" != "WW" ]; then
        if [ "W${test_additional: -1}W" != "W|W" ]; then
            echo "test additional: ${test_additional:1}"
            test_scenarios="${test_scenarios}|${test_additional:1}"
        else
            echo "test additional: ${test_additional:1:-1}"
            test_scenarios="${test_scenarios}|${test_additional:1:-1}"
        fi
    fi

    echo "final scenarios: ${test_scenarios}"
    extended-platform-tests run all --dry-run | \
        grep -E "${test_scenarios}" | grep -E "${TEST_IMPORTANCE}" > ./case_selected

    hardcoded_filters="~NonUnifyCI&;~Flaky&;~DEPRECATED&;~SUPPLEMENTARY&;~VMonly&;~ProdrunOnly&;~StagerunOnly&"
    if [[ "${test_scenarios}" == *"Stagerun"* ]] && [[ "${test_scenarios}" != *"~Stagerun"* ]]; then
        hardcoded_filters="~NonUnifyCI&;~Flaky&;~DEPRECATED&;~VMonly&;~ProdrunOnly&"
    fi
    echo "TEST_FILTERS: \"${hardcoded_filters};${TEST_FILTERS:-}\""
    echo "FILTERS_ADDITIONAL: \"${FILTERS_ADDITIONAL:-}\""
    test_filters="${hardcoded_filters};${TEST_FILTERS}"
    if [[ -n "${FILTERS_ADDITIONAL:-}" ]]; then
        echo "add FILTERS_ADDITIONAL into test_filters"
        test_filters="${hardcoded_filters};${TEST_FILTERS};${FILTERS_ADDITIONAL}"
    fi
    echo "------handle test filter start------"
    echo "${test_filters}"
    handle_filters "${test_filters}"
    echo "------handle test filter done------"

    echo "------handle module filter start------"
    echo "MODULE_FILTERS: \"${MODULE_FILTERS:-}\""
    handle_module_filter "${MODULE_FILTERS}"
    echo "------handle module filter done------"

    echo "------------------the case selected------------------"
    selected_case_num=$(cat ./case_selected|wc -l)
    echo ${selected_case_num}
    cat ./case_selected
    echo "-----------------------------------------------------"

    if [ "W${TEST_PROVIDER}W" == "WnoneW" ]; then
        extended-platform-tests run --max-parallel-tests ${TEST_PARALLEL} \
        -o "extended-platform-tests.log" \
        --timeout "${TEST_TIMEOUT}m" --junit-dir="junit" -f ./case_selected | tee extended-platform-tests_cmd.log 2>&1
    else
        extended-platform-tests run --max-parallel-tests ${TEST_PARALLEL} \
        --provider "${TEST_PROVIDER}" -o "extended-platform-tests.log" \
        --timeout "${TEST_TIMEOUT}m" --junit-dir="junit" -f ./case_selected | tee extended-platform-tests_cmd.log 2>&1
    fi
}

function check_case_selected {
    found_ok=$1
    if [ "W${found_ok}W" == "W0W" ]; then
        echo "find case"
    else
        echo "do not find case"
    fi
}
function handle_filters {
    filter_tmp="$1"
    if [ "W${filter_tmp}W" == "WW" ]; then
        echo "there is no filter"
        return
    fi
    echo "try to handler filters..."
    IFS=";" read -r -a filters <<< "${filter_tmp}"

    filters_and=()
    filters_or=()
    for filter in "${filters[@]}"
    do
        echo "${filter}"
        valid_filter "${filter}"
        filter_logical="$(echo $filter | grep -Eo '[&]?$')"

        if [ "W${filter_logical}W" == "W&W" ]; then
            filters_and+=( "$filter" )
        else
            filters_or+=( "$filter" )
        fi
    done

    echo "handle AND logical"
    for filter in ${filters_and[*]}
    do
        echo "handle filter_and ${filter}"
        handle_and_filter "${filter}"
    done

    echo "handle OR logical"
    rm -fr ./case_selected_or
    for filter in ${filters_or[*]}
    do
        echo "handle filter_or ${filter}"
        handle_or_filter "${filter}"
    done
    if [[ -e ./case_selected_or ]]; then
        sort -u ./case_selected_or > ./case_selected && rm -fr ./case_selected_or
    fi
}

function valid_filter {
    filter="$1"
    if ! echo ${filter} | grep -E '^[~]?[a-zA-Z0-9_]{1,}[&]?$'; then
        echo "the filter ${filter} is not correct format. it should be ^[~]?[a-zA-Z0-9_]{1,}[&]?$"
        exit 1
    fi
    action="$(echo $filter | grep -Eo '^[~]?')"
    value="$(echo $filter | grep -Eo '[a-zA-Z0-9_]{1,}')"
    logical="$(echo $filter | grep -Eo '[&]?$')"
    echo "$action--$value--$logical"
}

function handle_and_filter {
    action="$(echo $1 | grep -Eo '^[~]?')"
    value="$(echo $1 | grep -Eo '[a-zA-Z0-9_]{1,}')"

    ret=0
    if [ "W${action}W" == "WW" ]; then
        cat ./case_selected | grep -E "${value}" > ./case_selected_and || ret=$?
        check_case_selected "${ret}"
    else
        cat ./case_selected | grep -v -E "${value}" > ./case_selected_and || ret=$?
        check_case_selected "${ret}"
    fi
    if [[ -e ./case_selected_and ]]; then
        cp -fr ./case_selected_and ./case_selected && rm -fr ./case_selected_and
    fi
}

function handle_or_filter {
    action="$(echo $1 | grep -Eo '^[~]?')"
    value="$(echo $1 | grep -Eo '[a-zA-Z0-9_]{1,}')"

    ret=0
    if [ "W${action}W" == "WW" ]; then
        cat ./case_selected | grep -E "${value}" >> ./case_selected_or || ret=$?
        check_case_selected "${ret}"
    else
        cat ./case_selected | grep -v -E "${value}" >> ./case_selected_or || ret=$?
        check_case_selected "${ret}"
    fi
}

function handle_module_filter {
    local module_filter="$1"
    declare -a module_filter_keys
    declare -a module_filter_values
    valid_and_get_module_filter "$module_filter"


    for i in "${!module_filter_keys[@]}"; do

        module_key="${module_filter_keys[$i]}"
        filter_value="${module_filter_values[$i]}"
        echo "moudle: $module_key"
        echo "filter: $filter_value"
        [ -s ./case_selected ] || { echo "No Case already Selected before handle ${module_key}"; continue; }

        cat ./case_selected | grep -v -E "${module_key}" > ./case_selected_exclusive || true
        cat ./case_selected | grep -E "${module_key}" > ./case_selected_inclusive || true
        rm -fr ./case_selected && cp -fr ./case_selected_inclusive ./case_selected && rm -fr ./case_selected_inclusive

        handle_filters "${filter_value}"

        [ -e ./case_selected ] && cat ./case_selected_exclusive >> ./case_selected && rm -fr ./case_selected_exclusive
        [ -e ./case_selected ] && sort -u ./case_selected > ./case_selected_sort && mv -f ./case_selected_sort ./case_selected

    done
}

function valid_and_get_module_filter {
    local module_filter_tmp="$1"

    IFS='#' read -ra pairs <<< "$module_filter_tmp"
    for pair in "${pairs[@]}"; do
        IFS=':' read -ra kv <<< "$pair"
        if [[ ${#kv[@]} -ne 2 ]]; then
            echo "moudle filter format is not correct"
            exit 1
        fi

        module_key="${kv[0]}"
        filter_value="${kv[1]}"
        module_filter_keys+=("$module_key")
        module_filter_values+=("$filter_value")
    done
}
run
