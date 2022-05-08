helm upgrade coc . \
    -f ./values.yaml \
    --set firebase_service_account_file_base64=$(base64 ~/.gcloud/coc-prod-service-account.json) \
    --set postgres_password_base64=$(echo testpassword | base64 -) \
    --set postgres_config_base64=$(cat ./postgresql.conf.prod | base64 -)
