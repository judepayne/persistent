# persistent
A small clojure library for persisting data to various stores

    [persistent "0.1.0"]

This library has three protocols:
- `PersistentStore` for reading and writing data to various stores
- `RemoteStore` for stores than need to be cloned locally (e.g. github) and pushed to once writes are completed, forming a sort of transaction.
- `IssueLog` for raising an issue to a store (if it has that capability).

The three types of stores currently implemented are:

- `FileStore` (which is instantiated with a `path` string).
- `S3Store` (an Amazon s3 bucket, instantiated with a `bucket` string).
- `GithubRepo` (instantiated with a (github) `user`, `repo` and `local-path` into which the repo should be cloned. This store implements the RemoteStore and IssueLog protocols as well as PersistentStore).

local paths should be terminated with a `/`.
