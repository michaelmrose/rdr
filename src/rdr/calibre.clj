(ns rdr.calibre
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [me.raynes.fs :as fs]
   [clojure.data.json :as json]
   [rdr.utils :refer :all]
   )
  (:gen-class))

(set! *warn-on-reflection* true)

(defn get-library-path []
  (let [calibre-config (str (System/getenv "HOME") "/.config/calibre/global.py.json")]
    (str (get (json/read-str(slurp calibre-config)) "library_path") "/")))

(defn calibre-running? []
  (pgrep "GUIPool|calibre" ))

(defn db-changed-since-last-visit? []
  (let [db (str (get-library-path) "metadata.db")
        checksum (checksum-file db)
        checksum-file (get-configuration-file-path "checksum")
        oldsum (slurp checksum-file)
        ]
    (spit checksum-file checksum)
    (not= checksum oldsum)))


(defn return-remote-library-segment []
  (if (calibre-running?) "--with-library=http://localhost:8080" ""))

(defn format-calibredb-query "Take the full QUERY passed to our app and format it for processing with calibredb. Will need --for-machine to get a json output. Will need to.
   Will need --with-library=http://localhost:8080 if calibre-running? returns true"
  [query]
  ["calibredb" "list" "--fields" "title,authors,formats" "-s" query "--for-machine" (return-remote-library-segment)])

(defn query-string-to-vector-of-maps 
  "Uses format-calibredb-query to format query, collects and processes json results
   and returns a vector of maps"
  [query]
  (-> (:out (apply ex/sh (format-calibredb-query query)))
      (json/read-str :key-fn keyword)))

(defn list-all-books []
  (query-string-to-vector-of-maps "*"))

(defn filename-to-metadata [file]
  (first(filter (fn [m](vector-contains? (:formats m) file) ) (list-all-books))))

(defn select-preferred-ebook-format [book preferred-formats]
  "Given a book map and a vector of preferred formats in order of preference
   select the most desired format available"
  (if-let* [formats (:formats book)
            ^clojure.lang.LazySeq available (map fs/extension formats)
            preferred (first(filter #(.contains available %) preferred-formats))
            desired (first(filter #(string/ends-with? % preferred) formats))
            ]
    desired
    (first (:formats book))))
;;ls /home/michael/books/*/*\(2602\)/*
;; (defn id-to-real-path-to-formats [id]
;;   (:out (ex/sh "bash" "-c" (str (get-library-path) "*/*(" id ")/*") ))) 


(defn id-to-formats [id]
  (->>(:out (ex/sh "sh" "-c" (str "ls " (get-library-path) "*/*\\(" id  "\\)/*")))
      (string/split-lines)
      (map #(re-find #".*pdf$|.*epub$" %))
      (filter identity)
      (into []))) 

(id-to-formats 3)
