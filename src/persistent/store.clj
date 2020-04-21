(ns peristent.store
  (:require [cognitect.aws.client.api   :as aws]
            [cognitect.aws.credentials  :as credentials]
            [clojure.data.json          :as json]
            [clojure.java.shell         :as sh]
            [clj-http.client            :as client])
  (:refer-clojure :exclude [read]))


(defprotocol PersistentStore
  (write [this filename data] [this filename data content-type] "Writes file filename")
  (write-json [this filename data] "Writes data in json form to filename (presumably .json)")
  (read [this filename] "Reads file filename")
  (read-json [this filename] "Reads filename (presumably .json) and converts from json to edn."))


(defprotocol IssueLog
  (report [this token title body] "Creates an issue."))


(defprotocol RemoteStore
  (clone [_] "Clones the remote store locally.")
  (push [_] "Push local changes to remote store."))


(defrecord FileStore [path]
  PersistentStore
  (write [_ filename data] (spit (str path filename) data))
  (write-json [_ filename data] (spit (str path filename) (json/write-str data)))
  (read [_ filename] (slurp (str path filename)))
  (read-json [_ filename] (json/read-str (slurp (str path filename)) :key-fn keyword)))


;; create credentials provider which loas aws_access_key_id &
;; aws_secret_access_key from ~/.aws/credentials
(def credentials
  (credentials/profile-credentials-provider "default"))


(def s3 (aws/client {:api :s3
                     :region :eu-west-2
                     :credentials-provider credentials}))


(defrecord S3Store [bucket]
  PersistentStore
  (write [_ filename data content-type] (aws/invoke s3
                                                    {:op :PutObject
                                                     :request {:Bucket bucket
                                                               :Key filename
                                                               :Body data
                                                               :ContentType content-type}}))

  (write-json [_ filename data] (aws/invoke s3
                                            {:op :PutObject
                                             :request {:Bucket bucket
                                                       :Key filename
                                                       :Body (json/write-str data)
                                                       :ContentType "application/json"}}))

  (read [_ filename] (-> (aws/invoke s3
                                     {:op :GetObject
                                      :request {:Bucket bucket
                                                :Key filename}})
                         :Body
                         slurp))

  (read-json [_ filename] (-> (aws/invoke s3 {:op :GetObject
                                              :request {:Bucket bucket
                                                        :Key filename}})
                         :Body
                         slurp
                         (json/read-str :key-fn keyword))))


;; Github stuff

(defn github-repo-url
  [user repo]
  (str "https://github.com" "/" user "/" repo))


(defn- github-cmd-url
  [user repo]
  (str "https://github.com" "/" user "/" repo ".git"))


(defn- github-issue-url
  [user repo]
  (str "https://api.github.com/repos/" user "/" repo "/issues"))


(defn- clone-github-repo
  "clones a remote github repo locally."
  [github-user github-repo local-path]
  (sh/sh "git" "clone" (github-cmd-url github-user github-repo) local-path)
  (Thread/sleep 100))


(defn- commit-to-github
  "adds, commits & pushes to master"
  ([local-path]
   (commit-to-github local-path "auto commit"))
  ([local-path message]
   (sh/sh "git" "-C" local-path "add" "*")
   (sh/sh "git" "-C" local-path "commit" "-m" message)
   (sh/sh "git" "-C" local-path "push" "origin" "master")
   (sh/sh "rm" "-rf" local-path)))


(defrecord GithubRepo [user repo local-path]
  PersistentStore
  (read [_ filename]             (slurp (str local-path "/" filename)))

  (read-json [_ filename]        (-> (slurp (str local-path "/" filename))
                                   (json/read-str :key-fn keyword)))

  (write [_ filename data]       (spit (str local-path "/" filename) data))

  (write-json [_ filename data]  (->> (json/write-str data)
                                      (spit (str local-path "/" filename))))

  IssueLog
  (report [_ token title body] 
    (let [m {:body (json/write-str {:title title :body body})
             :content-type :json
             :oauth-token token}]
      (let [status (:status (client/post (github-issue-url user repo) m))]
        (if (= 201 status)
          true
          (throw (Exception. (str "Could not post issue: " title " to Github!")))))))

  RemoteStore
  (clone [_]                      (clone-github-repo user repo local-path))

  (push [_]                       (commit-to-github local-path)))


(defn github-repo
  "Creates a new GithubRepo."
  ([user repo]
   (let [repo (->GithubRepo user repo repo)]
     repo))
  ([user repo working-dir]
   (let [repo (->GithubRepo user repo (str working-dir "/" repo))]
     repo)))
