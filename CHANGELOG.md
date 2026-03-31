# [1.1.0](https://github.com/hectorvent/floci/compare/1.0.11...1.1.0) (2026-03-31)


### Bug Fixes

* added versionId to S3 notifications for versioning enabled buckets. ([#135](https://github.com/hectorvent/floci/issues/135)) ([3d67bc4](https://github.com/hectorvent/floci/commit/3d67bc4ba38da69fe116a865e442cfc30a33c1b3))
* align S3 CreateBucket and HeadBucket region behavior with AWS ([#75](https://github.com/hectorvent/floci/issues/75)) ([8380166](https://github.com/hectorvent/floci/commit/838016660cb58daa0e06892c3d7aa554eb191f62))
* DynamoDB table creation compatibility with Terraform AWS provider v6 ([#89](https://github.com/hectorvent/floci/issues/89)) ([7b87bf2](https://github.com/hectorvent/floci/commit/7b87bf2c1fa8f9cff7aef4be488d7b2cbf3fe26d))
* **dynamodb:** apply filter expressions in Query ([#123](https://github.com/hectorvent/floci/issues/123)) ([8b6f4fa](https://github.com/hectorvent/floci/commit/8b6f4fa4f51b73240f5b685bd835172fb996d780))
* **dynamodb:** respect `if_not_exists` for `update_item` ([#102](https://github.com/hectorvent/floci/issues/102)) ([8882a8e](https://github.com/hectorvent/floci/commit/8882a8ebe2213e383ff719793c137b50a937c6c0))
* for no-such-key with non-ascii key ([#112](https://github.com/hectorvent/floci/issues/112)) ([ab072cf](https://github.com/hectorvent/floci/commit/ab072cf660f784ab5a65077573e3adf36990a2ae))
* **KMS:** Allow arn and alias to encrypt ([#69](https://github.com/hectorvent/floci/issues/69)) ([fa4e107](https://github.com/hectorvent/floci/commit/fa4e107572792b5cc4dc6e3f4b323695a4a9add7))
* resolve compatibility test failures across multiple services ([#109](https://github.com/hectorvent/floci/issues/109)) ([1377868](https://github.com/hectorvent/floci/commit/1377868094389616308e3d379c9979a883051f9a))
* **s3:** allow upload up to 512MB by default. close [#19](https://github.com/hectorvent/floci/issues/19) ([#110](https://github.com/hectorvent/floci/issues/110)) ([3891232](https://github.com/hectorvent/floci/commit/38912326c96741022fc05cc3c0ddc8c1612b906a))
* **s3:** expose inMemory flag in test constructor to fix S3 disk-persistence tests ([#136](https://github.com/hectorvent/floci/issues/136)) ([522b369](https://github.com/hectorvent/floci/commit/522b3696a6ae3aa8bfb3b02f4284a507c91ffa94))
* **sns:** add PublishBatch support to JSON protocol handler ([543df05](https://github.com/hectorvent/floci/commit/543df0539b2e68ad2795ce9deb0557624aeea70a))
* Storage load after backend is created ([#71](https://github.com/hectorvent/floci/issues/71)) ([c95dd10](https://github.com/hectorvent/floci/commit/c95dd1068e7910e3c19bd888be421469b64a1ad9))
* **storage:** fix storage global config issue and memory s3 directory creation ([b84a128](https://github.com/hectorvent/floci/commit/b84a1281f86f01a3de656748f8d6b90dd20e798f))


### Features

* add ACM support ([#21](https://github.com/hectorvent/floci/issues/21)) ([8a8d55d](https://github.com/hectorvent/floci/commit/8a8d55d9727c41eb0f5aa8a434ce792e64cfeed2))
* add HOSTNAME_EXTERNAL support for multi-container Docker setups ([#82](https://github.com/hectorvent/floci/issues/82)) ([20b40c1](https://github.com/hectorvent/floci/commit/20b40c1565b87e203dd6ce3d453e019ab0557e80)), closes [#81](https://github.com/hectorvent/floci/issues/81)
* add JSONata query language support for Step Functions ([#84](https://github.com/hectorvent/floci/issues/84)) ([f82b370](https://github.com/hectorvent/floci/commit/f82b370ab2e38f40306c7e330d97da4f720fe828))
* add Kinesis ListShards operation ([#61](https://github.com/hectorvent/floci/issues/61)) ([6ff8190](https://github.com/hectorvent/floci/commit/6ff819083d48de01317c1de7f12eaa7f23a638a4))
* add opensearch service emulation ([#85](https://github.com/hectorvent/floci/issues/85)) ([#132](https://github.com/hectorvent/floci/issues/132)) ([68b8ed8](https://github.com/hectorvent/floci/commit/68b8ed883a45ac35690c474a7d82179db642b145))
* add SES (Simple Email Service) emulation ([#14](https://github.com/hectorvent/floci/issues/14)) ([9bf23d5](https://github.com/hectorvent/floci/commit/9bf23d5513ddeeca83b9185baea34b5fb2dbeaa9))
* Adding/Fixing support for virtual hosts ([#88](https://github.com/hectorvent/floci/issues/88)) ([26facf2](https://github.com/hectorvent/floci/commit/26facf26e5d6b1cfd6dda0825e43d02645cdb0fa))
* **APIGW:** add AWS integration type for API Gateway REST v1 ([#108](https://github.com/hectorvent/floci/issues/108)) ([bb4f000](https://github.com/hectorvent/floci/commit/bb4f000914caea64f27c78ce8abab85c1ffac344))
* **APIGW:** OpenAPI/Swagger import, models, and request validation ([#113](https://github.com/hectorvent/floci/issues/113)) ([d1d7ec3](https://github.com/hectorvent/floci/commit/d1d7ec3bd31281a95626042ad71c4d50df0610ab))
* docker image with awscli Closes: [#66](https://github.com/hectorvent/floci/issues/66)) ([#95](https://github.com/hectorvent/floci/issues/95)) ([823770e](https://github.com/hectorvent/floci/commit/823770e46325f47252ba3f3054f34710e51f597d))
* implement GetRandomPassword for Secrets Manager ([#76](https://github.com/hectorvent/floci/issues/76)) ([#80](https://github.com/hectorvent/floci/issues/80)) ([c57d9eb](https://github.com/hectorvent/floci/commit/c57d9ebcf88f1e9ed31567f9b5989a17588ebf98))
* **lifecycle:** add support for startup and shutdown initialization hooks ([#128](https://github.com/hectorvent/floci/issues/128)) ([7b2576f](https://github.com/hectorvent/floci/commit/7b2576fb42e52e49bd897490b0ace29d113b786d))
* **s3:** add conditional request headers (If-Match, If-None-Match, If-Modified-Since, If-Unmodified-Since) ([#48](https://github.com/hectorvent/floci/issues/48)) ([66af545](https://github.com/hectorvent/floci/commit/66af545053595db74a16afc701b849bf078cbb23)), closes [#46](https://github.com/hectorvent/floci/issues/46)
* **s3:** add presigned POST upload support ([#120](https://github.com/hectorvent/floci/issues/120)) ([1e59f8d](https://github.com/hectorvent/floci/commit/1e59f8dc59161b830887a31b3b3441cad34c781b))
* **s3:** add Range header support for GetObject ([#44](https://github.com/hectorvent/floci/issues/44)) ([b0f5ae2](https://github.com/hectorvent/floci/commit/b0f5ae22cd9bbf9999eef49abd39402781d8f5fc)), closes [#40](https://github.com/hectorvent/floci/issues/40)
* **SFN:** add DynamoDB AWS SDK integration and complete optimized updateItem ([#103](https://github.com/hectorvent/floci/issues/103)) ([4766a7e](https://github.com/hectorvent/floci/commit/4766a7e6f5ace562f9c620b4aa18f1de71a701c5))

## [1.0.11](https://github.com/hectorvent/floci/compare/1.0.10...1.0.11) (2026-03-24)


### Bug Fixes

* add S3 GetObjectAttributes and metadata parity ([#29](https://github.com/hectorvent/floci/issues/29)) ([7d5890a](https://github.com/hectorvent/floci/commit/7d5890a6440ca72d565f3d987afa380825ba5861))

## [1.0.10](https://github.com/hectorvent/floci/compare/1.0.9...1.0.10) (2026-03-24)


### Bug Fixes

* return versionId in CompleteMultipartUpload response ([#35](https://github.com/hectorvent/floci/issues/35)) ([6e8713d](https://github.com/hectorvent/floci/commit/6e8713d9fe4e1b3f6536f979899209daa00b0a04)), closes [hectorvent/floci#32](https://github.com/hectorvent/floci/issues/32)

## [1.0.9](https://github.com/hectorvent/floci/compare/1.0.8...1.0.9) (2026-03-24)


### Bug Fixes

* add ruby lambda runtime support ([#18](https://github.com/hectorvent/floci/issues/18)) ([38bdaf9](https://github.com/hectorvent/floci/commit/38bdaf9616bdb833dbe1b8d4f13c30659b390768))

## [1.0.8](https://github.com/hectorvent/floci/compare/1.0.7...1.0.8) (2026-03-24)


### Bug Fixes

* return NoSuchVersion error for non-existent versionId ([5576222](https://github.com/hectorvent/floci/commit/557622299951b50c795204503ef727b8dac9b6b8))

## [1.0.7](https://github.com/hectorvent/floci/compare/1.0.6...1.0.7) (2026-03-24)


### Bug Fixes

* s3 unit test error ([0d77526](https://github.com/hectorvent/floci/commit/0d77526e2e457e8827ce82042dc5854d62794fde))

## [1.0.6](https://github.com/hectorvent/floci/compare/1.0.5...1.0.6) (2026-03-24)


### Bug Fixes

* **s3:** truncate LastModified timestamps to second precision ([#24](https://github.com/hectorvent/floci/issues/24)) ([ad31e7a](https://github.com/hectorvent/floci/commit/ad31e7a7b7ed8850ba668f7f09c3cad6dc8c81b0))

## [1.0.5](https://github.com/hectorvent/floci/compare/1.0.4...1.0.5) (2026-03-23)


### Bug Fixes

* fix s3 createbucket response format for rust sdk compatibility ([#11](https://github.com/hectorvent/floci/issues/11)) ([0e29c65](https://github.com/hectorvent/floci/commit/0e29c65266e55f48118ec00a4e6971d6264b08f2))

## [1.0.4](https://github.com/hectorvent/floci/compare/1.0.3...1.0.4) (2026-03-20)


### Bug Fixes

* adding build for development branch ([ae15f4c](https://github.com/hectorvent/floci/commit/ae15f4c79c7a28ba50c0426be5cdf1446cdafdeb))
* adding build for development branch ([95cb2aa](https://github.com/hectorvent/floci/commit/95cb2aaf1e63cf3a8b359bd5ab1c0b9f6cfb506b))
* docker build on native ([525f106](https://github.com/hectorvent/floci/commit/525f106eb4d302192d128a2ee00a80adbcb12c67))
* rename github action ([1fe1f6b](https://github.com/hectorvent/floci/commit/1fe1f6b7d87aa25573f015e2483b1c98a5962c4a))
* update workflow to download artifact into target ([4c18934](https://github.com/hectorvent/floci/commit/4c1893459579a6e5e1fa37145ace2a8433cd56e2))

## [1.0.4-dev.3](https://github.com/hectorvent/floci/compare/1.0.4-dev.2...1.0.4-dev.3) (2026-03-17)


### Bug Fixes

* update workflow to download artifact into target ([4c18934](https://github.com/hectorvent/floci/commit/4c1893459579a6e5e1fa37145ace2a8433cd56e2))

## [1.0.4-dev.2](https://github.com/hectorvent/floci/compare/1.0.4-dev.1...1.0.4-dev.2) (2026-03-17)


### Bug Fixes

* rename github action ([1fe1f6b](https://github.com/hectorvent/floci/commit/1fe1f6b7d87aa25573f015e2483b1c98a5962c4a))

## [1.0.4-dev.1](https://github.com/hectorvent/floci/compare/1.0.3...1.0.4-dev.1) (2026-03-17)


### Bug Fixes

* adding build for development branch ([ae15f4c](https://github.com/hectorvent/floci/commit/ae15f4c79c7a28ba50c0426be5cdf1446cdafdeb))
* adding build for development branch ([95cb2aa](https://github.com/hectorvent/floci/commit/95cb2aaf1e63cf3a8b359bd5ab1c0b9f6cfb506b))
* docker build on native ([525f106](https://github.com/hectorvent/floci/commit/525f106eb4d302192d128a2ee00a80adbcb12c67))

## [1.0.3-dev.1](https://github.com/hectorvent/floci/compare/1.0.2...1.0.3-dev.1) (2026-03-17)


### Bug Fixes

* adding build for development branch ([ae15f4c](https://github.com/hectorvent/floci/commit/ae15f4c79c7a28ba50c0426be5cdf1446cdafdeb))
* adding build for development branch ([95cb2aa](https://github.com/hectorvent/floci/commit/95cb2aaf1e63cf3a8b359bd5ab1c0b9f6cfb506b))
* improving native image compilation time ([49c69db](https://github.com/hectorvent/floci/commit/49c69db32314f7e2f94114d86d50e88b3e2a3884))
* update git pages config for the docs ([286bef9](https://github.com/hectorvent/floci/commit/286bef9dd7bfcf162f2ca5c2c030ea280e0b6de6))

## [1.0.2](https://github.com/hectorvent/floci/compare/1.0.1...1.0.2) (2026-03-15)


### Bug Fixes

* docker built action not being triggered ([a6b078f](https://github.com/hectorvent/floci/commit/a6b078fd76f973305ccab2e1ce6b45795e76b9b3))

## [1.0.1](https://github.com/hectorvent/floci/compare/1.0.0...1.0.1) (2026-03-15)


### Bug Fixes

* github action trigger ([156ceb2](https://github.com/hectorvent/floci/commit/156ceb2d884391864a24787e01b2c64b15b5f0f3))

# 1.0.0 (2026-03-15)


### Bug Fixes

* trigger build actions ([e96cf42](https://github.com/hectorvent/floci/commit/e96cf4212b187ef631116fe32b28b8be561056c1))
