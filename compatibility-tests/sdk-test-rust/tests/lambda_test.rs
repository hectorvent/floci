mod common;

use aws_sdk_lambda::types::{ImageConfig, PackageType, Runtime};

#[tokio::test(flavor = "multi_thread")]
async fn test_lambda_create_function() {
    let lambda = common::lambda_client().await;
    let func_name = "rust-test-create-func";
    let role_arn = "arn:aws:iam::000000000000:role/test-role";

    let _guard = common::CleanupGuard::new({
        let lambda = lambda.clone();
        async move {
            let _ = lambda.delete_function().function_name(func_name).send().await;
        }
    });

    let result = lambda
        .create_function()
        .function_name(func_name)
        .runtime(Runtime::Nodejs18x)
        .role(role_arn)
        .handler("index.handler")
        .code(
            aws_sdk_lambda::types::FunctionCode::builder()
                .zip_file(aws_smithy_types::Blob::new(common::minimal_zip()))
                .build(),
        )
        .send()
        .await;
    assert!(result.is_ok(), "CreateFunction failed: {:?}", result.err());
}

#[tokio::test(flavor = "multi_thread")]
async fn test_lambda_get_function() {
    let lambda = common::lambda_client().await;
    let func_name = "rust-test-get-func";
    let role_arn = "arn:aws:iam::000000000000:role/test-role";

    let _guard = common::CleanupGuard::new({
        let lambda = lambda.clone();
        async move {
            let _ = lambda.delete_function().function_name(func_name).send().await;
        }
    });

    // Setup
    lambda
        .create_function()
        .function_name(func_name)
        .runtime(Runtime::Nodejs18x)
        .role(role_arn)
        .handler("index.handler")
        .code(
            aws_sdk_lambda::types::FunctionCode::builder()
                .zip_file(aws_smithy_types::Blob::new(common::minimal_zip()))
                .build(),
        )
        .send()
        .await
        .expect("setup");

    let result = lambda.get_function().function_name(func_name).send().await;
    assert!(result.is_ok(), "GetFunction failed: {:?}", result.err());
    assert_eq!(
        result
            .unwrap()
            .configuration()
            .and_then(|c| c.function_name())
            .unwrap_or(""),
        func_name
    );
}

#[tokio::test(flavor = "multi_thread")]
async fn test_lambda_list_functions() {
    let lambda = common::lambda_client().await;
    let func_name = "rust-test-list-func";
    let role_arn = "arn:aws:iam::000000000000:role/test-role";

    let _guard = common::CleanupGuard::new({
        let lambda = lambda.clone();
        async move {
            let _ = lambda.delete_function().function_name(func_name).send().await;
        }
    });

    // Setup
    lambda
        .create_function()
        .function_name(func_name)
        .runtime(Runtime::Nodejs18x)
        .role(role_arn)
        .handler("index.handler")
        .code(
            aws_sdk_lambda::types::FunctionCode::builder()
                .zip_file(aws_smithy_types::Blob::new(common::minimal_zip()))
                .build(),
        )
        .send()
        .await
        .expect("setup");

    let result = lambda.list_functions().send().await;
    assert!(result.is_ok(), "ListFunctions failed: {:?}", result.err());
    assert!(!result.unwrap().functions().is_empty());
}

#[tokio::test(flavor = "multi_thread")]
async fn test_lambda_invoke() {
    let lambda = common::lambda_client().await;
    let func_name = "rust-test-invoke";
    let role_arn = "arn:aws:iam::000000000000:role/test-role";

    let _guard = common::CleanupGuard::new({
        let lambda = lambda.clone();
        async move {
            let _ = lambda.delete_function().function_name(func_name).send().await;
        }
    });

    // Setup
    lambda
        .create_function()
        .function_name(func_name)
        .runtime(Runtime::Nodejs18x)
        .role(role_arn)
        .handler("index.handler")
        .code(
            aws_sdk_lambda::types::FunctionCode::builder()
                .zip_file(aws_smithy_types::Blob::new(common::minimal_zip()))
                .build(),
        )
        .send()
        .await
        .expect("setup");

    let payload = r#"{"name":"RustTest"}"#;
    let result = lambda
        .invoke()
        .function_name(func_name)
        .payload(aws_smithy_types::Blob::new(payload.as_bytes().to_vec()))
        .send()
        .await;
    assert!(result.is_ok(), "Invoke failed: {:?}", result.err());

    let response = result.unwrap();
    assert_eq!(response.status_code(), 200);
    assert!(response.function_error().is_none());
}

#[tokio::test(flavor = "multi_thread")]
async fn test_lambda_delete_function() {
    let lambda = common::lambda_client().await;
    let func_name = "rust-test-delete-func";
    let role_arn = "arn:aws:iam::000000000000:role/test-role";

    // No cleanup guard needed - test is about deletion

    // Setup
    lambda
        .create_function()
        .function_name(func_name)
        .runtime(Runtime::Nodejs18x)
        .role(role_arn)
        .handler("index.handler")
        .code(
            aws_sdk_lambda::types::FunctionCode::builder()
                .zip_file(aws_smithy_types::Blob::new(common::minimal_zip()))
                .build(),
        )
        .send()
        .await
        .expect("setup");

    let result = lambda.delete_function().function_name(func_name).send().await;
    assert!(result.is_ok(), "DeleteFunction failed: {:?}", result.err());
}

#[tokio::test(flavor = "multi_thread")]
async fn test_lambda_image_config_working_directory_round_trip() {
    let lambda = common::lambda_client().await;
    let func_name = "rust-test-imgwd-fn";
    let role_arn = "arn:aws:iam::000000000000:role/test-role";
    let image_uri = "000000000000.dkr.ecr.us-east-1.amazonaws.com/fake-repo:latest";

    let _guard = common::CleanupGuard::new({
        let lambda = lambda.clone();
        async move {
            let _ = lambda.delete_function().function_name(func_name).send().await;
        }
    });

    let create_resp = lambda
        .create_function()
        .function_name(func_name)
        .package_type(PackageType::Image)
        .role(role_arn)
        .code(
            aws_sdk_lambda::types::FunctionCode::builder()
                .image_uri(image_uri)
                .build(),
        )
        .image_config(
            ImageConfig::builder()
                .working_directory("/app")
                .build(),
        )
        .send()
        .await
        .expect("CreateFunction with ImageConfig.WorkingDirectory failed");

    let wd = create_resp
        .image_config_response()
        .and_then(|r| r.image_config())
        .and_then(|c| c.working_directory());
    assert_eq!(wd, Some("/app"), "CreateFunction response must include WorkingDirectory");

    let get_resp = lambda
        .get_function_configuration()
        .function_name(func_name)
        .send()
        .await
        .expect("GetFunctionConfiguration failed");

    let wd = get_resp
        .image_config_response()
        .and_then(|r| r.image_config())
        .and_then(|c| c.working_directory());
    assert_eq!(wd, Some("/app"), "GetFunctionConfiguration must persist WorkingDirectory");

    let update_resp = lambda
        .update_function_configuration()
        .function_name(func_name)
        .image_config(
            ImageConfig::builder()
                .working_directory("/updated")
                .build(),
        )
        .send()
        .await
        .expect("UpdateFunctionConfiguration failed");

    let wd = update_resp
        .image_config_response()
        .and_then(|r| r.image_config())
        .and_then(|c| c.working_directory());
    assert_eq!(wd, Some("/updated"), "UpdateFunctionConfiguration must update WorkingDirectory");
}
