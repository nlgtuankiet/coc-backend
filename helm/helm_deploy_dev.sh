#!/bin/bash
set -e

### Enviroment
env="dev"
initialize="false"
debug="false"
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
postgres_password_base64=$(echo $postgres_password | base64 -w 0 -)


### Postgres config
postgres_config=./postgresql.conf.dev
if [ "$env" = "prod" ]; then
    postgres_config=./postgresql.conf.prod
fi
echo "postgres_config -> $postgres_config"
postgres_config_base64=$(base64 -w 0 "$postgres_config")

### Firebase service account
firebase_service_account=~/.gcloud/coc-dev-service-account.json
echo "firebase_service_account -> $firebase_service_account"
firebase_service_account_file_base64=$(base64 -w 0 "$firebase_service_account")

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