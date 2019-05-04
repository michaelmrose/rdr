(ns rdr.core
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [com.rpl.specter :as ?]
   [swiss.arrows :refer :all]
   [me.raynes.fs :as fs]
   [clojure.data.json :as json]
   [pandect.algo.adler32 :refer :all]
   [rdr.utils :refer :all]
   )
  (:gen-class))

(set! *warn-on-reflection* true)

(defn get-library-path []
  (let [calibre-config (str (System/getenv "HOME") "/.config/calibre/global.py.json")]
    (str (get (json/read-str(slurp calibre-config)) "library_path") "/")))

(defn calibre-running? []
  (pgrep "GUIPool|calibre" ))

(defn return-ebook-reader-command 
  "Read the type of file found at books path or paths and then compare it to a map from user configuration to    
   determine what reader command to use. map could be passed quoted at cli or a single application name
   passed with -r ex: -r zathura or -r '{epub somereader pdf zathura else foobar}'This could ultimately be part of a configuration file"
  [book]
  "zathura"
  )

(defn return-remote-library-segment []
  (if (calibre-running?) "--with-library=http://localhost:8080" ""))

(defn format-calibredb-query 
  "Take the full QUERY passed to our app and format it for processing with calibredb.
   Will need --for-machine to get a json output. Will need to.
   Will need --with-library=http://localhost:8080 if calibre-running? returns true"
  [query]
  ["calibredb" "list" "--fields" "title,authors,formats" "-s" query "--for-machine" (return-remote-library-segment)]
  )

(defn save-book-to-recent-reads 
  "saves book info map to ~/.config/booksclj/recent.edn keeping only the most recent n entries 
   where n is either 30 or the value defined by --keep"
  [book n]
  (let [recent (get-configuration-file-path "recent")
        current (string/split-lines(slurp recent))
        combined (string/join "\n"(take n (distinct(flatten [(str book) current]))))]
    (spit recent combined))
  (println "saving to recent list")
  )

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

(defn query-string-to-vector-of-maps 
  "Uses format-calibredb-query to format query, collects and processes json results
   and returns a vector of maps"
  [query]
  (-<> (:out (apply ex/sh (format-calibredb-query query)))
       (json/read-str <> :key-fn keyword)))

(defn list-all-books []
  (query-string-to-vector-of-maps "*"))

(defn open-ebook 
  "If books path is within the calibre library path pass book to save-book-to-recent-reads
   then open the most preferred format with the preferred reader defined by -p and -r or configuration"
  [book & options]
  (save-book-to-recent-reads book 30)
  (ex/sh (return-ebook-reader-command book) (select-preferred-ebook-format book [".pdf" ".epub"] ))
  )

(defn filename-to-metadata [file]
  (first(filter (fn [m](vector-contains? (:formats m) file) ) (list-all-books))))

(defn open-ebook-file [file]
  (ex/sh (return-ebook-reader-command file) file)
  (if-let [book (filename-to-metadata file)]
    (save-book-to-recent-reads book 30)))

(defn print-book-details [book]
  (let [title (:title book)
        authors (:authors book)]
    (str title " by " authors)))

(defn select-from-books-by-title-and-open [books]
  (if-let* [^clojure.lang.PersistentVector titles (mapv print-book-details books)
            choice (rofi-select titles)
            ndx (.indexOf titles choice)
            book (nth books ndx) 
            ]
    (open-ebook book)))

(defn save-configuration 
  "save options like keep library path and desired reader to a configuation file so that they
   may be omitted in the future."
  [options]
  )


(defn list-recent-reads []
  (let [recent (get-configuration-file-path "recent")]
    (map read-string (string/split-lines(slurp recent)))))


(defn open-last-book 
  "Open most recent entry from recent reads"
  []
  (open-ebook (first (list-recent-reads))))

(defn pick-from-recent-reads 
  "Read recent reads and use pick-from-selection to open"
  []
  (select-from-books-by-title-and-open (list-recent-reads)))

(defn print-help "Print help info" []
  (println (slurp "usage.txt")))

(defn query-and-open [query]
  (select-from-books-by-title-and-open (query-string-to-vector-of-maps query) ))

(defn -main
  "Parse arguments and decide what action to take."
  [& args]
  ()
  (let [op (first args)
        payload (string/join " " (rest args))]
    (condp = op
      "-q" (query-and-open payload)
      "-r" (pick-from-recent-reads)
      "-l" (open-last-book)
      "-o" (open-ebook-file payload)
      "-h" (print-help)))

  ;; process takes several seconds to properly terminate if we don't exit manually
  ;; yet obviously we don't want to kill the repl every time we run test main
  (if-not (is-in-repl?) 
    (shutdown-agents)))


;;this will be used to reuse results from prior queries when the db hasn't changed
(defn db-changed-since-last-visit? []
  (let [db (str (get-library-path) "metadata.db")
        checksum (adler32-file db)
        checksum-file (get-configuration-file-path "checksum")
        oldsum (slurp checksum-file)
        ]
    (spit checksum-file checksum)
    (not= checksum oldsum)))
