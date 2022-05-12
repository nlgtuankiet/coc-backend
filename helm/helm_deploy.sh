#!/bin/bash
set -e

### Build and invoke helm upgrade command
###
### Flags:
### -e: set enviroment, valid values are dev/prod, default to dev
### -d: do nothing, just print the command for debug only
### -i: when set, invoke helm "upgrade" command with -i option


### Enviroment
env="dev"
initialize="false"
debug="false"
base64Cmd="base64"
unameOut="$(uname -s)"

case "${unameOut}" in
    Linux*)
        echo "Linux detected"
        base64Cmd="base64 -w 0";;
esac

while getopts ":e:id" opt; do
    case $opt in
        e)
        env="$OPTARG"
        ;;

        i)
        initialize="true"
        ;;

        d)
        debug="true"
        ;;

        \?)
        echo "Invalid option: -$OPTARG" >&2
        exit 1
        ;;

        :)
        echo "Option -$OPTARG requires an argument." >&2
        exit 1
        ;;
    esac
done

if [ $env != "prod" ] && [ $env != "dev" ]; then
    echo "Invalid -e value: $env"
    exit 1
fi
echo "env -> $env"
echo "initialize -> $initialize"

### Postgres password
postgres_password=`printenv COC_POSTGRES_PASSWORD || echo ''`
if [ -z "$postgres_password" ]; then
    echo "COC_POSTGRES_PASSWORD not set, please input postgres password"
    stty -echo
    read -p "Password:" postgres_password
    stty echo
fi

if [ -z "$postgres_password" ]; then
    exit 1
fi
# echo "postgres_password -> $postgres_password"
postgres_password_base64=$(echo $postgres_password | $base64Cmd -)


### Postgres config
postgres_config=./postgresql.conf.dev
if [ "$env" = "prod" ]; then
    postgres_config=./postgresql.conf.prod
fi
echo "postgres_config -> $postgres_config"
postgres_config_base64=$($base64Cmd "$postgres_config")

### Firebase service account
firebase_service_account=~/.gcloud/coc-dev-service-account.json
echo "firebase_service_account -> $firebase_service_account"
firebase_service_account_file_base64=$($base64Cmd "$firebase_service_account")

### Build helm command
if [ "$debug" = "true" ]; then
    firebase_service_account_file_base64="aaa"
    postgres_password_base64="aaa"
    postgres_config_base64="aaa"
fi

helm_command="helm upgrade "
if [ "$initialize" = "true" ]; then
    helm_command="$helm_command -i "
fi
helm_command="$helm_command coc . -f ./values.yaml "
if [ "$env" = "dev" ]; then
    helm_command="$helm_command -f ./values-dev.yaml "
fi

helm_command="$helm_command --set firebase_service_account_file_base64=$firebase_service_account_file_base64 "
helm_command="$helm_command --set postgres_password_base64=$postgres_password_base64 "
helm_command="$helm_command --set postgres_config_base64=$postgres_config_base64 "

if [ "$debug" = "true" ]; then
    echo $helm_command
else
    eval $helm_command
fi
