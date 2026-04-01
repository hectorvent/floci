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
